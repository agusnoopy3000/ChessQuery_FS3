# ChessQuery Frontend

Monorepo NPM (workspaces) que contiene **2 aplicaciones** React + Vite y
**2 paquetes compartidos**.

## Estructura

```
frontend/
├── apps/
│   ├── chess-portal/       (port 5173) — portal del jugador
│   └── organizer-panel/    (port 5174) — panel del organizador
└── packages/
    ├── ui-lib/             — @chessquery/ui-lib (componentes UI, ver su README)
    └── shared/             — @chessquery/shared (tipos, API client, auth)
```

> El componente NPM evaluado en la rúbrica es `packages/ui-lib`
> (`@chessquery/ui-lib`). Su instalación, API y pruebas están documentadas
> en [`packages/ui-lib/README.md`](./packages/ui-lib/README.md).

## Componentes NPM compartidos

Consumidos por las apps vía NPM workspaces (no se publican a un registry;
se resuelven por symlink dentro del monorepo):

```jsonc
// apps/*/package.json
"dependencies": {
  "@chessquery/ui-lib": "*",
  "@chessquery/shared": "*"
}
```

| Paquete | Contenido |
|---|---|
| `@chessquery/ui-lib` | Componentes React (Button, Card, ChessBoard, StandingsTable, …) |
| `@chessquery/shared` | Tipos, `api-client`, contexto de auth Supabase |

## Setup

```bash
cd frontend
npm install                  # instala todos los workspaces
npm run dev:portal           # http://localhost:5173
npm run dev:organizer        # http://localhost:5174
```

## Build de producción

```bash
npm run build                # build de @chessquery/ui-lib + ambas apps
npm run build:portal         # solo chess-portal
npm run build:organizer      # solo organizer-panel
```

## Pruebas

Suite con **Vitest** + Testing Library (configurada a nivel de monorepo):

```bash
cd frontend
npx vitest run                       # ejecuta toda la suite
npx vitest run --coverage            # con reporte de cobertura (v8)
```

## Stack

- React 18, Vite 5, TypeScript 5.3
- React Router 6, TanStack Query 5
- Recharts 2 (gráficos de rating)
- Axios para clientes HTTP a los BFFs
- Vitest 4 + @testing-library/react para pruebas

## Variables de entorno (`.env` en cada app)

| Var | Default |
|---|---|
| `VITE_API_BASE_URL` | `http://localhost:3001` (player) / `3002` (organizer) |

Cada app incluye un `.env.example`; copiarlo a `.env` antes de `npm run dev`.
