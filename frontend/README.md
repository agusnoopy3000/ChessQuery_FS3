# ChessQuery Frontend

Monorepo NPM (workspaces) que contiene 3 aplicaciones React + Vite y
2 paquetes compartidos.

## Estructura

```
frontend/
├── apps/
│   ├── chess-portal/       (port 5173) — portal del jugador
│   ├── organizer-panel/    (port 5174) — panel del organizador
│   └── admin-panel/        (port 5175) — panel del administrador
└── packages/
    ├── ui-lib/             — @chessquery/ui-lib (componentes UI)
    └── shared/             — @chessquery/shared (tipos + utilidades)
```

## Componente NPM compartido — `@chessquery/ui-lib`

Librería de componentes consumida por las 3 apps vía workspace.

```jsonc
// apps/*/package.json
"dependencies": {
  "@chessquery/ui-lib": "*",
  "@chessquery/shared": "*"
}
```

## Setup

```bash
cd frontend
npm install                  # instala todos los workspaces
npm run dev:portal           # http://localhost:5173
npm run dev:organizer        # http://localhost:5174
npm run dev:admin            # http://localhost:5175
```

## Build de producción

```bash
npm run build                # build de ui-lib + las 3 apps
```

## Stack

- React 18, Vite 5, TypeScript 5.3
- React Router 6, TanStack Query 5
- Recharts 2 (gráficos de rating)
- Axios para clientes HTTP a los BFFs

## Variables de entorno (`.env` en cada app)

| Var | Default |
|---|---|
| `VITE_API_BASE_URL` | `http://localhost:3001` (player) / 3002 / 3003 |
