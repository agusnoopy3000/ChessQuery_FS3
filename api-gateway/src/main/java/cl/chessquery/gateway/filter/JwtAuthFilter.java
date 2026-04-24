package cl.chessquery.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/auth/logout",
            "/actuator"
    );

    private final WebClient authClient;
    private final ReactiveCircuitBreaker authCircuitBreaker;

    public JwtAuthFilter(WebClient.Builder webClientBuilder,
                         ReactiveResilience4JCircuitBreakerFactory cbFactory,
                         @Value("${gateway.ms-auth.url:http://ms-auth:9090}") String msAuthUrl) {
        this.authClient = webClientBuilder.baseUrl(msAuthUrl).build();
        this.authCircuitBreaker = cbFactory.create("ms-auth-validate");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        Mono<JsonNode> validateCall = authClient.get()
                .uri("/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token)
                .retrieve()
                .bodyToMono(JsonNode.class);

        return authCircuitBreaker.run(
                validateCall,
                throwable -> {
                    log.warn("MS-Auth circuit breaker fallback: {}", throwable.getMessage());
                    if (throwable instanceof WebClientResponseException wcre
                            && wcre.getStatusCode().value() == 401) {
                        return Mono.empty();
                    }
                    return Mono.error(new AuthUnavailableException());
                }
        )
        .flatMap(claims -> {
            String userId = textOrEmpty(claims, "userId");
            String email = textOrEmpty(claims, "email");
            String role = textOrEmpty(claims, "role");

            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .headers(h -> {
                        h.set("X-User-Id", userId);
                        h.set("X-User-Email", email);
                        h.set("X-User-Role", role);
                    })
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());
        })
        .onErrorResume(AuthUnavailableException.class,
                ex -> serviceUnavailable(exchange, "Auth service unavailable"))
        .switchIfEmpty(Mono.defer(() -> unauthorized(exchange, "Invalid or expired token")));
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private String textOrEmpty(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return writeErrorBody(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", message);
    }

    private Mono<Void> serviceUnavailable(ServerWebExchange exchange, String message) {
        return writeErrorBody(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", message);
    }

    private Mono<Void> writeErrorBody(ServerWebExchange exchange, HttpStatus status,
                                       String error, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                status.value(), error, message, java.time.Instant.now()
        );
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private static class AuthUnavailableException extends RuntimeException {
        AuthUnavailableException() { super("MS-Auth unavailable"); }
    }
}
