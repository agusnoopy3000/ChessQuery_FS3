# Historias de Usuario — ChessQuery

> **Propósito de este documento**
> Describir, en lenguaje claro y con el suficiente detalle técnico, las funcionalidades más relevantes de la plataforma **ChessQuery**. Está pensado para que cualquier miembro del equipo (desarrollo, QA, diseño, docentes evaluadores) o un usuario externo pueda entender **qué** hace la plataforma, **para quién**, y **bajo qué reglas**.
>
> **¿Qué es ChessQuery?**
> Una plataforma web basada en microservicios para gestionar el ajedrez competitivo en Chile: creación de torneos, inscripción de jugadores, registro de partidas con cálculo automático de ELO, almacenamiento de PGN y estadísticas. Proyecto académico del curso DSY1106 (Desarrollo Fullstack III, DuocUC).
>
> **Cómo leer este documento**
> Cada historia sigue la estructura:
> 1. **Rol → Objetivo → Beneficio** (historia en una frase)
> 2. **Contexto** — para qué sirve en la vida real
> 3. **Precondiciones** — qué debe cumplirse antes
> 4. **Flujo principal** — pasos numerados del caso feliz
> 5. **Flujos alternativos / errores** — qué pasa cuando algo falla
> 6. **Criterios de aceptación** — condiciones medibles para dar por cumplida la historia
> 7. **Notas técnicas** — endpoint, evento RabbitMQ, microservicio responsable, etc.

---

## Glosario rápido

| Término | Significado |
|---|---|
| **ELO** | Sistema numérico que mide la fuerza de un jugador de ajedrez. Sube o baja según resultados. |
| **FIDE** | Federación Internacional de Ajedrez. Publica el ELO oficial mundial. |
| **PGN** | *Portable Game Notation*. Archivo de texto estándar para guardar partidas de ajedrez. |
| **ECO** | *Encyclopedia of Chess Openings*. Catálogo estándar de aperturas (p.ej. C42 = Defensa Petrov). |
| **Swiss / Round-Robin / Knockout** | Formatos de torneo: Suizo (emparejamientos por puntaje acumulado), Round-Robin (todos contra todos), Knockout (eliminación directa). |
| **Buchholz / Sonneborn-Berger** | Sistemas de desempate usados cuando dos jugadores terminan con los mismos puntos. |
| **JWT** | Token firmado que identifica al usuario en cada petición HTTP. |
| **RabbitMQ** | Sistema de mensajería para comunicación asíncrona entre microservicios. |
| **BFF** | *Backend For Frontend*. Capa que adapta las APIs al frontend según el rol del usuario. |

---

## Roles del sistema

- **Jugador (PLAYER)** — Ajedrecista federado o casual. Se inscribe en torneos, juega partidas, consulta su ELO y estadísticas.
- **Organizador (ORGANIZER)** — Club, árbitro o federación regional. Crea y administra torneos, valida inscripciones, registra resultados.
- **Administrador (ADMIN)** — Staff de ChessQuery. Mantiene catálogos (clubes, países, títulos), supervisa la plataforma y resuelve incidencias.
- **Sistema** — Procesos automáticos: cálculo de ELO, detección de aperturas, sincronización con FIDE/Lichess, envío de notificaciones.

---

# Épica 1 — Autenticación y cuentas (MS-Auth)

## HU-01 · Registro de usuario

**Como** visitante sin cuenta,
**quiero** registrarme proporcionando email, contraseña y eligiendo mi rol (jugador u organizador),
**para** acceder a las funcionalidades personalizadas de ChessQuery.

**Contexto**
Todo usuario que quiera inscribirse en torneos, registrar partidas o crear competencias necesita una cuenta. El rol elegido al registrarse determina qué podrá hacer: un *Jugador* podrá inscribirse en torneos, mientras que un *Organizador* podrá crearlos.

**Precondiciones**
- El visitante no tiene cuenta previa con ese email.
- El visitante acepta los términos y condiciones (casilla obligatoria).

**Flujo principal**
1. El visitante accede a `/registro` en el portal web.
2. Completa el formulario: nombre completo, email, contraseña (mínimo 8 caracteres), confirmación de contraseña, rol (PLAYER / ORGANIZER).
3. El frontend envía `POST /auth/register` a MS-Auth.
4. MS-Auth valida que el email sea único, hashea la contraseña con BCrypt y crea el registro `auth_user`.
5. MS-Auth genera un JWT de acceso (15 min) y un refresh token (7 días, almacenado como hash SHA-256 en `refresh_token`).
6. MS-Auth publica el evento `user.registered` en RabbitMQ para que MS-Users cree el perfil extendido.
7. El visitante recibe respuesta 201 con `{ userId, accessToken, refreshToken, role }` y queda logueado.

**Flujos alternativos / errores**
- **Email duplicado** → 409 Conflict, mensaje "El email ya está registrado".
- **Contraseña débil** (<8 caracteres o sin número) → 400 Bad Request con detalles.
- **Rol inválido** → 400 Bad Request.

**Criterios de aceptación**
- [ ] Un nuevo usuario puede registrarse en menos de 3 segundos.
- [ ] La contraseña nunca se almacena en texto plano (BCrypt, cost factor ≥ 10).
- [ ] Se publica el evento `user.registered` y MS-Users crea el perfil `player` asociado.
- [ ] El email es único a nivel de base de datos (constraint).
- [ ] Respuesta 201 con estructura `{ userId, accessToken, refreshToken, role }`.

**Notas técnicas**
- Endpoint: `POST /auth/register`
- Microservicio: MS-Auth (puerto 9090, `auth_db:5432`)
- Evento emitido: `user.registered` (exchange `ChessEvents`, routing key `user.registered`)
- Tablas: `auth_user`, `refresh_token`

---

## HU-02 · Inicio de sesión (login)

**Como** usuario registrado,
**quiero** iniciar sesión con mi email y contraseña,
**para** obtener un token JWT que me identifique en toda la plataforma.

**Contexto**
El JWT emitido aquí viaja en el header `Authorization: Bearer <token>` de todas las peticiones posteriores. El API Gateway lo valida contra MS-Auth en cada request.

**Precondiciones**
- El usuario tiene una cuenta activa.

**Flujo principal**
1. Usuario completa `/login` con email y contraseña.
2. Frontend envía `POST /auth/login`.
3. MS-Auth busca el usuario por email y compara el hash BCrypt.
4. Si coincide, genera `accessToken` (15 min) y `refreshToken` (7 días).
5. Guarda el hash del refresh token en `refresh_token` asociado al dispositivo/IP.
6. Responde 200 con `{ accessToken, refreshToken, userId, role, expiresIn }`.

**Flujos alternativos / errores**
- **Credenciales inválidas** → 401 Unauthorized, mensaje genérico "Email o contraseña incorrectos" (no revelar cuál falló).
- **Cuenta bloqueada** (futuro: tras N intentos fallidos) → 423 Locked.

**Criterios de aceptación**
- [ ] Login exitoso responde en <500 ms.
- [ ] Se soporta login simultáneo en múltiples dispositivos (cada uno con su refresh token).
- [ ] Tras login, el JWT es aceptado por todos los microservicios vía API Gateway.
- [ ] Los intentos fallidos no revelan si el email existe o no.

**Notas técnicas**
- Endpoint: `POST /auth/login`
- Algoritmo JWT: HS256 (jjwt 0.12.5)
- Payload JWT: `{ sub: userId, email, role, iat, exp }`

---

## HU-03 · Refresh de sesión

**Como** usuario con sesión activa cuyo access token expiró,
**quiero** renovar mi token usando el refresh token,
**para** continuar navegando sin volver a ingresar credenciales.

**Contexto**
Los access tokens duran solo 15 minutos por seguridad. El refresh token permite obtener uno nuevo sin pedir credenciales, siempre que no haya sido revocado.

**Flujo principal**
1. Frontend detecta 401 por token expirado.
2. Envía `POST /auth/refresh` con el refresh token.
3. MS-Auth verifica que el hash SHA-256 exista y no esté revocado ni expirado.
4. Genera un nuevo access token (y opcionalmente rota el refresh token).
5. Responde 200 con `{ accessToken, refreshToken }`.

**Errores**
- Refresh token inválido/revocado → 401, el usuario debe volver a loguearse.

**Criterios de aceptación**
- [ ] Un refresh token puede usarse hasta su expiración (7 días) salvo logout explícito.
- [ ] Refresh tokens se almacenan hasheados (SHA-256), nunca en texto plano.

---

## HU-04 · Cierre de sesión (logout)

**Como** usuario con sesión activa,
**quiero** cerrar sesión invalidando mi refresh token,
**para** proteger mi cuenta especialmente si perdí el dispositivo.

**Flujo principal**
1. Usuario pulsa "Cerrar sesión".
2. `POST /auth/logout` con el refresh token.
3. MS-Auth marca el refresh token como `revoked=true`.
4. Frontend borra tokens del almacenamiento local.

**Criterios de aceptación**
- [ ] Tras logout, el refresh token queda inutilizable aunque el atacante lo tenga.
- [ ] El access token (corto) se deja expirar; no hay blacklist.
- [ ] Existe opción "Cerrar sesión en todos los dispositivos" (revoca todos los refresh tokens del usuario).

---

# Épica 2 — Perfil del jugador (MS-Users)

## HU-05 · Ver perfil público de un jugador

**Como** visitante, jugador u organizador,
**quiero** consultar el perfil de un jugador (nombre, club, país, ELO nacional, ELO FIDE, título, categoría de edad, historial básico),
**para** conocer su nivel y decidir inscripciones, emparejamientos o simplemente informarme.

**Contexto**
El perfil es la "tarjeta de presentación" del jugador en la plataforma. Un organizador lo consulta antes de aceptar una inscripción en un torneo con ELO mínimo; otro jugador lo revisa antes de un enfrentamiento.

**Flujo principal**
1. Usuario navega a `/jugadores/{id}`.
2. Frontend llama `GET /users/{id}`.
3. MS-Users retorna el perfil con datos denormalizados de club y país.

**Criterios de aceptación**
- [ ] Muestra: nombre completo, RUT (formato `12345678-9`, solo para chilenos), club, país, título FIDE (si aplica), ELO nacional, ELO FIDE, categoría de edad calculada (SUB8..SUB20, OPEN, SENIOR).
- [ ] Respeta privacidad: email y teléfono **no** se exponen públicamente.
- [ ] Tiempo de respuesta <300 ms (perfil cacheable).

**Notas técnicas**
- Endpoint: `GET /users/{id}`
- Categoría de edad calculada en runtime a partir de `fechaNacimiento`.

---

## HU-06 · Búsqueda de jugadores tolerante a typos

**Como** organizador,
**quiero** buscar jugadores por nombre aunque escriba con errores tipográficos o falten tildes,
**para** inscribirlos rápidamente sin conocer su ID.

**Contexto ejemplo**
Busco "Garido" y quiero encontrar "Agustín Garrido Castro". El sistema debe ser suficientemente tolerante para uso real en un torneo con cientos de inscritos.

**Flujo principal**
1. Organizador escribe texto en el buscador de la vista "Inscribir jugador".
2. Frontend llama `GET /users/search?q=garido&limit=10`.
3. MS-Users usa la extensión PostgreSQL `pg_trgm` para calcular similitud trigramática.
4. Retorna los 10 jugadores más similares, ordenados por score descendente.

**Criterios de aceptación**
- [ ] Encuentra resultados con hasta 2 errores tipográficos.
- [ ] Insensible a mayúsculas/minúsculas y tildes.
- [ ] Tiempo de respuesta <500 ms con 10.000 jugadores en BD.
- [ ] Pagina resultados si hay más de 20.

---

## HU-07 · Ranking nacional por categoría de edad

**Como** jugador,
**quiero** ver el ranking chileno filtrado por mi categoría (SUB12, SUB18, OPEN, SENIOR, etc.),
**para** comparar mi progreso con jugadores de mi edad.

**Flujo principal**
1. Usuario entra a `/ranking` y selecciona categoría.
2. `GET /users/ranking?category=SUB18&page=0&size=50`.
3. MS-Users retorna paginado por `eloNational DESC`.

**Criterios de aceptación**
- [ ] Muestra: posición, nombre, club, ELO nacional, variación últimos 30 días.
- [ ] Solo incluye jugadores activos (con al menos 1 partida en los últimos 12 meses).
- [ ] Exportable a CSV (deseable).

---

## HU-08 · Historial de ELO del jugador

**Como** jugador,
**quiero** ver un gráfico con la evolución de mi ELO a lo largo del tiempo,
**para** analizar mi progreso y detectar torneos que me impactaron positiva o negativamente.

**Flujo principal**
1. Jugador entra a su perfil → pestaña "Historial".
2. `GET /users/{id}/rating-history?from=2025-01-01&to=2026-04-24`.
3. Frontend dibuja gráfico de líneas con fecha vs. ELO.

**Criterios de aceptación**
- [ ] Muestra cada cambio de ELO con fecha, ELO antes/después y partida asociada (link).
- [ ] Permite filtrar por rango de fechas.

---

# Épica 3 — Torneos (MS-Tournament)

## HU-09 · Crear un torneo

**Como** organizador,
**quiero** crear un torneo configurando formato, fechas, sede, cupos y restricciones de ELO,
**para** publicarlo y recibir inscripciones de jugadores.

**Contexto**
Es el caso de uso central de los clubes. Un torneo típico: "Abierto de Santiago 2026 · Suizo a 7 rondas · cupo 80 · ELO mín 1400".

**Precondiciones**
- El usuario está autenticado con rol `ORGANIZER`.

**Flujo principal**
1. Organizador completa formulario: nombre, descripción, formato (SWISS / ROUND_ROBIN / KNOCKOUT), fecha inicio/fin, sede, cupos máximos, ELO mínimo, ELO máximo, número de rondas.
2. Frontend envía `POST /tournaments` con header `X-User-Role: ORGANIZER`.
3. MS-Tournament valida datos, crea el torneo en estado `DRAFT` y publica `tournament.created`.
4. Organizador puede luego cambiar estado a `OPEN` (`PATCH /tournaments/{id}/status`) para recibir inscripciones.

**Flujos alternativos**
- Datos inválidos (fecha fin antes que inicio, cupos <= 0) → 400 con mensaje.
- Usuario sin rol ORGANIZER → 403 Forbidden.

**Criterios de aceptación**
- [ ] Estados permitidos y transiciones: `DRAFT → OPEN → IN_PROGRESS → FINISHED`.
- [ ] No se puede saltar estados (ej. DRAFT → FINISHED directamente).
- [ ] El evento `tournament.created` incluye id, nombre, formato, organizerId, fechas.
- [ ] Un torneo en `DRAFT` solo lo ve su organizador.

**Notas técnicas**
- Endpoint: `POST /tournaments`
- Tabla: `tournament`
- Evento: `tournament.created`

---

## HU-10 · Inscribirse en un torneo

**Como** jugador,
**quiero** inscribirme en un torneo abierto,
**para** competir y sumar partidas oficiales.

**Precondiciones**
- Jugador autenticado con rol `PLAYER`.
- El torneo está en estado `OPEN`.

**Flujo principal**
1. Jugador ve el torneo y pulsa "Inscribirme".
2. Frontend envía `POST /tournaments/{id}/registrations`.
3. MS-Tournament consulta el ELO del jugador llamando a MS-Users (`GET /users/{id}/elo`), protegido con **Circuit Breaker** (Resilience4j). Si MS-Users no responde, usa ELO fallback = 1500.
4. Valida: no duplicado, cupos disponibles, ELO dentro del rango `[eloMin, eloMax]`.
5. Crea `tournament_registration` en estado `CONFIRMED` y publica `player.registered`.
6. Responde 201 con los datos de la inscripción.

**Flujos alternativos**
- Cupo lleno → 409 Conflict.
- ELO fuera de rango → 422 Unprocessable Entity.
- Ya inscrito → 409 Conflict.
- Torneo cerrado → 400 Bad Request.

**Criterios de aceptación**
- [ ] Si MS-Users está caído, la inscripción sigue funcionando con ELO fallback 1500 (resiliencia).
- [ ] No es posible inscribirse dos veces al mismo torneo.
- [ ] Se publica `player.registered` con `{ tournamentId, playerId, eloSeed }`.

---

## HU-11 · Generar emparejamientos de una ronda

**Como** organizador,
**quiero** generar los emparejamientos de cada ronda automáticamente según el formato del torneo,
**para** evitar errores manuales y acelerar la logística del evento.

**Contexto**
Cada formato usa una estrategia distinta:
- **Suizo**: empareja por puntaje acumulado, evitando repetir rival y alternando colores.
- **Round-Robin**: todos contra todos, calendario fijo.
- **Knockout**: eliminación directa, ganador avanza.

Se implementa con **Factory Method** (`PairingStrategyFactory.getStrategy(format)`) que retorna la estrategia correspondiente.

**Flujo principal**
1. Organizador pulsa "Generar ronda 3".
2. `POST /tournaments/{id}/rounds/3`.
3. MS-Tournament usa la estrategia adecuada y crea `tournament_pairing` para cada mesa.
4. Publica `tournament.round.starting` para que MS-Notifications avise a los jugadores.

**Criterios de aceptación**
- [ ] Suizo: nunca empareja a dos jugadores que ya se enfrentaron.
- [ ] Suizo: diferencia de puntos entre emparejados es mínima posible.
- [ ] Manejo de "bye" (jugador impar) con bonificación de 1 punto.
- [ ] Round-Robin respeta calendario Berger.

---

## HU-12 · Registrar resultado de una partida del torneo

**Como** árbitro/organizador,
**quiero** registrar el resultado de cada emparejamiento (1-0, 0-1, ½-½),
**para** actualizar la tabla de posiciones en tiempo real.

**Flujo principal**
1. Árbitro entra a la vista de ronda actual.
2. Por cada mesa, registra resultado: `PATCH /tournaments/pairings/{pairingId}/result` con `{ result: "WHITE_WIN" | "BLACK_WIN" | "DRAW" }`.
3. MS-Tournament actualiza el pairing.
4. Opcional: se dispara creación de Game en MS-Game si se carga también el PGN.

**Criterios de aceptación**
- [ ] No se puede modificar resultado tras cerrar la ronda (requiere re-abrir por un admin).
- [ ] Resultados se reflejan en standings en <2 segundos.

---

## HU-13 · Consultar tabla de posiciones con desempates

**Como** jugador, organizador o espectador,
**quiero** ver la tabla de posiciones ordenada por puntos y aplicando desempates Buchholz y Sonneborn-Berger,
**para** conocer la clasificación oficial durante y después del torneo.

**Flujo principal**
1. Usuario entra a `/torneos/{id}/standings`.
2. `GET /tournaments/{id}/standings`.
3. MS-Tournament calcula puntos, Buchholz (suma de puntos de rivales) y Sonneborn-Berger (suma ponderada por resultados).
4. Retorna ranking ordenado por: `puntos DESC, buchholz DESC, sonnebornBerger DESC`.

**Criterios de aceptación**
- [ ] Standings se actualizan automáticamente al cerrar cada ronda.
- [ ] Muestran: posición, jugador, puntos, partidas jugadas, Buchholz, S-B, ELO performance.
- [ ] Tiempo de respuesta <1 s para torneos de hasta 100 jugadores.

---

## HU-14 · Listar y filtrar torneos

**Como** jugador,
**quiero** buscar torneos por estado (abierto, en curso, finalizado), formato y fechas,
**para** encontrar competencias acordes a mi nivel e interés.

**Criterios de aceptación**
- [ ] `GET /tournaments?status=OPEN&format=SWISS&page=0&size=20` retorna paginado.
- [ ] Ordenable por fecha de inicio.
- [ ] Incluye resumen: nombre, fechas, sede, cupos (usados/totales), ELO rango.

---

# Épica 4 — Partidas y cálculo de ELO (MS-Game)

## HU-15 · Registrar una partida con cálculo automático de ELO

**Como** organizador o jugador (en partidas casuales),
**quiero** registrar una partida indicando jugadores, resultado y PGN,
**para** que el sistema calcule el nuevo ELO de ambos jugadores y detecte la apertura automáticamente.

**Contexto**
Este es el corazón funcional del sistema. Cada partida:
1. Actualiza el ELO de los dos jugadores según la fórmula FIDE.
2. Identifica la apertura a partir del PGN (primeros 10 movimientos).
3. Sube el PGN a almacenamiento S3 (MinIO local) para su consulta futura.
4. Dispara eventos que disparan notificaciones y actualizan estadísticas.

**Fórmula ELO usada (FIDE estándar)**
- `K = 32` si el jugador tiene menos de 30 partidas registradas.
- `K = 16` si tiene 30 o más.
- `ELO_nuevo = ELO_actual + K * (resultado - expectativa)`
- `expectativa = 1 / (1 + 10^((ELO_rival - ELO_propio)/400))`
- `resultado` = 1 (victoria), 0.5 (empate), 0 (derrota).

> **Nota actual**: hasta que MS-Analytics esté implementado, `totalGames` se toma como 0 y se usa siempre K=32.

**Flujo principal**
1. Organizador (o jugador en partida casual) completa: whitePlayerId, blackPlayerId, result, gameType (TOURNAMENT / CASUAL), tournamentId (opcional), PGN.
2. Frontend envía `POST /games`.
3. MS-Game:
   - Valida campos.
   - Extrae primeros 10 movimientos del PGN.
   - Busca apertura por prefijo más largo en tabla `opening` (90+ aperturas ECO precargadas).
   - Calcula nuevo ELO de ambos jugadores.
   - Sube PGN a MinIO: `games/{yyyy}/{mm}/{gameId}.pgn`.
   - Persiste en tabla `game`.
   - Publica `game.finished` y dos eventos `elo.updated` (uno por jugador).
4. MS-Users consume `elo.updated` desde cola dedicada `users.elo.queue` y actualiza el ELO del jugador.
5. MS-Analytics (futuro) consume `game.finished` y actualiza `PLAYER_STATS_MV`.

**Criterios de aceptación**
- [ ] El cálculo de ELO es simétrico: lo que gana uno lo pierde el otro (en victoria/derrota).
- [ ] En empate, ambos ELOs convergen hacia el promedio.
- [ ] La apertura detectada coincide con el código ECO correcto en al menos 95% de casos con PGN estándar.
- [ ] El PGN se sube exitosamente y es recuperable por URL presignada.
- [ ] Los eventos se publican siempre antes de responder 201.

**Notas técnicas**
- Endpoint: `POST /games`
- Microservicio: MS-Game (puerto 8083)
- Almacenamiento: MinIO bucket `chessquery-pgn`
- Eventos: `game.finished`, `elo.updated` (x2)
- Cola dedicada para MS-Users: `users.elo.queue` (routing key `elo.*`)

---

## HU-16 · Descargar el PGN de una partida

**Como** jugador, analista o entrenador,
**quiero** descargar el PGN de cualquier partida registrada,
**para** estudiarla en software especializado (ChessBase, Lichess, Scid).

**Flujo principal**
1. Usuario entra al detalle de la partida.
2. Pulsa "Descargar PGN" → `GET /games/{id}/pgn-url`.
3. MS-Game genera URL presignada S3 válida por 1 hora.
4. El navegador descarga el archivo directamente desde MinIO.

**Criterios de aceptación**
- [ ] URL presignada expira exactamente en 1 hora.
- [ ] No se expone el bucket ni credenciales de S3.
- [ ] Partidas de torneos públicos son accesibles; partidas privadas (futuro) requieren ser participante.

---

## HU-17 · Historial de partidas de un jugador

**Como** jugador,
**quiero** filtrar mis partidas por tipo (torneo/casual), resultado (victoria/empate/derrota) y rango de fechas,
**para** analizar mi desempeño y preparar futuros torneos.

**Criterios de aceptación**
- [ ] `GET /games?playerId={id}&gameType=TOURNAMENT&result=WIN&page=0&size=20`.
- [ ] Muestra: fecha, rival, color, resultado, ELO antes/después, apertura, torneo (si aplica).
- [ ] Ordenable por fecha descendente por defecto.

---

# Épica 5 — Analítica (MS-Analytics, pendiente)

## HU-18 · Estadísticas agregadas del jugador

**Como** jugador,
**quiero** ver estadísticas agregadas: porcentaje de victorias con blancas vs. negras, aperturas más jugadas y con mejores resultados, rival histórico más fuerte,
**para** identificar fortalezas y debilidades concretas.

**Contexto**
Estas estadísticas se mantienen pre-agregadas en la tabla `PLAYER_STATS_MV` de `analytics_db`, actualizada por consumidores de eventos `game.finished`. Así se evitan JOINs costosos entre microservicios.

**Criterios de aceptación**
- [ ] `GET /analytics/players/{id}/stats` retorna en <200 ms.
- [ ] Incluye: totalGames, winRate, winRateWhite, winRateBlack, top5Openings, toughestOpponent.
- [ ] Se actualiza en <5 segundos tras registrar una partida nueva.

---

## HU-19 · Aperturas más populares del circuito chileno

**Como** organizador, entrenador o analista,
**quiero** ver un ranking de las aperturas más jugadas por categoría de edad o ELO,
**para** entender tendencias del meta nacional y preparar material didáctico.

**Criterios de aceptación**
- [ ] Filtrable por categoría, rango de ELO, rango de fechas.
- [ ] Muestra: código ECO, nombre, veces jugada, % victorias con blancas.

---

# Épica 6 — Notificaciones (MS-Notifications, pendiente)

## HU-20 · Notificación de inicio de ronda

**Como** jugador inscrito en un torneo,
**quiero** recibir una notificación (in-app y/o email) cuando comienza mi ronda,
**para** no perder por incomparecencia y saber a qué mesa dirigirme.

**Flujo**
1. MS-Tournament publica `tournament.round.starting`.
2. MS-Notifications consume, identifica a los jugadores de esa ronda, genera notificaciones.
3. Usuario ve la notificación en el portal (y recibe email si lo configuró).

**Criterios de aceptación**
- [ ] Notificación llega en <30 segundos desde el evento.
- [ ] Incluye: nombre del torneo, número de ronda, mesa asignada, rival, color.

---

## HU-21 · Notificación de cambio de ELO

**Como** jugador,
**quiero** recibir una notificación cuando mi ELO cambia tras una partida,
**para** ver el impacto inmediato del resultado.

**Criterios de aceptación**
- [ ] Notificación disparada por evento `elo.updated`.
- [ ] Incluye: partida, ELO anterior, ELO nuevo, variación (+/- puntos).

---

# Épica 7 — ETL y sincronización externa (MS-ETL, pendiente)

## HU-22 · Sincronización mensual con FIDE

**Como** sistema,
**quiero** sincronizar mensualmente los ratings FIDE oficiales de todos los jugadores chilenos registrados,
**para** mantener actualizado el campo `eloFide` sin intervención manual.

**Flujo**
1. Job programado (cron mensual) descarga la lista oficial FIDE.
2. MS-ETL cruza por `fideId` con jugadores locales y actualiza `eloFide`, `fideTitle`.
3. Publica `rating.updated` por cada cambio y `sync.completed` al finalizar.

**Criterios de aceptación**
- [ ] Proceso idempotente (reejecutar no duplica datos).
- [ ] Registra log de auditoría con cantidad de jugadores actualizados y errores.

---

## HU-23 · Import de partidas desde Lichess

**Como** jugador,
**quiero** vincular mi cuenta Lichess para importar mis partidas públicas,
**para** enriquecer mi historial sin subir PGNs manualmente uno a uno.

---

# Épica 8 — Administración y plataforma

## HU-24 · Gestión del catálogo (clubes, países, títulos)

**Como** administrador,
**quiero** crear, editar y desactivar clubes, países y títulos FIDE,
**para** mantener la integridad del catálogo compartido entre todos los microservicios.

**Criterios de aceptación**
- [ ] CRUD completo en panel admin.
- [ ] Club no se puede eliminar si tiene jugadores asociados (soft delete).

---

## HU-25 · Rate limiting por IP

**Como** administrador,
**quiero** que el API Gateway limite a 100 requests por minuto por IP,
**para** prevenir abuso, ataques de fuerza bruta y scraping.

**Criterios de aceptación**
- [ ] Rate limit implementado con Redis en API Gateway.
- [ ] Respuesta 429 Too Many Requests con header `Retry-After`.
- [ ] IPs internas (microservicios) exentas.

---

## HU-26 · Validación centralizada de JWT en el Gateway

**Como** sistema,
**quiero** que el API Gateway valide cada request llamando a `GET /auth/validate` de MS-Auth,
**para** que los microservicios downstream confíen exclusivamente en los headers `X-User-Id`, `X-User-Email`, `X-User-Role` inyectados por el Gateway.

**Beneficio**: un único punto de verdad para autenticación; los microservicios no cargan con lógica JWT.

**Criterios de aceptación**
- [ ] Ningún microservicio (excepto MS-Auth) decodifica JWT directamente.
- [ ] Requests sin JWT válido retornan 401 antes de llegar al microservicio.
- [ ] Si MS-Auth está caído, el Gateway retorna 503 Service Unavailable.

---

# Priorización MVP (MoSCoW)

| Prioridad | Historias | Razón |
|---|---|---|
| **Must** (imprescindibles para MVP académico) | HU-01, HU-02, HU-03, HU-05, HU-09, HU-10, HU-11, HU-12, HU-13, HU-15, HU-26 | Flujo mínimo end-to-end: registrarse, crear torneo, inscribirse, jugar, ver standings. |
| **Should** (deseables para una demo completa) | HU-04, HU-06, HU-07, HU-14, HU-16, HU-17, HU-25 | Mejoran UX y seguridad sin ser bloqueantes. |
| **Could** (agregan valor si hay tiempo) | HU-08, HU-18, HU-19, HU-20, HU-21, HU-24 | Dependen de microservicios aún no implementados. |
| **Won't** (fuera de alcance del curso) | HU-22, HU-23 | Integraciones externas complejas; quedan como roadmap post-MVP. |

---

# Trazabilidad — Historia ↔ Microservicio ↔ Estado

| Historia | Microservicio(s) responsable(s) | Estado actual |
|---|---|---|
| HU-01 a HU-04 | MS-Auth | ✅ Completado |
| HU-05 a HU-08 | MS-Users | ✅ Completado (HU-08 depende del frontend) |
| HU-09 a HU-14 | MS-Tournament | ✅ Completado |
| HU-15 a HU-17 | MS-Game | ✅ Completado |
| HU-18, HU-19 | MS-Analytics | ⏳ Pendiente |
| HU-20, HU-21 | MS-Notifications | ⏳ Pendiente |
| HU-22, HU-23 | MS-ETL | ⏳ Pendiente |
| HU-24 | Admin Panel + microservicios | ⏳ Pendiente |
| HU-25, HU-26 | API Gateway | ⏳ Pendiente |

---

# Referencias cruzadas

- **Contratos de API y eventos**: ver `CONTEXT.md` en la raíz del proyecto.
- **Modelo de datos completo (ERD)**: ver `CONTEXT.md` sección "Data model".
- **Decisiones arquitectónicas**: ver `CLAUDE.md` sección "Key architectural decisions".
- **Puertos y credenciales de desarrollo**: ver `CLAUDE.md` sección "Service ports".
