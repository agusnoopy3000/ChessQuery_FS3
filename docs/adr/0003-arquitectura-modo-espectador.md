# ADR-0003 — Arquitectura del modo espectador (link público)

- **Estado:** Propuesto
- **Fecha:** 2026-07-08
- **Épicas relacionadas:** EP-S1 (S1-01…S1-04)
- **Decide:** equipo (el modelo de seguridad requiere revisión humana)

## Contexto

El organizador genera un link público (sin cuenta) para que espectadores vean las
partidas en vivo, standings y resultados de un torneo. Debe: ser solo-lectura, no
exponer datos personales más allá de nombre/ELO, resistir carga de muchos espectadores
concurrentes, y admitir un delay anti-trampa opcional (ej. 30s como transmisiones FIDE).

Ya existe infra reutilizable: `LiveGameBroadcaster` en MS-Game y Supabase Realtime.
El portal es un monorepo (`frontend/apps/chess-portal`, `frontend/apps/organizer-panel`).

## Opciones (dos ejes)

### Eje A — Transporte del tablero en vivo
1. **Reusar el broadcaster de sesión + Supabase Realtime con un canal broadcast separado por torneo.**
   - + Reutiliza lo existente; RLS de Supabase controla el acceso read-only.
   - − Hay que garantizar que el canal público no filtre PII (proyectar solo nombre/ELO/movimientos).
2. **Endpoint público de polling en el Gateway.**
   - + Simple, sin Realtime.
   - − Es justo la deuda que O6-04 quiere saldar (polling→realtime); peor UX y más tráfico.

### Eje B — Superficie de UI y autorización
1. **Ruta pública dentro de `chess-portal`** (`/spectator/:token`), token de solo lectura por torneo.
2. **Micro-frontend `spectator` separado.**
   - + Aislamiento total del bundle autenticado.
   - − Otro deploy/pipeline; sobredimensionado para el alcance.

## Decisión

**Propuesta:**
- **Eje A → Opción 1:** canal Supabase Realtime **broadcast dedicado** por torneo, con payload saneado (sin PII), delay configurable opcional, y **fallback a polling** si el canal cae.
- **Eje B → Opción 1:** ruta pública en `chess-portal` con **token de solo lectura por torneo** (generado/revocado por el organizador, S1-01), QR descargable.
- **Autorización:** token no permite ninguna mutación; RLS en los canales Realtime; rate limiting específico para rutas públicas usando el Redis ya existente (S1-04).

## Consecuencias

- Prerequisito duro: **CloudFront delante de S3 (HTTPS público)** para que el link sea compartible y habilite Service Workers/push (ya identificado en Fase 1 del backlog).
- MS-Tournament gana: entidad/campo `spectatorToken` por torneo + endpoints de generar/revocar; endpoint público (o vía Gateway) para standings/resultados read-only con `og:tags` (S1-03).
- MS-Game: exponer un feed de movimientos saneado para el canal broadcast (nunca el canal privado de la sesión).
- Prueba de carga ligera con k6 sobre el canal broadcast antes de habilitar a un torneo real (riesgo "Realtime a escala" del backlog).
- Esta decisión también salda O6-04 (standings del player de polling→realtime) al compartir el mecanismo.
