# Requirements Document: Supabase Studio Integration

## Introduction

ChessQuery es una plataforma de microservicios para ajedrez competitivo en Chile que utiliza Supabase para autenticación y almacenamiento de archivos PGN. Actualmente, Supabase Studio está corriendo localmente en `http://127.0.0.1:54323` y proporciona una interfaz web para gestionar usuarios, autenticación, storage, triggers, funciones y logs.

Esta feature documenta y mejora la integración con Supabase Studio para facilitar la gestión operativa de la plataforma, permitiendo a administradores y desarrolladores:
- Gestionar usuarios y roles desde la interfaz web
- Monitorear archivos PGN subidos al storage
- Inspeccionar triggers y funciones de base de datos
- Revisar logs de autenticación y base de datos
- Ejecutar queries SQL ad-hoc para debugging

El objetivo es establecer workflows claros para operaciones comunes y documentar las capacidades disponibles en Studio que complementan la arquitectura de microservicios existente.

## Glossary

- **Studio**: Supabase Studio, la interfaz web de administración de Supabase accesible en `http://127.0.0.1:54323`
- **ChessQuery_Platform**: El sistema completo de microservicios para ajedrez competitivo
- **Auth_Module**: El módulo de autenticación de Supabase que gestiona usuarios, sesiones y JWT
- **Storage_Module**: El módulo de almacenamiento de Supabase que gestiona el bucket `chessquery-pgn`
- **API_Gateway**: El gateway de Spring Cloud que valida JWT y enruta requests (puerto 8080)
- **MS_Users**: Microservicio que gestiona jugadores y crea registros Player tras eventos de registro
- **User_Profile**: Tabla `public.user_profiles` que almacena el rol (PLAYER/ORGANIZER/ADMIN) asociado a cada usuario de Supabase
- **PGN_File**: Archivo de notación de partida de ajedrez almacenado en formato `.pgn`
- **Service_Role_Key**: Clave de API con privilegios administrativos para operaciones backend
- **Anon_Key**: Clave de API pública para operaciones desde el frontend
- **RLS**: Row Level Security, sistema de políticas de seguridad a nivel de fila en PostgreSQL
- **Webhook**: Endpoint HTTP que Supabase llama cuando ocurren eventos (ej: `user-registered`)
- **Trigger**: Función de base de datos que se ejecuta automáticamente ante eventos (ej: `on_auth_user_created`)

## Requirements

### Requirement 1: Acceso a Supabase Studio Local

**User Story:** Como desarrollador o administrador, quiero acceder a Supabase Studio desde mi navegador, para gestionar la plataforma sin necesidad de herramientas CLI o queries SQL manuales.

#### Acceptance Criteria

1. WHEN Supabase está corriendo localmente, THE Studio SHALL ser accesible en `http://127.0.0.1:54323`
2. THE Studio SHALL autenticar automáticamente usando la service_role key sin requerir credenciales manuales
3. THE Studio SHALL mostrar el dashboard principal con acceso a las secciones: Authentication, Table Editor, Storage, Database, SQL Editor, Logs Explorer
4. WHEN el usuario accede a Studio por primera vez, THE ChessQuery_Platform SHALL proporcionar documentación de las URLs y credenciales necesarias

### Requirement 2: Gestión de Usuarios desde Studio

**User Story:** Como administrador, quiero gestionar usuarios de ChessQuery desde Studio, para crear usuarios de prueba, modificar roles y resetear contraseñas sin escribir código.

#### Acceptance Criteria

1. WHEN el administrador navega a Authentication → Users, THE Studio SHALL mostrar la lista completa de usuarios registrados con sus emails, fechas de creación y estado
2. THE Studio SHALL permitir crear usuarios manualmente especificando email, password y metadata (role)
3. WHEN el administrador selecciona un usuario, THE Studio SHALL permitir editar el campo `user_metadata.role` con valores PLAYER, ORGANIZER o ADMIN
4. THE Studio SHALL permitir resetear la contraseña de un usuario generando un link de recuperación
5. WHEN el administrador crea o modifica un usuario, THE Auth_Module SHALL disparar los triggers correspondientes (`on_auth_user_created`, `on_auth_user_registered_webhook`)
6. THE Studio SHALL permitir eliminar usuarios (soft delete) marcándolos como inactivos

### Requirement 3: Inspección de User Profiles

**User Story:** Como desarrollador, quiero inspeccionar la tabla `user_profiles` desde Studio, para verificar que los roles se están asignando correctamente y diagnosticar problemas de permisos.

#### Acceptance Criteria

1. WHEN el desarrollador navega a Table Editor → public.user_profiles, THE Studio SHALL mostrar todos los registros con columnas: id (UUID), role, created_at
2. THE Studio SHALL permitir filtrar registros por role (PLAYER, ORGANIZER, ADMIN)
3. THE Studio SHALL permitir editar el campo role directamente desde la interfaz
4. WHEN el desarrollador modifica un role, THE Studio SHALL validar que el valor sea uno de los permitidos (PLAYER, ORGANIZER, ADMIN)
5. THE Studio SHALL mostrar la relación FK entre `user_profiles.id` y `auth.users.id`

### Requirement 4: Monitoreo de Storage de PGN

**User Story:** Como administrador, quiero ver los archivos PGN subidos al storage desde Studio, para verificar que las partidas se están almacenando correctamente y diagnosticar problemas de upload.

#### Acceptance Criteria

1. WHEN el administrador navega a Storage → chessquery-pgn, THE Studio SHALL mostrar la estructura de carpetas `games/{year}/{month}/`
2. THE Studio SHALL listar todos los archivos `.pgn` con sus nombres, tamaños y fechas de creación
3. THE Studio SHALL permitir descargar cualquier archivo PGN haciendo clic en él
4. THE Studio SHALL permitir eliminar archivos PGN individualmente
5. THE Studio SHALL mostrar el tamaño total del bucket y el número de archivos
6. WHEN el administrador selecciona un archivo, THE Studio SHALL mostrar la URL pública y permitir copiarla al portapapeles

### Requirement 5: Inspección de Triggers y Funciones

**User Story:** Como desarrollador, quiero inspeccionar los triggers y funciones de base de datos desde Studio, para entender el flujo de eventos y diagnosticar problemas en el webhook de registro.

#### Acceptance Criteria

1. WHEN el desarrollador navega a Database → Triggers, THE Studio SHALL mostrar los triggers: `on_auth_user_created`, `on_auth_user_registered_webhook`
2. THE Studio SHALL mostrar para cada trigger: tabla asociada, evento (INSERT/UPDATE/DELETE), función ejecutada, y estado (enabled/disabled)
3. WHEN el desarrollador navega a Database → Functions, THE Studio SHALL mostrar las funciones: `handle_new_user`, `notify_user_registered`
4. THE Studio SHALL permitir ver el código SQL completo de cada función
5. THE Studio SHALL permitir ejecutar funciones manualmente con parámetros de prueba
6. WHEN el desarrollador modifica una función, THE Studio SHALL validar la sintaxis SQL antes de guardar

### Requirement 6: Configuración de Webhooks

**User Story:** Como desarrollador, quiero configurar y monitorear webhooks desde Studio, para asegurar que los eventos de registro se envían correctamente al API Gateway.

#### Acceptance Criteria

1. WHEN el desarrollador navega a Database → Webhooks, THE Studio SHALL mostrar el webhook configurado para `auth.users` con destino `POST {API_GATEWAY}/webhook/user-registered`
2. THE Studio SHALL mostrar el estado del webhook (active/inactive) y el número de invocaciones exitosas y fallidas
3. THE Studio SHALL permitir editar la URL del webhook y el secret de autenticación
4. THE Studio SHALL permitir probar el webhook manualmente enviando un payload de ejemplo
5. WHEN el webhook falla, THE Studio SHALL mostrar el código de error HTTP y el mensaje de respuesta
6. THE Studio SHALL permitir ver el historial de invocaciones del webhook con timestamps y payloads

### Requirement 7: Exploración de Logs

**User Story:** Como desarrollador, quiero explorar logs de autenticación y base de datos desde Studio, para diagnosticar errores de login, problemas de triggers y queries lentas.

#### Acceptance Criteria

1. WHEN el desarrollador navega a Logs Explorer → Auth, THE Studio SHALL mostrar logs de eventos de autenticación: login, signup, logout, token refresh
2. THE Studio SHALL permitir filtrar logs por tipo de evento, usuario (email), y rango de fechas
3. WHEN el desarrollador navega a Logs Explorer → Postgres, THE Studio SHALL mostrar logs de queries SQL ejecutadas, errores de base de datos y ejecuciones de triggers
4. THE Studio SHALL permitir buscar logs por texto libre (ej: email de usuario, mensaje de error)
5. THE Studio SHALL mostrar para cada log: timestamp, nivel (info/warning/error), mensaje, y metadata adicional
6. THE Studio SHALL permitir exportar logs filtrados en formato JSON o CSV

### Requirement 8: Ejecución de Queries SQL Ad-Hoc

**User Story:** Como desarrollador, quiero ejecutar queries SQL desde Studio, para inspeccionar datos, diagnosticar problemas y realizar operaciones de mantenimiento sin conectarme directamente a PostgreSQL.

#### Acceptance Criteria

1. WHEN el desarrollador navega a SQL Editor, THE Studio SHALL proporcionar un editor de texto con syntax highlighting para SQL
2. THE Studio SHALL permitir ejecutar queries SELECT, INSERT, UPDATE, DELETE contra cualquier tabla accesible
3. WHEN el desarrollador ejecuta un query, THE Studio SHALL mostrar los resultados en formato tabular con paginación
4. THE Studio SHALL mostrar el tiempo de ejecución del query y el número de filas afectadas
5. THE Studio SHALL permitir guardar queries frecuentes como "snippets" con nombres descriptivos
6. THE Studio SHALL proporcionar autocompletado de nombres de tablas y columnas
7. WHEN el desarrollador ejecuta un query con error, THE Studio SHALL mostrar el mensaje de error de PostgreSQL con el número de línea

### Requirement 9: Creación de Usuarios Demo

**User Story:** Como desarrollador, quiero crear usuarios demo desde Studio, para probar el flujo completo de registro → webhook → RabbitMQ → MS-Users → Player sin usar el frontend.

#### Acceptance Criteria

1. THE ChessQuery_Platform SHALL proporcionar documentación con scripts SQL para crear usuarios demo: `alice@chessquery.test` (PLAYER), `bob@chessquery.test` (ORGANIZER)
2. WHEN el desarrollador ejecuta el script de creación en SQL Editor, THE Auth_Module SHALL crear el usuario en `auth.users`
3. THE Auth_Module SHALL disparar el trigger `on_auth_user_created` que crea el registro en `user_profiles`
4. THE Auth_Module SHALL disparar el webhook que envía el evento a `POST /webhook/user-registered` en API_Gateway
5. THE API_Gateway SHALL publicar el evento `user.registered` en RabbitMQ
6. THE MS_Users SHALL consumir el evento y crear el registro Player correspondiente
7. THE Studio SHALL permitir verificar cada paso del flujo: usuario en auth.users, profile en user_profiles, logs del webhook, y finalmente el Player en user_db (via query SQL)

### Requirement 10: Documentación de Workflows Operativos

**User Story:** Como administrador, quiero documentación clara de workflows operativos usando Studio, para realizar tareas comunes sin necesidad de soporte técnico.

#### Acceptance Criteria

1. THE ChessQuery_Platform SHALL proporcionar documentación de cómo crear un usuario manualmente desde Studio con pasos específicos
2. THE ChessQuery_Platform SHALL proporcionar documentación de cómo cambiar el rol de un usuario existente
3. THE ChessQuery_Platform SHALL proporcionar documentación de cómo verificar que un archivo PGN se subió correctamente
4. THE ChessQuery_Platform SHALL proporcionar documentación de cómo diagnosticar por qué un usuario no puede hacer login usando Logs Explorer
5. THE ChessQuery_Platform SHALL proporcionar documentación de cómo verificar que el webhook de registro está funcionando
6. THE ChessQuery_Platform SHALL proporcionar documentación de cómo limpiar usuarios de prueba y sus datos asociados
7. THE ChessQuery_Platform SHALL proporcionar documentación de cómo conectar Studio Cloud (supabase.com) si se necesita acceso remoto

### Requirement 11: Gestión de Políticas RLS

**User Story:** Como desarrollador, quiero inspeccionar y modificar políticas RLS desde Studio, para ajustar permisos de acceso a user_profiles y storage sin editar archivos de migración.

#### Acceptance Criteria

1. WHEN el desarrollador navega a Table Editor → user_profiles → Policies, THE Studio SHALL mostrar todas las políticas RLS configuradas
2. THE Studio SHALL mostrar para cada política: nombre, operación (SELECT/INSERT/UPDATE/DELETE), roles aplicables, y expresión SQL
3. THE Studio SHALL permitir crear nuevas políticas usando un formulario con campos: nombre, operación, roles, y expresión USING/WITH CHECK
4. THE Studio SHALL permitir editar políticas existentes modificando la expresión SQL
5. THE Studio SHALL permitir habilitar/deshabilitar políticas temporalmente sin eliminarlas
6. WHEN el desarrollador navega a Storage → chessquery-pgn → Policies, THE Studio SHALL mostrar las políticas de acceso al bucket
7. THE Studio SHALL permitir modificar políticas de storage para controlar quién puede subir, leer o eliminar archivos PGN

### Requirement 12: Monitoreo de Sesiones Activas

**User Story:** Como administrador, quiero ver las sesiones activas de usuarios desde Studio, para monitorear actividad en tiempo real y detectar sesiones sospechosas.

#### Acceptance Criteria

1. WHEN el administrador navega a Authentication → Users → [usuario específico], THE Studio SHALL mostrar todas las sesiones activas del usuario
2. THE Studio SHALL mostrar para cada sesión: fecha de inicio, última actividad, dirección IP, user agent, y estado (active/expired)
3. THE Studio SHALL permitir revocar sesiones individuales forzando logout del usuario
4. THE Studio SHALL permitir revocar todas las sesiones de un usuario simultáneamente
5. WHEN el administrador revoca una sesión, THE Auth_Module SHALL invalidar el refresh token correspondiente
6. THE Studio SHALL mostrar el número total de sesiones activas en la plataforma

### Requirement 13: Backup y Restauración desde Studio

**User Story:** Como administrador, quiero realizar backups de la base de datos de autenticación desde Studio, para proteger datos de usuarios antes de operaciones riesgosas.

#### Acceptance Criteria

1. WHEN el administrador navega a Database → Backups, THE Studio SHALL mostrar la lista de backups automáticos con fechas y tamaños
2. THE Studio SHALL permitir crear un backup manual bajo demanda
3. THE Studio SHALL permitir descargar backups en formato SQL dump
4. THE Studio SHALL mostrar el estado de cada backup (completed/in_progress/failed)
5. WHEN el administrador selecciona un backup, THE Studio SHALL permitir restaurarlo con confirmación explícita
6. THE Studio SHALL advertir que la restauración sobrescribirá datos actuales y requerirá confirmación adicional

### Requirement 14: Integración con Variables de Entorno

**User Story:** Como desarrollador, quiero que la documentación incluya las variables de entorno necesarias para conectar servicios a Supabase, para configurar correctamente el API Gateway y microservicios.

#### Acceptance Criteria

1. THE ChessQuery_Platform SHALL documentar la variable `SUPABASE_URL` con el valor `http://host.docker.internal:54321` para servicios en Docker
2. THE ChessQuery_Platform SHALL documentar la variable `SUPABASE_JWT_SECRET` obtenida de `supabase status`
3. THE ChessQuery_Platform SHALL documentar la variable `SUPABASE_SERVICE_KEY` para operaciones backend con privilegios administrativos
4. THE ChessQuery_Platform SHALL documentar la variable `SUPABASE_ANON_KEY` para operaciones frontend sin autenticación
5. THE ChessQuery_Platform SHALL documentar la variable `SUPABASE_WEBHOOK_SECRET` para validar requests del webhook
6. THE ChessQuery_Platform SHALL documentar cómo obtener estos valores ejecutando `supabase status` en la terminal
7. THE ChessQuery_Platform SHALL documentar en qué archivos `.env` debe configurarse cada variable (api-gateway, ms-game, frontend)

### Requirement 15: Monitoreo de Uso de Storage

**User Story:** Como administrador, quiero monitorear el uso de storage desde Studio, para detectar crecimiento anormal del bucket de PGN y planificar capacidad.

#### Acceptance Criteria

1. WHEN el administrador navega a Storage → chessquery-pgn → Usage, THE Studio SHALL mostrar el tamaño total del bucket en MB/GB
2. THE Studio SHALL mostrar el número total de archivos almacenados
3. THE Studio SHALL mostrar un gráfico de crecimiento del storage en los últimos 7 días
4. THE Studio SHALL mostrar los 10 archivos más grandes con sus tamaños y rutas
5. THE Studio SHALL permitir establecer alertas cuando el bucket supere un umbral de tamaño (ej: 1GB)
6. WHEN el bucket supera el 80% de la cuota, THE Studio SHALL mostrar una advertencia visible en el dashboard
