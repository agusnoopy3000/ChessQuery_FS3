# R20 — Verificación de flujos sugeridos del PLAN_DEMO

Checklist de validación end-to-end de los flujos que el PLAN_DEMO promete.
Marcar conforme se ejecutan; capturar logs/screenshots cuando algo falle.

## 1. Torneo end-to-end (creación + inscripción + pairings)

**Pasos:**
1. ORGANIZER `carla@demo.cl` (pestaña 5174) → crear torneo "Demo Cup" SWISS, max 8.
2. PLAYER `ana@demo.cl` (5173) → tab "Torneos" → "Inscribirme".
3. PLAYER `bruno@demo.cl` (5173) → idem.
4. ORGANIZER → tab "Inscripciones" → confirmar ambos.
5. ORGANIZER → "Generar emparejamientos ronda 1".

**Verificación:**
- [ ] `POST /api/organizer/tournaments` → 201 (con R1 mergeado, NO 400).
- [ ] Studio → `tournament_db.tournament` → fila `status='OPEN'`.
- [ ] Studio → `tournament_registration` → 2 filas `CONFIRMED`.
- [ ] Studio → `tournament_pairing` → 1 fila con `white_player_id` y `black_player_id`.

**Si falla:** revisar logs `docker logs chessquery_ms_tournament` y
`chessquery_api_gateway`.

## 2. Standings + ELO recalculado tras game.finished

**Pasos:**
1. Ana y Bruno juegan la partida pareada (live game, jugar 4-6 movidas, una rinde).
2. ORGANIZER → tab "Standings" del torneo.

**Verificación:**
- [ ] Standings refleja 1.0 / 0.0 (o 0.5/0.5 si tablas).
- [ ] Studio → `game_db.game` → `tournament_id` no null.
- [ ] Studio → `user_db.player` → `elo_national` cambió (Ana subió, Bruno bajó por la fórmula FIDE K=32).
- [ ] Studio → `user_db.rating_history` → 2 filas nuevas (RatingType.NATIONAL).

**Mensaje técnico:** _"el evento `game.finished` viaja por RabbitMQ a
MS-Users, que recalcula ELO con K=32 y publica `elo.updated`"_.

## 3. Búsqueda fuzzy con pg_trgm

**Pasos:**
1. Chess-portal → buscador → tipear "Salin".
2. Aparece "Pablo Salinas" (seed FECHAJ).

**Verificación:**
- [ ] Resultado aparece en <500 ms.
- [ ] Click → perfil público con ELO nacional, club, ELO FIDE.
- [ ] Tipear "salnas" (typo) → sigue match (similitud trigram).

## 4. Circuit breaker MS-Users → MS-Game (fallback ELO 1500)

**Pasos:**
1. Con torneo ya en marcha: `docker stop chessquery_ms_users`.
2. Ana crea una partida live nueva (puede ser ad-hoc fuera del torneo).
3. La partida se crea con ELO de seed = 1500 (fallback).
4. `docker start chessquery_ms_users` → todo vuelve a la normalidad.

**Verificación:**
- [ ] La sesión live se crea sin error (no 500).
- [ ] Studio → `live_game_session.white_elo_before = 1500`.
- [ ] Logs ms-tournament/ms-game muestran "circuit breaker open" en algún
      lugar al perder MS-Users.
- [ ] Tras restart de ms-users, no requirió restart del resto.

**⚠️ FRÁGIL:** este flujo a veces deja al circuit breaker en estado HALF_OPEN
si MS-Users tarda en recuperar. Si no se ensaya antes, dejarlo afuera de la
demo.

## 5. Notificaciones in-app + email (N1 + N2)

**Pasos:**
1. Tras finalizar la partida del flujo 1, esperar ~2 segundos.

**Verificación:**
- [ ] Campana 🔔 de Ana muestra badge con `1`.
- [ ] Click → dropdown muestra "Partida #N finalizada · Ganaste/Perdiste".
- [ ] Mailpit (`http://127.0.0.1:54324`) recibió 1 email para
      `jugador-{id}@chessquery.cl` con el subject "Partida #N finalizada".
- [ ] Studio → `notif_db.notification_log` → 4 filas nuevas (2 EMAIL + 2 IN_APP).

**Si Mailpit/Inbucket no recibe:** verificar `SMTP_HOST` / `SMTP_PORT` en
`docker logs chessquery_ms_notifications` — debería ser `host.docker.internal:54325`.
Si el SMTP no responde, MockEmailService cae a log y el IN_APP igual queda
persistido (degradación graceful).

## 6. Tests automatizados

```bash
export JAVA_HOME="/Users/agustingarridosnoopy/Library/Java/JavaVirtualMachines/ms-21.0.8/Contents/Home"
mvn -f ms-game/pom.xml test
mvn -f ms-tournament/pom.xml test
mvn -f ms-users/pom.xml test
```

**Resultado esperado:** todos verdes. Snapshot al cierre del refinamiento:
- ms-game: 12/12
- ms-tournament: pasaba en main (no se tocó la lógica)
- ms-users: pasaba en main (cambios solo en consumer, sin tocar entidades)

## Archivos de evidencia

Adjuntar al cerrar la verificación:
- Screenshot del modal de fin de partida (con resultado grande).
- Screenshot de la campana 🔔 con dropdown abierto.
- Screenshot del Mailpit con el email del flujo 1.
- Screenshot de Studio con `tournament_pairing` poblado.
- Output de `mvn test` con tests verdes.
