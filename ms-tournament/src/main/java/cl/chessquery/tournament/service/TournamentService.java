package cl.chessquery.tournament.service;

import cl.chessquery.tournament.client.UserEloClient;
import cl.chessquery.tournament.dto.*;
import cl.chessquery.tournament.entity.*;
import cl.chessquery.tournament.exception.ApiException;
import cl.chessquery.tournament.pairing.*;
import cl.chessquery.tournament.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentService {

    private final TournamentRepository             tournamentRepo;
    private final TournamentRegistrationRepository registrationRepo;
    private final TournamentRoundRepository        roundRepo;
    private final TournamentPairingRepository      pairingRepo;
    private final PairingStrategyFactory           strategyFactory;
    private final UserEloClient                    userEloClient;
    private final EventPublisherService            events;

    // ── Crear torneo ──────────────────────────────────────────────────────────

    @Transactional
    public TournamentResponse createTournament(CreateTournamentRequest req, Long organizerId) {
        Tournament t = Tournament.builder()
                .name(req.name())
                .description(req.description())
                .format(req.format())
                .status(TournamentStatus.DRAFT)
                .startDate(req.startDate())
                .endDate(req.endDate())
                .location(req.location())
                .maxPlayers(req.maxPlayers())
                .roundsTotal(req.roundsTotal())
                .organizerId(organizerId)
                .minElo(req.minElo())
                .maxElo(req.maxElo())
                .timeControl(req.timeControl())
                .build();

        tournamentRepo.save(t);
        events.publishTournamentCreated(t.getId(), t.getName(), t.getOrganizerId(), t.getFormat().name());
        log.info("Torneo creado: id={} name='{}' organizer={}", t.getId(), t.getName(), organizerId);
        return toResponse(t);
    }

    // ── Transición de estado ─────────────────────────────────────────────────

    @Transactional
    public TournamentResponse transitionStatus(Long id, TournamentStatus newStatus) {
        Tournament t = findOrThrow(id);
        TournamentStatus current = t.getStatus();

        validateTransition(t, current, newStatus);

        t.setStatus(newStatus);
        tournamentRepo.save(t);
        log.info("Torneo {} cambió estado: {} → {}", id, current, newStatus);
        return toResponse(t);
    }

    private void validateTransition(Tournament t, TournamentStatus current, TournamentStatus newStatus) {
        switch (current) {
            case DRAFT -> {
                if (newStatus != TournamentStatus.OPEN) {
                    throw new ApiException(400, "INVALID_TRANSITION",
                            "Desde DRAFT solo se puede ir a OPEN");
                }
                if (t.getRoundsTotal() == null || t.getRoundsTotal() < 1) {
                    throw new ApiException(400, "INVALID_TRANSITION",
                            "El torneo debe tener al menos 1 ronda para abrirse");
                }
            }
            case OPEN -> {
                if (newStatus != TournamentStatus.IN_PROGRESS) {
                    throw new ApiException(400, "INVALID_TRANSITION",
                            "Desde OPEN solo se puede ir a IN_PROGRESS");
                }
                long confirmed = registrationRepo.countByTournamentIdAndStatus(t.getId(), RegistrationStatus.CONFIRMED);
                if (confirmed < 2) {
                    throw new ApiException(400, "INVALID_TRANSITION",
                            "Se necesitan al menos 2 jugadores confirmados para iniciar el torneo");
                }
            }
            case IN_PROGRESS -> {
                if (newStatus != TournamentStatus.FINISHED) {
                    throw new ApiException(400, "INVALID_TRANSITION",
                            "Desde IN_PROGRESS solo se puede ir a FINISHED");
                }
            }
            default -> throw new ApiException(400, "INVALID_TRANSITION",
                    "No se puede cambiar el estado de un torneo " + current);
        }
    }

    // ── Inscribir jugador ─────────────────────────────────────────────────────

    @Transactional
    public RegistrationResponse joinTournament(Long tournamentId, Long playerId) {
        Tournament t = findOrThrow(tournamentId);

        if (t.getStatus() != TournamentStatus.OPEN) {
            throw new ApiException(400, "TOURNAMENT_NOT_OPEN",
                    "El torneo no está abierto para inscripciones");
        }

        // Verificar cupos
        if (t.getMaxPlayers() != null) {
            long confirmed = registrationRepo.countByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED);
            if (confirmed >= t.getMaxPlayers()) {
                throw new ApiException(409, "TOURNAMENT_FULL",
                        "El torneo ha alcanzado el máximo de jugadores permitidos");
            }
        }

        // Verificar inscripción previa
        registrationRepo.findByTournamentIdAndPlayerId(tournamentId, playerId)
                .ifPresent(r -> {
                    if (r.getStatus() == RegistrationStatus.CONFIRMED) {
                        throw new ApiException(409, "ALREADY_REGISTERED",
                                "El jugador ya está inscrito en este torneo");
                    }
                });

        // Obtener ELO con circuit breaker
        int seedRating = userEloClient.getElo(playerId);

        // Validar rango ELO
        if (t.getMinElo() != null && seedRating < t.getMinElo()) {
            throw new ApiException(400, "ELO_TOO_LOW",
                    "El ELO del jugador (" + seedRating + ") es menor al mínimo requerido (" + t.getMinElo() + ")");
        }
        if (t.getMaxElo() != null && seedRating > t.getMaxElo()) {
            throw new ApiException(400, "ELO_TOO_HIGH",
                    "El ELO del jugador (" + seedRating + ") supera el máximo permitido (" + t.getMaxElo() + ")");
        }

        TournamentRegistration reg = TournamentRegistration.builder()
                .tournament(t)
                .playerId(playerId)
                .status(RegistrationStatus.CONFIRMED)
                .registeredAt(Instant.now())
                .seedRating(seedRating)
                .build();

        registrationRepo.save(reg);
        events.publishPlayerRegistered(tournamentId, playerId, seedRating);
        log.info("Jugador {} inscrito en torneo {} con seed={}", playerId, tournamentId, seedRating);

        return toRegistrationResponse(reg);
    }

    // ── Generar ronda ─────────────────────────────────────────────────────────

    @Transactional
    public RoundResponse generateRound(Long tournamentId, int roundNumber) {
        Tournament t = findOrThrow(tournamentId);

        if (t.getStatus() != TournamentStatus.IN_PROGRESS) {
            throw new ApiException(400, "TOURNAMENT_NOT_IN_PROGRESS",
                    "El torneo debe estar en progreso para generar rondas");
        }

        // Verificar que la ronda no existe ya
        if (roundRepo.findByTournamentIdAndRoundNumber(tournamentId, roundNumber).isPresent()) {
            throw new ApiException(409, "ROUND_ALREADY_EXISTS",
                    "La ronda " + roundNumber + " ya existe para este torneo");
        }

        // Calcular standings actuales
        List<PlayerStanding> standings = computeStandings(tournamentId);

        if (standings.size() < 2) {
            throw new ApiException(400, "NOT_ENOUGH_PLAYERS",
                    "Se necesitan al menos 2 jugadores para generar una ronda");
        }

        // Generar emparejamientos
        PairingStrategy strategy = strategyFactory.getStrategy(t.getFormat());
        List<PairingResult> results = strategy.generatePairings(standings, roundNumber);

        // Persistir ronda
        TournamentRound round = TournamentRound.builder()
                .tournament(t)
                .roundNumber(roundNumber)
                .status(RoundStatus.IN_PROGRESS)
                .build();
        roundRepo.save(round);

        // Persistir emparejamientos
        List<TournamentPairing> pairings = new ArrayList<>();
        for (PairingResult pr : results) {
            TournamentPairing pairing = TournamentPairing.builder()
                    .round(round)
                    .whitePlayerId(pr.whitePlayerId())
                    .blackPlayerId(pr.blackPlayerId())
                    .boardNumber(pr.boardNumber())
                    .build();
            pairings.add(pairing);
        }
        pairingRepo.saveAll(pairings);

        events.publishRoundStarting(tournamentId, roundNumber, pairings.size());
        log.info("Ronda {} generada para torneo {} con {} emparejamientos",
                roundNumber, tournamentId, pairings.size());

        List<PairingResponse> pairingResponses = pairings.stream()
                .map(this::toPairingResponse)
                .toList();

        return new RoundResponse(
                round.getId(),
                tournamentId,
                roundNumber,
                round.getRoundDate(),
                round.getStatus().name(),
                pairingResponses
        );
    }

    // ── Grabar resultado ──────────────────────────────────────────────────────

    @Transactional
    public PairingResponse recordResult(Long pairingId, String result) {
        TournamentPairing pairing = pairingRepo.findById(pairingId)
                .orElseThrow(() -> new ApiException(404, "PAIRING_NOT_FOUND",
                        "Emparejamiento con id " + pairingId + " no encontrado"));

        pairing.setResult(result);
        pairingRepo.save(pairing);

        Long tournamentId = pairing.getRound().getTournament().getId();
        events.publishGameFinished(
                pairingId,
                tournamentId,
                pairing.getWhitePlayerId(),
                pairing.getBlackPlayerId(),
                result
        );

        return toPairingResponse(pairing);
    }

    // ── Standings ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StandingEntry> getStandings(Long tournamentId) {
        findOrThrow(tournamentId);

        List<TournamentRegistration> registrations = registrationRepo.findByTournamentId(tournamentId);
        List<TournamentRound> rounds = roundRepo.findByTournamentIdOrderByRoundNumberAsc(tournamentId);

        List<Long> roundIds = rounds.stream().map(TournamentRound::getId).toList();
        List<TournamentPairing> allPairings = roundIds.isEmpty()
                ? List.of()
                : pairingRepo.findByRoundIdIn(roundIds);

        // Calcular puntos por jugador
        Map<Long, Double> points = new HashMap<>();
        for (TournamentRegistration r : registrations) {
            points.put(r.getPlayerId(), 0.0);
        }

        for (TournamentPairing p : allPairings) {
            if (p.getResult() == null) continue;
            switch (p.getResult()) {
                case "1-0" -> {
                    points.merge(p.getWhitePlayerId(), 1.0, Double::sum);
                    points.merge(p.getBlackPlayerId(), 0.0, Double::sum);
                }
                case "0-1" -> {
                    points.merge(p.getWhitePlayerId(), 0.0, Double::sum);
                    points.merge(p.getBlackPlayerId(), 1.0, Double::sum);
                }
                case "1/2-1/2" -> {
                    points.merge(p.getWhitePlayerId(), 0.5, Double::sum);
                    points.merge(p.getBlackPlayerId(), 0.5, Double::sum);
                }
                default -> { /* ignore */ }
            }
        }

        // Calcular Buchholz: suma de puntos de los rivales
        Map<Long, Double> buchholz = new HashMap<>();
        for (TournamentRegistration r : registrations) {
            buchholz.put(r.getPlayerId(), 0.0);
        }

        for (TournamentPairing p : allPairings) {
            if (p.getResult() == null) continue;
            double whitePoints = points.getOrDefault(p.getWhitePlayerId(), 0.0);
            double blackPoints = points.getOrDefault(p.getBlackPlayerId(), 0.0);
            buchholz.merge(p.getWhitePlayerId(), blackPoints, Double::sum);
            buchholz.merge(p.getBlackPlayerId(), whitePoints, Double::sum);
        }

        // Calcular Sonneborn-Berger: suma(puntos_rival * puntos_propios_en_esa_partida)
        Map<Long, Double> sb = new HashMap<>();
        for (TournamentRegistration r : registrations) {
            sb.put(r.getPlayerId(), 0.0);
        }

        for (TournamentPairing p : allPairings) {
            if (p.getResult() == null) continue;
            double scoreWhite = switch (p.getResult()) {
                case "1-0"     -> 1.0;
                case "1/2-1/2" -> 0.5;
                default        -> 0.0;
            };
            double scoreBlack = 1.0 - scoreWhite;
            double blackPoints = points.getOrDefault(p.getBlackPlayerId(), 0.0);
            double whitePoints = points.getOrDefault(p.getWhitePlayerId(), 0.0);

            sb.merge(p.getWhitePlayerId(), scoreWhite * blackPoints, Double::sum);
            sb.merge(p.getBlackPlayerId(), scoreBlack * whitePoints, Double::sum);
        }

        Map<Long, Integer> seedByPlayer = registrations.stream()
                .collect(Collectors.toMap(
                        TournamentRegistration::getPlayerId,
                        r -> r.getSeedRating() != null ? r.getSeedRating() : 0
                ));

        List<Long> sorted = points.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder())
                        .thenComparingDouble(e -> -buchholz.getOrDefault(e.getKey(), 0.0))
                        .thenComparingDouble(e -> -sb.getOrDefault(e.getKey(), 0.0)))
                .map(Map.Entry::getKey)
                .toList();

        List<StandingEntry> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Long playerId = sorted.get(i);
            result.add(new StandingEntry(
                    i + 1,
                    playerId,
                    points.getOrDefault(playerId, 0.0),
                    buchholz.getOrDefault(playerId, 0.0),
                    sb.getOrDefault(playerId, 0.0),
                    seedByPlayer.getOrDefault(playerId, 0)
            ));
        }
        return result;
    }

    // ── Listar torneos ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<TournamentResponse> listTournaments(
            TournamentStatus status, TournamentFormat format, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Tournament> tournamentPage;

        if (status != null && format != null) {
            tournamentPage = tournamentRepo.findByStatusAndFormat(status, format, pageable);
        } else if (status != null) {
            tournamentPage = tournamentRepo.findByStatus(status, pageable);
        } else if (format != null) {
            tournamentPage = tournamentRepo.findByFormat(format, pageable);
        } else {
            tournamentPage = tournamentRepo.findAll(pageable);
        }

        List<TournamentResponse> content = tournamentPage.getContent().stream()
                .map(this::toResponse)
                .toList();

        return new PageResponse<>(
                content,
                tournamentPage.getNumber(),
                tournamentPage.getSize(),
                tournamentPage.getTotalElements(),
                tournamentPage.getTotalPages()
        );
    }

    // ── Obtener torneo ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TournamentResponse getTournament(Long id) {
        return toResponse(findOrThrow(id));
    }

    // ── Obtener ronda ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RoundResponse getRound(Long tournamentId, int roundNumber) {
        findOrThrow(tournamentId);
        TournamentRound round = roundRepo.findByTournamentIdAndRoundNumber(tournamentId, roundNumber)
                .orElseThrow(() -> new ApiException(404, "ROUND_NOT_FOUND",
                        "Ronda " + roundNumber + " no encontrada para el torneo " + tournamentId));

        List<PairingResponse> pairingResponses = pairingRepo.findByRoundId(round.getId()).stream()
                .map(this::toPairingResponse)
                .toList();

        return new RoundResponse(
                round.getId(),
                tournamentId,
                round.getRoundNumber(),
                round.getRoundDate(),
                round.getStatus().name(),
                pairingResponses
        );
    }

    // ── Listar inscripciones ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RegistrationResponse> listRegistrations(Long tournamentId) {
        findOrThrow(tournamentId);
        return registrationRepo.findByTournamentId(tournamentId).stream()
                .map(this::toRegistrationResponse)
                .toList();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private List<PlayerStanding> computeStandings(Long tournamentId) {
        List<TournamentRegistration> registrations = registrationRepo.findByTournamentId(tournamentId)
                .stream()
                .filter(r -> r.getStatus() == RegistrationStatus.CONFIRMED)
                .toList();

        List<StandingEntry> standings = getStandings(tournamentId);
        Map<Long, StandingEntry> standingMap = standings.stream()
                .collect(Collectors.toMap(StandingEntry::playerId, s -> s));

        return registrations.stream()
                .map(r -> {
                    StandingEntry se = standingMap.get(r.getPlayerId());
                    double pts = se != null ? se.points() : 0.0;
                    int seed = r.getSeedRating() != null ? r.getSeedRating() : 0;
                    return new PlayerStanding(r.getPlayerId(), pts, seed, 0);
                })
                .toList();
    }

    private Tournament findOrThrow(Long id) {
        return tournamentRepo.findById(id)
                .orElseThrow(() -> new ApiException(404, "TOURNAMENT_NOT_FOUND",
                        "Torneo con id " + id + " no encontrado"));
    }

    private TournamentResponse toResponse(Tournament t) {
        int registered = (int) registrationRepo.countByTournamentIdAndStatus(t.getId(), RegistrationStatus.CONFIRMED);
        return new TournamentResponse(
                t.getId(),
                t.getName(),
                t.getDescription(),
                t.getFormat().name(),
                t.getStatus().name(),
                t.getStartDate(),
                t.getEndDate(),
                t.getLocation(),
                t.getMaxPlayers(),
                t.getRoundsTotal(),
                t.getOrganizerId(),
                t.getMinElo(),
                t.getMaxElo(),
                t.getTimeControl(),
                registered,
                t.getCreatedAt()
        );
    }

    private RegistrationResponse toRegistrationResponse(TournamentRegistration r) {
        return new RegistrationResponse(
                r.getId(),
                r.getTournament().getId(),
                r.getPlayerId(),
                r.getStatus().name(),
                r.getSeedRating(),
                r.getRegisteredAt()
        );
    }

    private PairingResponse toPairingResponse(TournamentPairing p) {
        return new PairingResponse(
                p.getId(),
                p.getRound().getId(),
                p.getWhitePlayerId(),
                p.getBlackPlayerId(),
                p.getResult(),
                p.getBoardNumber()
        );
    }
}
