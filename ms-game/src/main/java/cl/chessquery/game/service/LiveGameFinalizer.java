package cl.chessquery.game.service;

import cl.chessquery.game.dto.RegisterGameRequest;
import cl.chessquery.game.repository.LiveGameSessionRepository;
import cl.chessquery.game.realtime.LiveGameBroadcaster;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LiveGameFinalizer {

    private static final int MAX_ATTEMPTS = 3;

    private final LiveGameSessionRepository sessionRepo;
    private final GameService gameService;
    private final LiveGameBroadcaster broadcaster;
    private final TransactionTemplate txTemplate;
    private final ExecutorService finalizerPool = Executors.newFixedThreadPool(
            2, r -> {
                Thread t = new Thread(r, "live-game-finalizer");
                t.setDaemon(true);
                return t;
            });

    public LiveGameFinalizer(LiveGameSessionRepository sessionRepo,
                             GameService gameService,
                             LiveGameBroadcaster broadcaster,
                             PlatformTransactionManager transactionManager) {
        this.sessionRepo = sessionRepo;
        this.gameService = gameService;
        this.broadcaster = broadcaster;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void scheduleAfterCommit(Long sessionId, RegisterGameRequest request) {
        Runnable task = () -> materializeWithRetry(sessionId, request);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    finalizerPool.submit(task);
                }
            });
            return;
        }
        finalizerPool.submit(task);
    }

    private void materializeWithRetry(Long sessionId, RegisterGameRequest request) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Long gameId = txTemplate.execute(status -> {
                    var session = sessionRepo.findById(sessionId)
                            .orElseThrow(() -> new IllegalStateException("LiveGame " + sessionId + " no existe"));
                    if (session.getFinalizedGameId() != null) {
                        return session.getFinalizedGameId();
                    }
                    var registered = gameService.registerGame(request);
                    session.setFinalizedGameId(registered.id());
                    sessionRepo.save(session);
                    return registered.id();
                });
                if (gameId != null) {
                    broadcaster.publish(sessionId, "game.finished", Map.of("finalizedGameId", gameId));
                    log.info("LiveGame {} materializada async como game {}", sessionId, gameId);
                }
                return;
            } catch (Exception e) {
                log.warn("LiveGame {} no se pudo materializar (intento {}/{}): {}",
                        sessionId, attempt, MAX_ATTEMPTS, e.toString());
                sleepBeforeRetry(attempt);
            }
        }
    }

    private void sleepBeforeRetry(int attempt) {
        if (attempt >= MAX_ATTEMPTS) return;
        try {
            Thread.sleep(attempt * 500L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    void shutdown() {
        finalizerPool.shutdown();
        try {
            finalizerPool.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
