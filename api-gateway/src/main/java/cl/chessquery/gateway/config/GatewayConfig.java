package cl.chessquery.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
public class GatewayConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    /** Rate limiter: 100 req/min, burst 120, 1 token por request. */
    @Bean
    @org.springframework.context.annotation.Primary
    public RedisRateLimiter defaultRateLimiter() {
        return new RedisRateLimiter(100, 120, 1);
    }

    /** Rate limiter para /auth/login y /auth/register: 20 req/min. */
    @Bean
    public RedisRateLimiter authRateLimiter() {
        return new RedisRateLimiter(20, 40, 1);
    }

    /** Key resolver por IP remota para el rate limiter. */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(ip);
        };
    }

    @Bean
    public CorsWebFilter corsWebFilter(Environment env) {
        String originsProp = env.getProperty("gateway.cors.allowed-origins",
                "http://localhost:5173,http://localhost:5174,http://localhost:5175,http://localhost");
        List<String> origins = List.of(originsProp.split(","));

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "X-User-Id", "X-User-Role"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
