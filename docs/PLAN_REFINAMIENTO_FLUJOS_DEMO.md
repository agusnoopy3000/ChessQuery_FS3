# Plan — Refinamiento de los flujos de la demo

> Para seguir después. Foco: que las 4 escenas que se muestran en la presentación
> (deck `ChessQuery_ContextoDemo.html`) se vean y respondan impecables frente a
> clientes/académicos. Prioridad **P0** = se muestra sí o sí en la demo; **P1** =
> mejora visible; **P2** = nice-to-have.

## Estado actual (lo ya hecho)
- UX/accesibilidad (4 fases) + auth full-screen → rama `feat/ux-refinamiento-presentacion` (pusheada).
- ELO de ChessQuery (`eloPlatform`): se envía y se muestra en el dashboard.
- Notificaciones: bandeja por sesión (nueva sesión = vacía).
- Correos: bienvenida, invitación a partida y **todo el flujo de inscripción a torneo** (confirmada/recibida/aprobada/rechazada/convocatoria) — App Password OK, remitente `agucastro.656@gmail.com`.
- Front en S3 con cache-control (index.html `no-cache`). QR del portal lista.

## ⚙️ Operación (importante para la demo)
- **El backend ahora es `chessquery-stack:15`** (incluye el fix de correos). El runbook viejo apunta a `:12` — **actualizarlo a `:15`** o los correos de torneo no saldrán.
- Encender: RDS `available` → `aws ecs update-service ... --task-definition chessquery-stack:15 --desired-count 1 --force-new-deployment`. Apagar: desired 0 + `rds stop-db-instance`.

---

## Escena 1 — Registro & onboarding
- **P0** Verificar de punta a punta el correo de **bienvenida** (ya funciona; confirmar que llega y se ve bien en móvil del que escanea la QR).
- **P1** Onboarding del jugador nuevo: el dashboard arranca con `eloPlatform` null → muestra "SIN RATING"/"Aún sin partidas". Sembrar un ELO inicial (p. ej. 1200) al crear el perfil para que el KPI no se vea vacío en la demo.
- **P1** Login/registro: el panel decorativo lateral **no se oculta** (el `display:flex` inline gana al CSS). Decidir: dejar el diseño partido (actual, se ve bien) o terminar el "solo formulario centrado" quitando el panel del JSX.
- **P2** Estados de error del registro (email ya usado, password débil) con copy claro.

## Escena 2 — Torneo end-to-end (organizador)
- **P0** Flujo completo sin fricción: crear torneo → abrir inscripciones → aprobar jugador → generar ronda 1. Probar con datos reales antes de la demo.
- **P0** Confirmar que **al generar la ronda** llega la **convocatoria por correo** (game.invitation de torneo, recién agregado) a los jugadores emparejados.
- **P1** `RegistrationsPanel`: feedback al aprobar/rechazar (toast "aprobado", fila que se actualiza sin recargar).
- **P1** Estados visibles del torneo (DRAFT → OPEN → IN_PROGRESS) con un indicador de progreso claro.
- **P2** Edición/ajuste manual de un emparejamiento (hoy solo regenerar toda la ronda).
- **P2** Preview de emparejamientos antes de confirmar la generación de ronda.

## Escena 3 — Partida en vivo + PGN
- **P0** Dos navegadores jugando 1v1: validación server-side, broadcast Realtime, reloj, PGN al terminar. Ensayar la coreografía (quién abre qué).
- **P0** Que el **ELO acumule bien**: el front ya envía `eloPlatform` como `eloBefore`. Verificar en una partida real que el ELO cambia desde el rating real (no desde 1500).
- **P1** Indicador claro de "esperando rival / reconectando" en estado WAITING.
- **P1** Animaciones de captura/jaque ya existen (Chessground); confirmar que respetan `reduce-motion`.
- **P2** **Follow-up técnico:** mover el `eloBefore` al **backend** (BFF/ms-game lo toma de ms-users) en vez de confiar en el cliente. Requiere cambio de backend + redeploy.

## Escena 4 — ELO + notificaciones
- **P0** Tras `game.finished`: recálculo de ELO + notificación in-app (toast) + correo de fin de partida. Verificar que el dashboard refleja el nuevo `eloPlatform`.
- **P0** Bandeja de notificaciones: **sesión nueva = vacía** (ya implementado). Probar: login → notificación llega → logout/login → bandeja limpia.
- **P1** Correo de fin de partida (`game.finished`) hoy es solo in-app: evaluar si se agrega email (mismo patrón que el de torneo).
- **P1** Chip "Sincronizando/Actualizado" en standings (ya hecho) — confirmar que se ve durante el refetch en vivo.

---

## Verificación general (antes de presentar)
1. Encender infra con **rev 15** y esperar RDS `available` + readiness `UP`.
2. Recorrido completo de las 4 escenas con 2 cuentas reales (una por la QR).
3. Revisar correos en la bandeja real (bienvenida, convocatoria, fin de partida).
4. Móvil a 375px: registro → portal → jugar → fin, sin scroll horizontal ni negros.
5. `npm run test` (chess-portal 37 / organizer 38) + builds verdes antes de re-sync a S3.

## Deuda técnica anotada
- `eloBefore` calculado en cliente (escena 3, P2) → llevar a backend.
- Remitente de correos: `agucastro.656@gmail.com` (no el `chessquery.invitaciones` que figuraba en el PR #36). Unificar si se quiere marca propia.
- Runbook desactualizado: referencia `:12`, ya debería ser `:15`.
