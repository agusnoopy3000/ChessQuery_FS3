package cl.chessquery.gateway.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO para deserializar el payload del webhook de Supabase.
 * <p>
 * Supabase envía webhooks con la estructura:
 * <pre>
 * {
 *   "type": "INSERT",
 *   "table": "users",
 *   "schema": "auth",
 *   "record": {
 *     "id": "uuid",
 *     "email": "user@example.com",
 *     "raw_user_meta_data": { "role": "PLAYER", "firstName": "...", "lastName": "..." },
 *     "created_at": "2026-..."
 *   },
 *   "old_record": null
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SupabaseWebhookPayload {

    private String type;
    private String table;
    private String schema;
    private Record record;

    @JsonProperty("old_record")
    private Record oldRecord;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Record {
        private String id;
        private String email;

        @JsonProperty("raw_user_meta_data")
        private Map<String, String> rawUserMetaData;

        @JsonProperty("created_at")
        private String createdAt;
    }
}
