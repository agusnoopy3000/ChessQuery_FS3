# @chessquery/shared

Paquete NPM de workspace con la lógica frontend transversal: tipos del
dominio, cliente HTTP y contexto de autenticación. Consumido por las apps
(`chess-portal`, `organizer-panel`) junto con `@chessquery/ui-lib`.

- **Nombre del paquete:** `@chessquery/shared`
- **Entrypoint:** `src/index.ts`
- **No se publica a registry**: se resuelve por NPM workspaces.

## Consumo

```jsonc
// apps/<app>/package.json
"dependencies": { "@chessquery/shared": "*" }
```

```tsx
import { AuthProvider, useAuth, createApiClient } from '@chessquery/shared';
import type { Player, Tournament, Pagination } from '@chessquery/shared';
```

## Qué exporta

| Módulo | Contenido |
|---|---|
| `types` | Tipos del dominio: `Player`, `Game`, `Tournament`, `Standing`, `Pagination<T>`, `ApiError`, `Role`, … |
| `api-client` | `createApiClient` / `createSupabaseApiClient` (Axios + manejo de auth y `onAuthFailure`), `TokenStorage` |
| `auth-context` | `AuthProvider` + hook `useAuth` (sesión Supabase, login/registro) |
| `auth-errors` | `translateAuthError` (mensajes de error de auth en español) |
| `supabase` | `createSupabaseClient` (cliente Supabase tipado) |

## Patrones de diseño aplicados

- **Custom Hook** — `useAuth` encapsula el estado de sesión y lo comparte
  entre componentes sin prop-drilling.
- **Provider / Context** — `AuthProvider` centraliza la auth Supabase.
- **Factory** — `createApiClient` construye instancias de Axios
  configuradas (baseURL, storage, callback de fallo de auth).

## Build

El paquete se distribuye como TypeScript fuente (`main`/`types` →
`src/index.ts`) y no tiene paso de build propio: cada app que lo consume lo
transpila con su pipeline de Vite. Verificación de tipos del monorepo:

```bash
cd frontend
npx vitest run        # la suite del monorepo cubre el código compartido
```
