# ADR-0001 — ELO segmentado por modalidad de tiempo

- **Estado:** Propuesto
- **Fecha:** 2026-07-08
- **Épicas relacionadas:** EP-P1 (P1-05 spike), EP-O3
- **Decide:** equipo (requiere validación humana — decisión de producto + migración delicada)

## Contexto

Hoy MS-Users mantiene ratings en varias dimensiones ya existentes en el modelo
(`eloFideRapid`, `eloFideBlitz`, `eloChesscomBullet/Blitz/Rapid`, además del ELO de
plataforma). El backlog (P1-05) plantea evaluar migrar de un `eloPlatform` único a un
rating **por familia de modalidad** al estilo Lichess (bullet / blitz / rapid / clásica),
ahora que P1-01 introduce el selector de modalidad y el reloj ya persiste
`timeControlInitialMs`/`timeControlIncrementMs` en `LiveGameSession`.

El costo real no es el cálculo (el `EloCalculator` de MS-Game es reutilizable por
dimensión) sino: (a) la migración de datos histórica, (b) que MS-Analytics y el
`game.finished` transporten la modalidad, y (c) el impacto en UI (rankings por modalidad).

## Opciones

1. **ELO único de plataforma (statu quo).** Un solo número; la modalidad solo afecta el reloj, no el rating.
   - + Cero migración, contratos intactos.
   - − No refleja que un jugador fuerte en clásica puede ser débil en bullet; menos fidelización.
2. **ELO por familia de modalidad (4 dimensiones internas).** Rating separado para bullet/blitz/rapid/clásica derivado del `timeControl` de la sesión.
   - + Alineado con estándar (Lichess/Chess.com); habilita filtros de lobby por modalidad (P1-02) con ELO real; mejor material para EP-O3.
   - − Migración expand→migrate→contract; `game.finished` debe incluir la familia de modalidad; K-factor y `totalGames` pasan a ser por dimensión (afecta integración MS-Analytics).
3. **Híbrido:** mantener `eloPlatform` como número "vitrina" y agregar dimensiones por modalidad de forma incremental (empezar solo con blitz+rapid, las más jugadas).
   - + Migración gradual, feature-flaggeable; menor riesgo.
   - − Dos fuentes de verdad temporales; hay que definir cuál se muestra dónde.

## Decisión

**Propuesta: Opción 3 (híbrido, incremental) — pendiente de confirmación del equipo.**
No bloquea R1. Se implementa el mapeo de `timeControl` → familia de modalidad como
utilidad compartida en R1 (necesaria igual para el tag PGN y el filtro P1-02), pero la
segmentación de rating se difiere: se activa por feature flag cuando se aborde EP-O3.

## Consecuencias

- R1 solo necesita: función `modalityFamily(initialMs, incrementMs)` (compartida front+back) y el tag `TimeControl` en el PGN. **No** toca el esquema de ratings todavía.
- Cuando se active: migración Flyway aditiva en `user_db` (`elo_bullet`, `elo_blitz`, `elo_rapid`, `elo_classical` nullable), `game.finished` gana campo `modalityFamily`, y MS-Analytics segmenta `PLAYER_STATS_MV` por modalidad.
- Riesgo a vigilar: `totalGames` para el K-factor pasa a ser por modalidad; coordinar con la integración MS-Game↔MS-Analytics ya pendiente.
