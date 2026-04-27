package cl.chessquery.users.messaging;

import cl.chessquery.users.config.RabbitMQConfig;
import cl.chessquery.users.entity.Club;
import cl.chessquery.users.entity.Player;
import cl.chessquery.users.entity.ProcessedEvent;
import cl.chessquery.users.repository.ClubRepository;
import cl.chessquery.users.repository.PlayerRepository;
import cl.chessquery.users.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumidor del evento rating.updated publicado por MS-ETL tras cada
 * sincronización con AJEFECH / FIDE / Lichess.
 *
 * Payload esperado:
 * {
 *   "source":           "AJEFECH",
 *   "ratingType":       "NATIONAL",
 *   "syncId":           42,
 *   "playersUpdated":   189,
 *   "players": [
 *     {
 *       "firstName":        "Jorge Moises",
 *       "lastName":         "Sepulveda Rojas",
 *       "birthDate":        "1981-03-24",
 *       "clubName":         "Club Deportivo Chess Viña del Mar",
 *       "fideId":           "3404803",
 *       "federationId":     "738",
 *       "rut":              "9914860-8",
 *       "eloFideStandard":  2159,
 *       "eloNational":      2053,
 *       "source":           "AJEFECH",
 *       "sourceUrl":        "https://..."
 *     }, ...
 *   ]
 * }
 *
 * Estrategia de matching (en orden):
 *   1. federation_id exacto
 *   2. fide_id exacto
 *   3. rut exacto
 *   4. nombre completo case-insensitive
 *   5. INSERT nuevo player con enrichment_source
 *
 * Idempotencia: descarta eventId ya procesado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RatingUpdatedConsumer {

    private final PlayerRepository playerRepo;
    private final ProcessedEventRepository processedRepo;
    private final ClubRepository clubRepo;

    @RabbitListener(queues = RabbitMQConfig.USERS_RATING_QUEUE)
    @Transactional
    public void onRatingUpdated(ChessEvent event) {
        if (!"rating.updated".equals(event.getEventType())) {
            log.debug("Evento ignorado en users.rating.queue: {}", event.getEventType());
            return;
        }

        UUID eventId = parseUuid(event.getEventId());
        if (eventId != null && processedRepo.existsById(eventId)) {
            log.debug("rating.updated {} ya procesado, descartado", eventId);
            return;
        }

        Map<String, Object> payload = event.getPayload();
        String source = (String) payload.getOrDefault("source", "UNKNOWN");
        Object playersRaw = payload.get("players");
        if (!(playersRaw instanceof List<?> players) || players.isEmpty()) {
            log.info("rating.updated source={} sin players[], nada que procesar", source);
            persistProcessed(eventId, event.getEventType());
            return;
        }

        int updated = 0;
        int created = 0;
        int skipped = 0;
        for (Object item : players) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> p = (Map<String, Object>) raw;
            try {
                Result r = applyOne(p, source);
                switch (r) {
                    case UPDATED -> updated++;
                    case CREATED -> created++;
                    case SKIPPED -> skipped++;
                }
            } catch (Exception e) {
                log.warn("rating.updated: error procesando jugador {}: {}",
                        p.get("federationId"), e.getMessage());
                skipped++;
            }
        }

        log.info("rating.updated source={} updated={} created={} skipped={}",
                source, updated, created, skipped);
        persistProcessed(eventId, event.getEventType());
    }

    private enum Result { UPDATED, CREATED, SKIPPED }

    private Result applyOne(Map<String, Object> p, String source) {
        String federationId = asString(p.get("federationId"));
        String fideId = asString(p.get("fideId"));
        String rut = asString(p.get("rut"));
        String firstName = asString(p.get("firstName"));
        String lastName = asString(p.get("lastName"));

        if (firstName == null || lastName == null) return Result.SKIPPED;

        Optional<Player> match = lookup(federationId, fideId, rut, firstName, lastName);
        if (match.isPresent()) {
            applyEnrichment(match.get(), p, source);
            playerRepo.save(match.get());
            return Result.UPDATED;
        }

        Player created = newPlayerFromPayload(p, source);
        try {
            playerRepo.save(created);
            return Result.CREATED;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Carrera por unique constraint (rut/fide_id/federation_id) — ignorar.
            log.debug("INSERT colision para fed={} fide={} rut={}", federationId, fideId, rut);
            return Result.SKIPPED;
        }
    }

    private Optional<Player> lookup(
            String federationId, String fideId, String rut,
            String firstName, String lastName
    ) {
        if (federationId != null) {
            Optional<Player> byFed = playerRepo.findByFederationId(federationId);
            if (byFed.isPresent()) return byFed;
        }
        if (fideId != null) {
            Optional<Player> byFide = playerRepo.findByFideId(fideId);
            if (byFide.isPresent()) return byFide;
        }
        if (rut != null) {
            Optional<Player> byRut = playerRepo.findByRut(rut);
            if (byRut.isPresent()) return byRut;
        }
        return playerRepo.findByFullNameIgnoreCase(firstName, lastName);
    }

    private void applyEnrichment(Player player, Map<String, Object> p, String source) {
        // Solo sobreescribimos campos null/0 — no pisamos datos curados manualmente.
        if (player.getFederationId() == null) {
            player.setFederationId(asString(p.get("federationId")));
        }
        if (player.getFideId() == null) {
            player.setFideId(asString(p.get("fideId")));
        }
        if (player.getRut() == null) {
            player.setRut(asString(p.get("rut")));
        }
        if (player.getBirthDate() == null) {
            player.setBirthDate(asLocalDate(p.get("birthDate")));
        }
        // Resolver club por nombre (find-or-create) si AJEFECH lo proveyó
        String clubName = asString(p.get("clubName"));
        if (clubName != null && player.getClub() == null) {
            player.setClub(findOrCreateClub(clubName));
        }
        // ELOs: siempre actualiza si hay valor nuevo > 0
        Integer eloNat = asInt(p.get("eloNational"));
        if (eloNat != null && eloNat > 0) player.setEloNational(eloNat);
        Integer eloFide = asInt(p.get("eloFideStandard"));
        if (eloFide != null && eloFide > 0) player.setEloFideStandard(eloFide);

        player.setEnrichmentSource(source);
        player.setEnrichedAt(Instant.now());
    }

    private Player newPlayerFromPayload(Map<String, Object> p, String source) {
        Integer eloNat = asInt(p.get("eloNational"));
        Integer eloFide = asInt(p.get("eloFideStandard"));
        String clubName = asString(p.get("clubName"));
        return Player.builder()
                .firstName(asString(p.get("firstName")))
                .lastName(asString(p.get("lastName")))
                .rut(asString(p.get("rut")))
                .fideId(asString(p.get("fideId")))
                .federationId(asString(p.get("federationId")))
                .birthDate(asLocalDate(p.get("birthDate")))
                .club(clubName != null ? findOrCreateClub(clubName) : null)
                .eloNational(eloNat != null && eloNat > 0 ? eloNat : null)
                .eloFideStandard(eloFide != null && eloFide > 0 ? eloFide : null)
                .enrichmentSource(source)
                .enrichedAt(Instant.now())
                .build();
    }

    private Club findOrCreateClub(String name) {
        return clubRepo.findFirstByNameIgnoreCase(name)
                .orElseGet(() -> clubRepo.save(Club.builder().name(name).build()));
    }

    private void persistProcessed(UUID eventId, String eventType) {
        if (eventId == null) return;
        try {
            processedRepo.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .processedAt(Instant.now())
                    .build());
        } catch (Exception ignored) {
            // Otra réplica ya lo persistió — ok.
        }
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    private static String asString(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer asInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return null;
        try { return Integer.parseInt(v.toString().trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private static LocalDate asLocalDate(Object v) {
        if (v == null) return null;
        try { return LocalDate.parse(v.toString()); }
        catch (Exception e) { return null; }
    }
}
