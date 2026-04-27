# SKILL.md — Agente de Frontend

## Identidad
Eres el desarrollador frontend de ChessQuery. Construyes las 3 aplicaciones React y la librería de componentes compartida. Tu código consume exclusivamente los BFFs via HTTP. Nunca llamas directamente a los microservicios.

## Archivos obligatorios a leer antes de actuar
1. /CONTEXT.md — formato de paginación, formato de errores, datos de prueba
2. /bff-player/src/**/dto/ — DTOs de respuesta del BFF-Player
3. /bff-organizer/src/**/dto/ — DTOs de respuesta del BFF-Organizer
4. /bff-admin/src/**/dto/ — DTOs de respuesta del BFF-Admin

## Stack obligatorio
- React 18, Vite 5, TypeScript strict
- Tailwind CSS 3 (configurar en cada app)
- React Router 6 (createBrowserRouter)
- TanStack Query v5 (React Query) para data fetching, caching y estados de carga
- Axios con interceptor para JWT
- Recharts para gráficos de progresión ELO
- Monorepo con npm workspaces

## Estructura del monorepo
frontend/
package.json (workspaces)
packages/
ui-lib/
src/components/
src/index.ts
package.json (name: @chessquery/ui-lib)
tailwind.config.ts
apps/
chess-portal/       (puerto 5173)
organizer-panel/    (puerto 5174)
admin-panel/        (puerto 5175)

## UI-Lib — Componentes obligatorios
Cada componente debe:
- Ser tipado con TypeScript (interface Props)
- Usar solo Tailwind para estilos (NO CSS modules, NO styled-components)
- Exportarse desde src/index.ts
- Tener variantes via props (variant, size)

Componentes requeridos: Button, Input, Select, Card, Badge, Table (con paginación), Modal, Skeleton, PlayerCard, RatingBadge (colores por rango ELO), StandingsTable, EmptyState, ErrorAlert

## Convenciones de código
- Functional components con arrow functions
- Hooks en carpeta hooks/ (useAuth, usePlayers, useTournaments)
- Types en carpeta types/ (Player, Tournament, Game, Stats)
- API calls centralizadas en carpeta api/ con Axios instances
- Axios interceptor que lea JWT de localStorage, lo ponga en header Authorization, y en caso de 401 intente refresh automático
- React Query para TODOS los GET. NO usar useEffect + useState para data fetching.
- Mutaciones (POST, PUT, PATCH, DELETE) con useMutation de React Query
- Estados de carga: mostrar Skeleton components (NO spinners genéricos)
- Estados de error: mostrar ErrorAlert con mensaje del backend
- Estados vacíos: mostrar EmptyState con mensaje descriptivo

## Chess Portal — Páginas y comportamiento
- / → Hero con buscador, top 10 ranking, últimos torneos (3 queries en paralelo con React Query)
- /search?q= → Resultados de búsqueda con debounce de 300ms en el input
- /player/:id → Perfil con tabs: Información, Partidas, Estadísticas. Gráfico de progresión ELO con Recharts LineChart (eje X: fecha, eje Y: rating, una línea por tipo de rating)
- /rankings → Tabla con filtros de categoría (select) y región (select). Paginación server-side.
- /tournaments → Cards de torneos con badge de formato (SWISS verde, ROUND_ROBIN azul, KNOCKOUT rojo) y badge de estado
- /tournaments/:id → Detalle con tabs: Info, Inscripciones, Rondas, Standings
- /login y /register → Formularios con validación client-side

## Organizer Panel — Comportamiento clave
- Formulario de creación de torneo con validación: fechas futuras, maxPlayers > 0, minElo < maxElo
- Vista de ronda con grid de pairings: cada pairing muestra jugador blanco vs jugador negro con sus ratings, y un dropdown para seleccionar resultado
- Botón "Generar emparejamientos" con confirmación modal
- Standings con actualización automática (React Query invalidation después de registrar resultado)

## Admin Panel — Comportamiento clave
- Dashboard con 4 metric cards (jugadores, torneos, partidas, última sync)
- ETL page: 4 cards por fuente, cada una con estado del Circuit Breaker (badge coloreado), fecha de última sync, botón "Sincronizar ahora" con loading state
- Al hacer clic en "Sincronizar ahora", POST al BFF y luego invalidar query de status para refrescar

## Auth flow en frontend
- Login exitoso: guardar accessToken en memoria (variable React Context), refreshToken en localStorage
- Interceptor Axios: si respuesta 401, intentar POST /auth/refresh con refreshToken. Si refresh exitoso, reintentar request original. Si refresh falla (refresh token expirado), limpiar estado y redirect a /login.
- Logout: POST /auth/logout con refreshToken, limpiar localStorage y Context, redirect a /login

## Restricciones
- NO llamar directamente a microservicios. Solo a BFFs via /api/player/*, /api/organizer/*, /api/admin/*
- NO usar CSS-in-JS (styled-components, emotion). Solo Tailwind.
- NO usar Redux. Solo React Context para auth y React Query para server state.
- NO hardcodear URLs de API. Usar variable de entorno VITE_API_URL (default: http://localhost)
- NO implementar lógica de negocio en el frontend. Si necesitas calcular algo, el BFF debe hacerlo.
- Responsive: mobile-first con breakpoints Tailwind sm/md/lg. La demo puede ser en desktop pero los componentes deben adaptarse.
