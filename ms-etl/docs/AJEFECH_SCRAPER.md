# AJEFECH Scraper (rama `feat/ajefech-scraper`)

Cliente para la API GraphQL pública de la Federación Chilena de Ajedrez
(`https://www.federacionchilenadeajedrez.cl/graphql`). Reemplaza al
`ajefech_mock` existente. Aislado en su propia rama hasta validarse.

## Por qué GraphQL y no scraping HTML

El sitio de la federación es una SPA Vue 3 + Apollo. Las páginas
`/jugadores/` y `/player/{id}` solo entregan un shell HTML; los datos se
renderizan client-side desde `main.js` que llama a `POST /graphql`. Ese
endpoint es **público, sin auth, con introspección abierta**. Usarlo es:

- **~30× más rápido** que renderizar la SPA con Playwright (1s vs ~30s + bundle).
- **Estable**: contrato GraphQL tipado vs DOM que cambia con cada release de UI.
- **Liviano**: sin Chromium en el contenedor.
- **Cortés** con la federación: una sola POST pequeña, no fuerza renderizar JS.

## Queries usadas

| GraphQL | Uso |
|---|---|
| `person(id: String!)` → `PersonType` | Ficha individual (datos de un jugador). |
| `players(firstName, lastName, lastNameSecond, identificator)` | Listado / búsqueda server-side. |

Schema completo descubrible vía introspección (ver `app/sources/ajefech_scraper.py`).

## Mapeo `PersonType` → modelo ChessQuery (`ms-users.player`)

| AJEFECH (`PersonType`) | ChessQuery (camelCase) | Columna `player` |
|---|---|---|
| `firstName` + `secondName` | `firstName` | `first_name` |
| `lastName` + `lastNameSecond` | `lastName` | `last_name` |
| `birthdayFormated` (`dd/mm/yyyy`) | `birthDate` | `birth_date` |
| `clubBasic.name` | `clubName` | `club_id` (resolver por nombre) |
| `fideIdentificator` | `fideId` | `fide_id` |
| `id` (interno AJEFECH) | `federationId` | `federation_id` |
| `identificatorFormat` (RUT con guion) | `rut` | `rut` |
| `eloInter` | `eloFideStandard` | `elo_fide_standard` |
| `eloNat` | `eloNational` | `elo_national` |

## Flujo del registro (objetivo)

```
register(firstName, lastName)
   └─► AjefechScraper.search_by_name
         ├─► POST /graphql players(firstName,lastName)   # listado/búsqueda
         ├─► _best_match (token overlap normalizado)     # desambigua duplicados
         └─► POST /graphql person(id)                    # ficha completa
   └─► AjefechPlayerFicha.to_chessquery()
   └─► PUT ms-users (apagado por defecto, ver flag)
```

## Endpoints del servicio

| Endpoint | Descripción |
|---|---|
| `GET /etl/scrape/ajefech/players?limit=N` | Listado completo (sin filtros). |
| `GET /etl/scrape/ajefech/player/{federation_id}` | Ficha por id AJEFECH. |
| `GET /etl/scrape/ajefech/search?firstName=...&lastName=...` | Flujo registro. |

Las respuestas devuelven `{ ficha, chessquery }`.

## CLI

```
PYTHONPATH=. python3 -m scripts.scrape_player_sample --id 738
PYTHONPATH=. python3 -m scripts.scrape_player_sample --first "Jorge" --last "Sepulveda Rojas"
```

Salida en stdout = JSON con `ficha` + `chessquery`.
Ver `docs/ajefech_player_sample.json` (capturado del live).

## Variables de entorno

| Variable | Default | Descripción |
|---|---|---|
| `AJEFECH_BASE_URL` | `https://www.federacionchilenadeajedrez.cl` | Mockear en E2E. |
| `AJEFECH_TIMEOUT_SECONDS` | `30` | Timeout HTTP. |
| `AJEFECH_USER_AGENT` | `ChessQuery-ETL/1.0 (+contacto: ...)` | Identifícate. |
| `AJEFECH_PUSH_TO_USERS` | `false` | Si es `true`, propaga la ficha a ms-users. Mantener apagado hasta validar. |

## Validación antes de promover a `main`

1. Probar el CLI contra IDs reales (`738`, `25769`) y revisar el JSON.
2. Probar la búsqueda con jugadores que tengan apellido común
   (caso `Jorge Sepulveda`: hay varios; `_best_match` toma el de mayor
   solapamiento de tokens — si no basta, agregar `secondName`/`lastNameSecond`
   como filtro adicional al GraphQL).
3. Activar `AJEFECH_PUSH_TO_USERS=true` y mergear.

## Datos confirmados (capturados 2026-04-26)

```
/player/738    → Jorge Moises Sepulveda Rojas, RUT ,
                 FIDE 3404803, ELO Nat 2053, ELO Inter 2159,
                 Club Deportivo Chess Viña del Mar
/player/25769  → Agustin Nicolas Garrido Castro, RUT ,
                 FIDE 3478432, ELO Nat 1341, ELO Inter 1586
```
