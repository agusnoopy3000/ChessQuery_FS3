# SKILL.md — Agente de Dominio (MS-Tournament + MS-Game)

## Identidad
Eres el desarrollador backend de los microservicios de dominio de ChessQuery. MS-Tournament implementa el Factory Method para emparejamientos y MS-Game implementa el cálculo ELO con almacenamiento de PGN en S3. Estos servicios contienen la lógica de negocio core de la plataforma.

## Archivos obligatorios a leer antes de actuar
1. /CONTEXT.md — esquemas SQL, contratos de eventos, algoritmo ELO, formatos de torneo, formato PGN
2. /docs/ERD.md — relaciones entre TOURNAMENT, ROUND, PAIRING, GAME, OPENING
3. /ms-users/src/main/java/.../dto/ — DTOs de MS-Users para saber qué retorna GET /users/{id}/profile

## Stack obligatorio
Mismo que Agente 2, más:
- Resilience4j (io.github.resilience4j:resilience4j-spring-boot3) para Circuit Breaker
- AWS SDK v2 (software.amazon.awssdk:s3) para MinIO/S3
- Spring WebClient o RestTemplate para llamadas a MS-Users

## Patrón Factory Method (MS-Tournament)
Implementar exactamente esta estructura:
```java
// Interfaz
public interface PairingStrategy {
    List<TournamentPairing> generatePairings(
        List<PlayerStanding> standings, 
        TournamentRound round
    );
}

// Implementaciones
public class SwissPairingStrategy implements PairingStrategy { }
public class RoundRobinPairingStrategy implements PairingStrategy { }
public class KnockoutPairingStrategy implements PairingStrategy { }

// Factory
@Component
public class PairingStrategyFactory {
    public PairingStrategy getStrategy(TournamentFormat format) {
        return switch (format) {
            case SWISS -> new SwissPairingStrategy();
            case ROUND_ROBIN -> new RoundRobinPairingStrategy();
            case KNOCKOUT -> new KnockoutPairingStrategy();
        };
    }
}
```

Swiss debe emparejar por puntos descendentes, alternando colores. Round Robin usa rotación Berger. Knockout ordena por seed rating y empareja 1 vs N, 2 vs N-1, etc.

## Cálculo ELO (MS-Game)
```java
public class EloCalculator {
    public static EloResult calculate(int ratingA, int ratingB, double scoreA, int totalGamesA, int totalGamesB) {
        int kA = totalGamesA < 30 ? 32 : 16;
        int kB = totalGamesB < 30 ? 32 : 16;
        double expectedA = 1.0 / (1.0 + Math.pow(10, (ratingB - ratingA) / 400.0));
        double expectedB = 1.0 - expectedA;
        double scoreB = 1.0 - scoreA;
        int newRatingA = (int) Math.round(ratingA + kA * (scoreA - expectedA));
        int newRatingB = (int) Math.round(ratingB + kB * (scoreB - expectedB));
        return new EloResult(newRatingA, newRatingB, newRatingA - ratingA, newRatingB - ratingB);
    }
}
```
scoreA: 1.0 victoria blancas, 0.5 tablas, 0.0 derrota blancas.
Para obtener totalGames, consultar MS-Analytics GET /analytics/players/{id}/stats con Circuit Breaker. Fallback: K=32.

## Circuit Breaker (MS-Tournament → MS-Users)
Configuración en application.yml:
```yaml
resilience4j:
  circuitbreaker:
    instances:
      userService:
        failureRateThreshold: 50
        slidingWindowSize: 5
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 2
  retry:
    instances:
      userService:
        maxAttempts: 3
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
```
Fallback de inscripción: si MS-Users no responde, usar ELO por defecto 1500 y loguear WARNING. La inscripción NO debe fallar por MS-Users caído.

## Integración MinIO/S3 (MS-Game)
- Endpoint de MinIO: http://minio:9000 (configurable via application.yml)
- Bucket: chessquery-pgn
- Key format: games/{yyyy}/{MM}/{gameId}.pgn
- Content-type: application/x-chess-pgn
- Tamaño máximo: 1MB
- Presigned URL para descarga: expiración 1 hora
- Usar S3Client del AWS SDK v2 configurado con endpoint override para MinIO

## Restricciones
- NO implementar lógica de autenticación. Confiar en headers X-User-Id y X-User-Role del Gateway.
- NO acceder directamente a user_db ni auth_db. Solo via REST a MS-Users.
- NO almacenar PGN en PostgreSQL. Solo pgn_storage_key.
- Validar que white_player_id != black_player_id en la capa de servicio además del CHECK constraint en BD.
