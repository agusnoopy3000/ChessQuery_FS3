# Requirements Document — ChessQuery Pre-Demo Refinement

## Introduction

Este documento especifica los requisitos para el refinamiento pre-demo de ChessQuery, una plataforma de microservicios para ajedrez competitivo en Chile. El objetivo es preparar el sistema para la demo final del curso DSY1106 DuocUC, resolviendo bloqueantes críticos, mejorando la experiencia de usuario, agregando higiene técnica y verificando todos los flujos de demostración.

El sistema actual (2026-05-07) cuenta con 53 commits en feat/ajefech-integration, flujo live game end-to-end funcional, 5 features UX implementados, email magic link customizado y PR a main creado. El refinamiento se enfoca en estabilizar el sistema para una demo exitosa sin introducir cambios arquitectónicos mayores.

## Glossary

- **API_Gateway**: Componente que valida JWT, propaga headers downstream y aplica rate limiting
- **MS_Users**: Microservicio que gestiona perfiles de jugadores y ratings
- **MS_Tournament**: Microservicio que gestiona torneos, inscripciones y emparejamientos
- **MS_Game**: Microservicio que gestiona partidas, PGN y detección de aperturas
- **MS_Analytics**: Microservicio que mantiene estadísticas agregadas de jugadores
- **MS_Notifications**: Microservicio que procesa y envía notificaciones
- **Chess_Portal**: Frontend para jugadores (registro, partidas, torneos, perfil)
- **Organizer_Panel**: Frontend para organizadores (gestión de torneos)
- **Supabase_Auth**: Servicio de autenticación externo que emite JWT
- **GoTrue**: Motor de autenticación de Supabase
- **Magic_Link**: Email con token de autenticación de un solo uso
- **Healthcheck**: Endpoint que reporta el estado de salud de un servicio
- **WebClient**: Cliente HTTP reactivo de Spring para llamadas inter-servicio
- **Caffeine**: Librería de caché en memoria para Java
- **FEN**: Forsyth-Edwards Notation, formato estándar para representar posiciones de ajedrez
- **PGN**: Portable Game Notation, formato estándar para registrar partidas de ajedrez
- **ELO**: Sistema de rating numérico para jugadores de ajedrez
- **Realtime**: Sistema de comunicación bidireccional en tiempo real
- **Pre_Move**: Jugada anticipada que se ejecuta automáticamente cuando es el turno del jugador
- **Under_Promotion**: Promoción de peón a pieza menor (torre, alfil, caballo) en lugar de reina
- **Circuit_Breaker**: Patrón de resiliencia que previene cascadas de fallos
- **Dry_Run**: Ejecución de prueba completa sin audiencia
- **Demo_Seed**: Datos de prueba pre-cargados para demostración
- **Actuator**: Módulo de Spring Boot que expone endpoints de monitoreo
- **Flyway**: Herramienta de migración de bases de datos
- **Testcontainers**: Librería para ejecutar contenedores Docker en tests

## Requirements

### Requirement 1: User ID Resolution in API Gateway

**User Story:** Como organizador de torneos, quiero crear torneos desde la interfaz web, para que los jugadores puedan inscribirse y poder validar inscripcion de jugadores que se van a emparejar.

#### Acceptance Criteria

1. WHEN API_Gateway recibe un request autenticado con JWT de Supabase, THE API_Gateway SHALL extraer el UUID del claim `sub`
2. WHEN API_Gateway extrae el UUID de Supabase, THE API_Gateway SHALL invocar MS_Users para resolver el UUID a player.id numérico
3. WHEN MS_Users retorna el player.id numérico, THE API_Gateway SHALL cachear la resolución UUID→id con TTL de 5 minutos
4. WHEN API_Gateway tiene el player.id numérico, THE API_Gateway SHALL propagar el valor como header `X-User-Id` a servicios downstream
5. THE Cache SHALL almacenar máximo 10,000 entradas UUID→id
6. WHEN la entrada de caché expira después de 5 minutos, THE API_Gateway SHALL resolver nuevamente el UUID invocando MS_Users
7. IF MS_Users no responde en 2 segundos, THEN THE API_Gateway SHALL retornar HTTP 503 Service Unavailable
8. WHEN un usuario ORGANIZER crea un torneo, THE MS_Tournament SHALL recibir player.id numérico en header X-User-Id y retornar HTTP 201 Created

### Requirement 2: Service Health Monitoring

**User Story:** Como operador del sistema, quiero que Docker Compose detecte servicios no saludables, para que no se enruten requests a containers zombie.

#### Acceptance Criteria

1. THE API_Gateway SHALL exponer endpoint `/actuator/health` que retorna HTTP 200 cuando el servicio está saludable
2. THE MS_Users SHALL exponer endpoint `/actuator/health` que retorna HTTP 200 cuando el servicio está saludable
3. THE MS_Tournament SHALL exponer endpoint `/actuator/health` que retorna HTTP 200 cuando el servicio está saludable
4. THE MS_Game SHALL exponer endpoint `/actuator/health` que retorna HTTP 200 cuando el servicio está saludable
5. THE MS_Analytics SHALL exponer endpoint `/actuator/health` que retorna HTTP 200 cuando el servicio está saludable
6. THE MS_Notifications SHALL exponer endpoint `/actuator/health` que retorna HTTP 200 cuando el servicio está saludable
7. WHEN un servicio Java no puede conectarse a su base de datos, THE healthcheck SHALL retornar HTTP 503 Service Unavailable
8. THE docker-compose.yml SHALL configurar healthcheck con wget a `/actuator/health` para cada servicio Java con intervalo de 10 segundos
9. WHEN un healthcheck falla 3 veces consecutivas, THE Docker Compose SHALL marcar el container como unhealthy

### Requirement 3: Magic Link LAN IP Support Investigation

**User Story:** Como jugador móvil en la misma red local, quiero autenticarme con magic link usando la IP LAN del servidor, para que pueda jugar desde mi dispositivo móvil.Pero priorizar siempre la funcionalidad en la plataforma web

#### Acceptance Criteria

1. THE equipo de desarrollo SHALL investigar por qué GoTrue rechaza redirects a http://192.168.1.186:5173/play/N
2. THE equipo de desarrollo SHALL documentar los hallazgos de la investigación en un archivo MAGIC_LINK_LAN.md
3. IF no se encuentra solución en 2 horas de investigación, THEN THE equipo SHALL documentar el workaround de usar desktop para magic link en la demo
4. THE documentación SHALL incluir configuración actual de additional_redirect_urls en Supabase
5. THE documentación SHALL incluir logs de error de GoTrue si están disponibles

### Requirement 4: Captured Material Display

**User Story:** Como jugador en una partida, quiero ver las piezas capturadas y el balance material, para que pueda evaluar la posición rápidamente.

#### Acceptance Criteria

1. WHEN Chess_Portal renderiza un tablero de partida activa, THE Chess_Portal SHALL parsear el FEN para extraer las piezas en el tablero
2. WHEN Chess_Portal extrae las piezas del FEN, THE Chess_Portal SHALL calcular las piezas capturadas comparando con la posición inicial
3. THE Chess_Portal SHALL mostrar las piezas capturadas por blancas arriba del tablero
4. THE Chess_Portal SHALL mostrar las piezas capturadas por negras debajo del tablero
5. WHEN existe diferencia material, THE Chess_Portal SHALL calcular el delta numérico (peón=1, caballo=3, alfil=3, torre=5, reina=9)
6. WHEN el delta material es mayor a 0, THE Chess_Portal SHALL mostrar el delta junto a las piezas capturadas del jugador con ventaja
7. THE Chess_Portal SHALL actualizar la visualización de material capturado después de cada movimiento

### Requirement 5: Functional Game Clock

**User Story:** Como jugador en una partida con control de tiempo, quiero ver el reloj decrementar en tiempo real, para que pueda gestionar mi tiempo efectivamente.

#### Acceptance Criteria

1. WHEN Chess_Portal recibe un game state con clock_white_ms y clock_black_ms, THE Chess_Portal SHALL inicializar dos relojes con esos valores
2. WHILE es el turno de blancas, THE Chess_Portal SHALL decrementar clock_white_ms cada 100 milisegundos
3. WHILE es el turno de negras, THE Chess_Portal SHALL decrementar clock_black_ms cada 100 milisegundos
4. WHEN un jugador completa su movimiento, THE Chess_Portal SHALL enviar el tiempo restante al servidor
5. WHEN un reloj llega a 0 milisegundos, THE Chess_Portal SHALL notificar al servidor que el jugador perdió por tiempo
6. THE MS_Game SHALL registrar el resultado como victoria por tiempo del oponente
7. WHEN el servidor confirma time-out, THE Chess_Portal SHALL mostrar modal de fin de partida con resultado "Perdiste por tiempo"
8. THE Chess_Portal SHALL sincronizar los relojes con el servidor cada 30 segundos para corregir drift

### Requirement 6: Persist Lichess Username and Club in Registration

**User Story:** Como jugador nuevo, quiero que mi username de Lichess y club(opcional) se guarden al registrarme, para que mi perfil esté completo desde el inicio.

#### Acceptance Criteria

1. WHEN Chess_Portal muestra el formulario de registro, THE Chess_Portal SHALL incluir campos lichessUsername y club
2. WHEN un usuario completa el formulario de registro, THE Chess_Portal SHALL incluir lichessUsername y club en el payload POST a Supabase Auth
3. WHEN Supabase Auth crea el usuario, THE webhook user-registered SHALL incluir lichessUsername y club en el payload del evento
4. WHEN MS_Users consume el evento user.registered, THE MS_Users SHALL persistir lichessUsername en PLAYER.lichess_username
5. WHEN MS_Users consume el evento user.registered con club, THE MS_Users SHALL resolver el nombre del club a club_id y persistir en PLAYER.club_id
6. WHEN un usuario consulta su perfil después de registrarse, THE MS_Users SHALL retornar lichessUsername y club en la respuesta

### Requirement 7: Player Header with ELO and Country

**User Story:** Como jugador en una partida, quiero ver el nombrey  ELO  para que tenga contexto sobre con quién estoy jugando. Si el jugador todavia no tiene elo registrado debe quedar como "Sin elo" . 

#### Acceptance Criteria

1. WHEN Chess_Portal renderiza una partida activa, THE Chess_Portal SHALL mostrar un header para cada jugador
2. THE header SHALL incluir el nombre completo del jugador en formato "Nombre Apellido"
3. THE header SHALL incluir el ELO nacional del jugador después del nombre separado por " · "
4. WHEN el jugador tiene un país asociado, THE header SHALL mostrar la bandera emoji del país después del ELO separado por " · "
5. THE Chess_Portal SHALL obtener el emoji de bandera usando el código ISO del país
6. THE header SHALL usar tipografía similar al estilo de Lichess
7. WHEN el jugador no tiene ELO registrado, THE header SHALL mostrar "Sin rating" en lugar del número

### Requirement 8: Realtime Event Notifications

**User Story:** Como jugador en una partida, quiero recibir notificaciones de eventos importantes, para que esté informado de lo que sucede en la partida.

#### Acceptance Criteria

1. WHEN el oponente se conecta a la partida, THE Chess_Portal SHALL mostrar un toast "Rival conectado"
2. WHEN el oponente se desconecta de la partida, THE Chess_Portal SHALL mostrar un toast "Rival desconectado"
3. WHEN el oponente permanece desconectado por 30 segundos, THE Chess_Portal SHALL mostrar un toast "Rival desconectado hace 30s"
4. WHEN el oponente realiza su primera jugada, THE Chess_Portal SHALL mostrar un toast "Primera jugada del rival"
5. THE Chess_Portal SHALL suscribirse a eventos Realtime de Supabase para detectar conexión/desconexión
6. THE toast SHALL desaparecer automáticamente después de 3 segundos
7. THE Chess_Portal SHALL mostrar máximo un toast a la vez, encolando notificaciones si es necesario

### Requirement 9: Pre-Move Support

**User Story:** Como jugador experimentado, quiero hacer pre-moves mientras espero el turno de mi oponente, para que pueda jugar más rápido.

#### Acceptance Criteria

1. THE Chess_Portal SHALL habilitar chessground.premovable.enabled=true en la configuración del tablero
2. WHEN no es el turno del jugador, THE Chess_Portal SHALL permitir seleccionar una pieza y un destino como pre-move
3. WHEN el jugador realiza un pre-move, THE chessground SHALL mostrar la jugada anticipada con estilo visual diferenciado
4. WHEN llega el turno del jugador y el pre-move es legal, THE Chess_Portal SHALL ejecutar el pre-move automáticamente
5. WHEN llega el turno del jugador y el pre-move es ilegal, THE Chess_Portal SHALL cancelar el pre-move sin ejecutarlo
6. WHEN el jugador cancela el pre-move antes de que sea su turno, THE chessground SHALL remover la visualización del pre-move

### Requirement 10: Under-Promotion Picker

**User Story:** Como jugador avanzado, quiero elegir a qué pieza promover mi peón, para que pueda hacer under-promotions cuando sea estratégicamente necesario.

#### Acceptance Criteria

1. WHEN un peón alcanza la última fila, THE Chess_Portal SHALL mostrar un modal de selección de pieza
2. THE modal SHALL mostrar 4 opciones: Reina (Q), Torre (R), Alfil (B), Caballo (N)
3. WHEN el jugador selecciona una pieza, THE Chess_Portal SHALL enviar el movimiento con la promoción elegida al servidor
4. THE modal SHALL tener Reina como opción por defecto visualmente destacada
5. WHEN el jugador hace click fuera del modal, THE modal SHALL permanecer abierto hasta que se seleccione una pieza
6. THE Chess_Portal SHALL enviar la promoción en formato UCI (ej: e7e8q, e7e8r, e7e8b, e7e8n)
7. WHEN el servidor confirma el movimiento de promoción, THE Chess_Portal SHALL actualizar el tablero con la pieza promovida

### Requirement 11: Draw Offer and Acceptance

**User Story:** Como jugador en una partida igualada, quiero ofrecer tablas a mi oponente, para que podamos acordar un empate mutuamente.

#### Acceptance Criteria

1. WHEN Chess_Portal renderiza una partida activa, THE Chess_Portal SHALL mostrar un botón "Ofrecer Tablas"
2. WHEN un jugador hace click en "Ofrecer Tablas", THE Chess_Portal SHALL enviar un evento draw.offered via Realtime
3. WHEN el oponente recibe una oferta de tablas, THE Chess_Portal SHALL mostrar un modal "Tu rival ofrece tablas" con botones Aceptar/Rechazar
4. WHEN el oponente hace click en Aceptar, THE Chess_Portal SHALL enviar draw.accepted al servidor
5. WHEN el oponente hace click en Rechazar, THE Chess_Portal SHALL enviar draw.rejected y cerrar el modal
6. WHEN MS_Game recibe draw.accepted, THE MS_Game SHALL finalizar la partida con resultado "1/2-1/2"
7. WHEN MS_Game recibe draw.rejected, THE MS_Game SHALL notificar al oferente que la oferta fue rechazada
8. THE Chess_Portal SHALL deshabilitar el botón "Ofrecer Tablas" después de enviar una oferta hasta que sea respondida
9. WHEN la partida termina en tablas por acuerdo mutuo, THE Chess_Portal SHALL mostrar modal de fin de partida con resultado "Tablas por acuerdo"

### Requirement 12: Move History Navigation

**User Story:** Como jugador revisando una partida, quiero navegar por el historial de jugadas, para que pueda analizar posiciones anteriores.

#### Acceptance Criteria

1. WHEN Chess_Portal renderiza una partida, THE Chess_Portal SHALL mostrar botones de navegación ← y →
2. WHEN el jugador hace click en ←, THE Chess_Portal SHALL retroceder una jugada en el historial
3. WHEN el jugador hace click en →, THE Chess_Portal SHALL avanzar una jugada en el historial
4. WHILE el jugador está navegando el historial, THE tablero SHALL estar en modo read-only
5. WHEN el jugador navega a una posición anterior, THE Chess_Portal SHALL actualizar el FEN del tablero a esa posición
6. WHEN el jugador está en una posición histórica, THE Chess_Portal SHALL deshabilitar los controles de juego
7. WHEN el jugador regresa a la posición actual, THE Chess_Portal SHALL habilitar los controles de juego
8. THE botón ← SHALL estar deshabilitado cuando el jugador está en la posición inicial
9. THE botón → SHALL estar deshabilitado cuando el jugador está en la posición actual

### Requirement 13: Build Command in Makefile

**User Story:** Como desarrollador, quiero un comando make build que reconstruya las imágenes Docker, para que no levante imágenes stale con make up.

#### Acceptance Criteria

1. THE Makefile SHALL incluir un target `build` que ejecuta `docker-compose build`
2. WHEN un desarrollador ejecuta `make build`, THE comando SHALL reconstruir todas las imágenes Docker definidas en docker-compose.yml
3. THE target `build` SHALL usar la flag `--no-cache` para forzar reconstrucción completa
4. THE Makefile SHALL incluir un target `up` que ejecuta `make build` antes de `docker-compose up -d`
5. WHEN un desarrollador ejecuta `make up`, THE comando SHALL garantizar que las imágenes están actualizadas antes de levantar los containers

### Requirement 14: Clean Environment Variables Example

**User Story:** Como desarrollador nuevo, quiero un archivo .env.example limpio, para que no me confunda con variables obsoletas.

#### Acceptance Criteria

1. THE archivo .env.example SHALL remover todas las variables con prefijo S3_*
2. THE archivo .env.example SHALL remover todas las variables con prefijo MINIO_*
3. THE archivo .env.example SHALL remover la variable AUTH_DB_URL
4. THE archivo .env.example SHALL mantener solo variables actualmente utilizadas por los servicios
5. THE archivo .env.example SHALL incluir comentarios explicando el propósito de cada variable
6. THE archivo .env.example SHALL agrupar variables por servicio (Supabase, PostgreSQL, RabbitMQ, Redis)

### Requirement 15: Remove Obsolete MinIO Init Script

**User Story:** Como desarrollador, quiero un repositorio limpio sin scripts obsoletos, para que no ejecute código innecesario.

#### Acceptance Criteria

1. THE archivo infrastructure/scripts/init-minio.sh SHALL ser eliminado del repositorio
2. THE archivo docker-compose.yml SHALL no referenciar init-minio.sh en ningún volumen o comando
3. WHEN un desarrollador ejecuta `make up`, THE sistema SHALL funcionar correctamente sin init-minio.sh

### Requirement 16: Flyway Validation Disabled in Development

**User Story:** Como desarrollador, quiero que MS_Game no entre en restart-loop por checksum mismatch de Flyway, para que pueda desarrollar sin interrupciones.

#### Acceptance Criteria

1. WHEN MS_Game se ejecuta con perfil `dev`, THE MS_Game SHALL configurar spring.flyway.validate-on-migrate=false
2. WHEN MS_Game se ejecuta con perfil `prod`, THE MS_Game SHALL mantener spring.flyway.validate-on-migrate=true
3. THE application-dev.yml de MS_Game SHALL incluir la configuración de Flyway para desarrollo
4. WHEN un desarrollador modifica una migración de Flyway en desarrollo, THE MS_Game SHALL aplicar la migración sin validar checksums

### Requirement 17: Live Game Service Tests

**User Story:** Como desarrollador, quiero tests automatizados del flujo live game, para que pueda verificar que el sistema funciona correctamente.

#### Acceptance Criteria

1. THE LiveGameServiceTest SHALL crear una sesión de juego con dos jugadores
2. THE LiveGameServiceTest SHALL simular que ambos jugadores se unen a la sesión
3. THE LiveGameServiceTest SHALL ejecutar 5 movimientos legales alternando turnos
4. THE LiveGameServiceTest SHALL simular que un jugador se rinde (resign)
5. THE LiveGameServiceTest SHALL invocar finishSession y verificar que retorna un PGN bien formado
6. THE PGN generado SHALL incluir headers [Event], [Site], [Date], [White], [Black], [Result]
7. THE PGN generado SHALL incluir los 5 movimientos en notación algebraica estándar
8. THE LiveGameServiceTest SHALL verificar que un movimiento ilegal retorna HTTP 400 Bad Request
9. THE LiveGameServiceTest SHALL verificar que un movimiento fuera de turno retorna HTTP 403 Forbidden
10. THE suite de tests SHALL alcanzar mínimo 60% de cobertura en LiveGameService

### Requirement 18: Demo Data Reset

**User Story:** Como presentador de la demo, quiero resetear los datos a un estado limpio, para que cada demo comience con datos consistentes.

#### Acceptance Criteria

1. THE Makefile SHALL incluir un target `demo-reset` que ejecuta un script de reset
2. WHEN un presentador ejecuta `make demo-reset`, THE script SHALL eliminar todos los usuarios de prueba de auth_db
3. WHEN el script ejecuta, THE script SHALL eliminar todas las sesiones de juego activas
4. WHEN el script ejecuta, THE script SHALL eliminar todos los torneos en estado DRAFT u OPEN
5. WHEN el script ejecuta, THE script SHALL mantener 10 jugadores chilenos seed con nombres, ratings y clubes realistas
6. THE script SHALL mantener las 30 aperturas ECO en la tabla OPENING
7. THE script SHALL ejecutar en menos de 30 segundos
8. WHEN el script completa, THE sistema SHALL estar listo para una demo limpia sin datos de pruebas anteriores

### Requirement 19: Complete Demo Dry-Run

**User Story:** Como presentador de la demo, quiero ejecutar un dry-run completo cronometrado, para que pueda identificar fricciones antes de la presentación real.

#### Acceptance Criteria

1. THE equipo SHALL ejecutar un dry-run completo de la demo con setup limpio
2. THE dry-run SHALL usar dos pestañas de navegador simulando dos usuarios diferentes
3. THE equipo SHALL cronometrar cada flujo de la demo
4. THE equipo SHALL documentar todas las fricciones encontradas en un archivo DEMO_DRYRUN.md
5. THE documentación SHALL incluir tiempo total de la demo
6. THE documentación SHALL incluir tiempo por flujo individual
7. THE documentación SHALL incluir lista de problemas encontrados con severidad (crítico/medio/bajo)
8. THE dry-run SHALL verificar que todos los flujos del PLAN_DEMO.md funcionan end-to-end

### Requirement 20: Verify Suggested Demo Flows

**User Story:** Como presentador de la demo, quiero verificar los flujos sugeridos del plan de demo, para que pueda demostrar todas las capacidades del sistema.

#### Acceptance Criteria

1. THE equipo SHALL verificar el flujo de creación y gestión de torneos end-to-end
2. THE equipo SHALL verificar el flujo de standings y actualización de ELO después de partidas
3. THE equipo SHALL verificar el flujo de búsqueda fuzzy de jugadores
4. THE equipo SHALL verificar el comportamiento del circuit breaker cuando MS_Users está lento
5. WHEN MS_Users demora más de 2 segundos, THE MS_Tournament SHALL usar fallback de ELO 1500
6. THE equipo SHALL documentar los resultados de cada verificación en DEMO_FLOWS_VERIFICATION.md
7. THE documentación SHALL incluir capturas de pantalla o logs de cada flujo exitoso
8. IF algún flujo falla, THEN THE equipo SHALL documentar el problema y crear un issue para resolverlo

