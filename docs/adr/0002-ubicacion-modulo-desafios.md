# ADR-0002 — Ubicación del módulo de desafíos y logros

- **Estado:** Propuesto
- **Fecha:** 2026-07-08
- **Épicas relacionadas:** EP-O4 (O4-01…O4-04)
- **Decide:** equipo

## Contexto

EP-O4 introduce desafíos configurables por el organizador ("juega 10 blitz este mes",
"gana 3 partidas con negras") y logros. El motor debe evaluar progreso **consumiendo
eventos existentes** (`game.finished`, `elo.updated`) de forma idempotente y casi en
tiempo real, sin nuevas llamadas síncronas. Publica `challenge.created`,
`challenge.progressed`, `achievement.unlocked`.

La pregunta: ¿vive en MS-Analytics (que ya consume `game.*`/`elo.*` y mantiene
`PLAYER_STATS_MV`), en MS-Tournament, o en un microservicio nuevo?

## Opciones

1. **En MS-Analytics.**
   - + Ya está suscrito a `game.finished`/`elo.updated`; es un consumidor idempotente de eventos por diseño; el backlog lo señala como "subutilizado". Reutiliza toda la infra de agregación.
   - − Mezcla "métricas de solo lectura" con "reglas de negocio con estado y escritura" (desafíos son entidades mutables del organizador).
2. **En MS-Tournament.**
   - + Cercano al organizador y a inscripciones.
   - − MS-Tournament no consume `game.finished` para stats (solo `game.finished` para cerrar pairings); habría que ampliar su rol; acopla desafíos al ciclo de torneo cuando en realidad son del club.
3. **Microservicio nuevo `ms-challenges`.**
   - + Límite de dominio limpio; escala independiente.
   - − Costo de arranque (nuevo módulo desde arquetipo, nueva BD, nuevo pipeline) alto para un equipo de 3; EP-O4 es P2 (no urgente).

## Decisión

**Propuesta: Opción 1 — módulo `challenge` dentro de MS-Analytics.**
Se crea como paquete/agregado separado (`cl.chessquery.analytics.challenge`) con sus
propias tablas en `analytics_db`, distinto de las materialized views de stats, pero
reutilizando el consumidor de eventos y la infra existente. Si a futuro crece, este
límite de paquete facilita extraerlo a `ms-challenges` (Opción 3) sin reescribir la lógica.

## Consecuencias

- EP-O4 (P2) no requiere microservicio nuevo → cabe en la capacidad del equipo.
- MS-Analytics gana escritura transaccional (definición de desafíos por el organizador vía bff-organizer) además de su rol de lectura; documentar que ya no es "solo lectura".
- Nuevos eventos `challenge.*` y `achievement.unlocked` publicados por MS-Analytics; `achievement.unlocked` lo consume MS-Notifications. Agregar al catálogo `docs/events.md`.
- Motor de reglas idempotente por `eventId` (reusa el patrón `processed_event`).
