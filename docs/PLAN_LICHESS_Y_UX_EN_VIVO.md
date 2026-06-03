# Plan — Integración Lichess API + mejoras de UX en vivo (inspiradas en en-croissant)

> Fecha: 2026-06-03. Contexto: tras separar el ELO de juego (`eloPlatform`) del
> federativo (`eloNational`/`eloFide`), queda libre el camino para sumar ratings
> de plataformas externas sin colisiones. Este doc cubre dos frentes:
> (1) integración real con **Lichess** vía el ETL, y (2) mejoras a la **experiencia
> de la partida en vivo** inspiradas en [en-croissant](https://github.com/franciscoBSalgueiro/en-croissant).

---

## Parte 1 — Integración con la API de Lichess (ms-etl)

### 1.1 Objetivo
Reemplazar `lichess_mock` por una fuente **real** que traiga los ratings de Lichess
de los jugadores que tengan `lichessUsername`, y publicarlos a la plataforma vía el
mismo evento `rating.updated` que ya consume `ms-users`.

### 1.2 Por qué Lichess primero
- **API pública, sin OAuth** para lecturas de perfil/rating (la vía más rápida a datos reales).
- Bien documentada y estable (a diferencia del scraping de AJEFECH/chess-results).
- ChessQuery **ya tiene el campo `lichessUsername`** en el registro y en `Player` → la clave de match ya existe.

### 1.3 Endpoints relevantes (lectura, sin token)
| Endpoint | Uso |
|---|---|
| `GET /api/user/{username}` | Perfil + `perfs` por modalidad (bullet, blitz, rapid, classical…): `{rating, rd, prog, games}` |
| `POST /api/users` (body: lista de hasta 300 usernames) | **Fetch masivo** de usuarios en 1 request (eficiente para sync) |
| `GET /api/user/{username}/rating-history` | Historial de rating por modalidad (para gráficos) |

Rate limit: ser amable; ante `429`, back-off ~1 min (ya hay `CircuitBreaker` en el ETL).
Reusar el patrón existente (cache Redis last-known-good + circuit breaker).

### 1.4 Modelo de datos (cambios)
- **`ms-users` (Player + migración Flyway):** agregar campos de rating Lichess por modalidad,
  p. ej. `elo_lichess_blitz`, `elo_lichess_rapid`, `elo_lichess_classical` (o un JSON `lichess_perfs`).
- **`RatingType` enum + `EloUpdatedConsumer`:** agregar `LICHESS_BLITZ / LICHESS_RAPID / LICHESS_CLASSICAL`
  mapeados a los campos nuevos (hoy el switch cubre NATIONAL / FIDE_* / PLATFORM).
- **Match key:** por `lichessUsername` (no por nombre/RUT como AJEFECH).

### 1.5 Implementación (ms-etl)
1. `app/sources/lichess_real.py` (`LichessRealSource`): con `httpx`, consultar `POST /api/users`
   con los usernames a sincronizar; mapear `perfs` → registros `{lichessUsername, eloLichessBlitz, …, source:"LICHESS"}`.
2. En `etl_service.sources`, usar la real cuando `LICHESS_USE_MOCK=false` (igual que AJEFECH).
3. Publicar `rating.updated` con `ratingType` por modalidad (o varios eventos).
4. Origen de usernames a sincronizar: endpoint que `ms-etl` pida a `ms-users` los players con `lichessUsername` no nulo (nuevo `GET /users/lichess-usernames`), en lotes de 300.
5. Scheduler opcional (APScheduler o cron) para sync periódico; por ahora, disparo manual `POST /etl/sync/lichess`.

### 1.6 (Opcional, fase 2) Account linking OAuth
Para acciones de cuenta (importar partidas propias, leer preferencias) → OAuth2 **PKCE** de Lichess.
No es necesario para leer ratings públicos. Dejar para una fase posterior.

### 1.7 Despliegue
- Mismo patrón que el resto del ETL (ver `docs/` ETL): build+push imagen `ms-etl`, crear `etl_db` en RDS,
  sumar contenedor al task-def. El consumidor `RatingUpdatedConsumer` de `ms-users` ya está desplegado.

### 1.8 Estimación
| Item | Esfuerzo |
|---|---|
| `LichessRealSource` + mapeo perfs + `POST /api/users` | 0,5–1 día |
| Schema `ms-users` (migración) + RatingType LICHESS_* + consumer | 0,5–1 día |
| Endpoint usernames + wiring publish + tests + back-off | 0,5–1 día |
| **Total ratings Lichess reales** | **~2–3 días** |
| Scheduler periódico | +0,5 día |
| Account linking OAuth PKCE (fase 2) | +2–3 días |

---

## Parte 2 — UX de la partida en vivo (inspirada en en-croissant)

en-croissant (Tauri + React, board **chessground**, motores UCI/Stockfish) destaca por:
barra de evaluación, flechas de mejor jugada, **árbol de jugadas con variaciones y anotaciones**,
**explorador de aperturas**, temas de tablero/piezas, e importación de partidas.

> ⚠️ **Anti-trampa:** durante el juego **en vivo NO** se debe mostrar evaluación de motor ni
> mejores jugadas (sería asistencia). El motor/eval va **solo en el análisis post-partida**.

### 2.1 Durante la partida (bajo riesgo, alto valor)
- **Panel de notación estilo en-croissant**: lista SAN scrollable de las jugadas (ya tenemos `state.moves`),
  con resaltado de la jugada actual y click para revisar posiciones anteriores (read-only).
- **Pulido del tablero**: resaltado de última jugada y de jaque, coordenadas, sonido de jugada/captura,
  botón de **flip** (girar tablero), y piezas/colores consistentes (chessground-like).
- **Piezas capturadas + balance material**: ya existe `computeMaterialBalance`; mostrarlo como en-croissant
  (piezas comidas por lado + "+N").

### 2.2 Post-partida (lo distintivo de en-croissant)
- **Modo "Analizar partida"**: tras finalizar, habilitar análisis con **Stockfish WASM** en el navegador
  (web worker): **barra de evaluación**, **flecha de mejor jugada**, y recorrido jugada-a-jugada.
- **Resumen de precisión / blunders** por jugador (estilo "accuracy" de Lichess/en-croissant).
- **Explorador de apertura**: ya detectamos `detectedOpeningName`/ECO; enlazar a la teoría/explorer.

### 2.3 Recomendación de alcance (incremental)
1. **Incremento 1 (rápido, ~1–2 días):** panel de notación SAN + resaltado de última jugada/jaque +
   sonido + flip. Cero dependencia de motor. Mejora inmediata y segura.
2. **Incremento 2 (~3–5 días):** modo análisis post-partida con Stockfish WASM (eval bar + flechas + accuracy).
   Mayor esfuerzo (web worker, manejo de memoria del wasm) pero es el sello de en-croissant.

### 2.4 Notas técnicas
- ChessQuery ya usa un tablero propio + `chess.js`; para el análisis se puede integrar `stockfish.wasm`
  (paquete `stockfish` / `lila-stockfish-web`) en un web worker, sin backend.
- Mantener el eval **deshabilitado** mientras `state.status === 'ACTIVE'`; habilitarlo solo en `FINISHED`.

---

## Resumen de prioridades sugeridas
1. **Lichess ratings reales** (ETL) — ~2–3 días; reaprovecha `lichessUsername` y la separación de ELO ya hecha.
2. **UX en vivo, Incremento 1** (notación + pulido de tablero) — ~1–2 días; bajo riesgo.
3. **Análisis post-partida con Stockfish WASM** — ~3–5 días; el diferencial estilo en-croissant.
