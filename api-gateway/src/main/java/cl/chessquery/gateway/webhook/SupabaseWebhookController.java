package cl.chessquery.gateway.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller para recibir webhooks de Supabase.
 * <p>
 * NOTA IMPORTANTE: Este endpoint NO requiere validación JWT.
 * Es llamado directamente por Supabase (desde Docker) y se autentica
 * SOLO mediante el header X-Supabase-Webhook-Secret.
 * <p>
 * Cuando Supabase Auth crea un usuario, envía un webhook POST con los
 * datos del nuevo usuario. Este controller construye el evento
 * user.registered con el formato existente de CONTEXT.md y lo publica
 * a RabbitMQ para mantener compatibilidad con MS-Users y MS-Notifications.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/supabase")
public class SupabaseWebhookController {

    private static final String EXCHANGE_NAME = "ChessEvents";
    private static final String ROUTING_KEY = "user.registered";

    /**
     * Secreto de webhook de ejemplo (público y conocido, está en el repo y la doc).
     * Si se usara en producción, cualquiera podría forjar eventos user.registered.
     * Ver H-03 del informe de seguridad.
     */
    private static final String DEFAULT_WEBHOOK_SECRET = "dev-webhook-secret";

    /** Perfil de Spring que indica despliegue real (AWS/producción). */
    private static final String PRODUCTION_PROFILE = "aws";

    private final RabbitTemplate rabbitTemplate;
    private final String expectedWebhookSecret;

    @Autowired
    public SupabaseWebhookController(
            RabbitTemplate rabbitTemplate,
            @Value("${supabase.webhook-secret}") String webhookSecret,
            Environment environment) {
        rejectDefaultSecretInProduction(webhookSecret, environment);
        this.rabbitTemplate = rabbitTemplate;
        this.expectedWebhookSecret = webhookSecret;
        log.info("SupabaseWebhookController initialized for exchange={}, routingKey={}",
                EXCHANGE_NAME, ROUTING_KEY);
    }

    /**
     * Constructor de conveniencia para tests: usa un {@link StandardEnvironment}
     * sin perfiles activos (equivale a ejecución local), por lo que el guard de
     * producción nunca se dispara.
     */
    SupabaseWebhookController(RabbitTemplate rabbitTemplate, String webhookSecret) {
        this(rabbitTemplate, webhookSecret, new StandardEnvironment());
    }

    /**
     * Aborta el arranque si se está usando el secreto de webhook de ejemplo en un
     * despliegue de producción (perfil {@code aws}). En local se permite, para no
     * entorpecer el desarrollo. Mitiga H-03: con el secreto público cualquiera
     * podría forjar eventos user.registered y provisionar usuarios basura.
     */
    private void rejectDefaultSecretInProduction(String webhookSecret, Environment environment) {
        boolean isProduction = List.of(environment.getActiveProfiles()).contains(PRODUCTION_PROFILE);
        boolean isDefaultSecret = DEFAULT_WEBHOOK_SECRET.equals(webhookSecret);
        if (isProduction && isDefaultSecret) {
            String banner = """
                    ====================================================================
                    ⛔ ARRANQUE ABORTADO — SUPABASE_WEBHOOK_SECRET inseguro
                    --------------------------------------------------------------------
                    El perfil activo es de producción ('aws') pero el secreto del webhook
                    es el valor de EJEMPLO (público y conocido). Con él, cualquiera podría
                    forjar eventos user.registered y provisionar usuarios basura.

                    Solución: definí la variable de entorno SUPABASE_WEBHOOK_SECRET con un
                    secreto fuerte (inyectado vía AWS Secrets Manager) y usá el MISMO valor
                    en la función de Supabase que envía el webhook (ver migración 00005).
                    ====================================================================""";
            log.error(banner);
            throw new IllegalStateException(
                    "SUPABASE_WEBHOOK_SECRET tiene el valor de ejemplo público en perfil de producción ('aws'): "
                    + "configurá un secreto fuerte y alinealo con Supabase antes de desplegar (H-03).");
        }
    }

    /**
     * Recibe webhook de Supabase cuando un usuario se registra.
     * Publica evento user.registered a RabbitMQ.
     *
     * @param payload     Payload del webhook de Supabase
     * @param webhookSecret Secret para validar autenticidad del webhook
     * @return 200 OK si el evento se publicó, 401 si el secret es inválido
     */
    @PostMapping("/user-registered")
    public ResponseEntity<Void> handleUserRegistered(
            @RequestBody SupabaseWebhookPayload payload,
            @RequestHeader(value = "X-Supabase-Webhook-Secret", required = false) String webhookSecret) {

        // Validar webhook secret (único mecanismo de autenticación)
        if (webhookSecret == null || !webhookSecret.equals(expectedWebhookSecret)) {
            log.warn("Webhook user-registered rejected: invalid or missing webhook secret");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Validar que el payload tiene datos del usuario
        if (payload == null || payload.getRecord() == null) {
            log.warn("Webhook user-registered rejected: empty payload");
            return ResponseEntity.badRequest().build();
        }

        SupabaseWebhookPayload.Record record = payload.getRecord();
        Map<String, String> metadata = record.getRawUserMetaData();

        // Construir evento user.registered con formato existente de CONTEXT.md
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("user.registered")
                .timestamp(Instant.now().toString())
                .payload(UserRegisteredEvent.Payload.builder()
                        .userId(record.getId())
                        .email(record.getEmail())
                        .role(metadata != null ? metadata.getOrDefault("role", "PLAYER") : "PLAYER")
                        .firstName(metadata != null ? metadata.getOrDefault("firstName", "") : "")
                        .lastName(metadata != null ? metadata.getOrDefault("lastName", "") : "")
                        .lichessUsername(metadata != null ? metadata.getOrDefault("lichessUsername", "") : "")
                        .clubName(metadata != null ? metadata.getOrDefault("clubName", "") : "")
                        .build())
                .build();

        try {
            rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, event);
            log.info("Published user.registered event: userId={}, email={}, role={}",
                    record.getId(),
                    record.getEmail(),
                    metadata != null ? metadata.getOrDefault("role", "PLAYER") : "PLAYER");
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to publish user.registered event for userId={}: {}",
                    record.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
