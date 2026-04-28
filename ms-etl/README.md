# ms-etl — ETL de Federaciones

Servicio Python (FastAPI) que sincroniza datos federados desde fuentes
externas:

- **AJEFECH** — Federación Ajedrez Chile (scraping GraphQL)
- **Lichess API** — ratings online

- **Puerto:** 8086
- **DB:** `etl_db` (PostgreSQL :5438)
- **Stack:** Python 3.11 + FastAPI + SQLAlchemy + Pika (RabbitMQ)

## Endpoints

| Método | Path | Descripción |
|---|---|---|
| GET  | `/etl/sources` | Estado de las fuentes (AJEFECH, LICHESS) |
| POST | `/etl/sources/{name}/sync` | Disparar sincronización manual |
| GET  | `/etl/runs` | Histórico de ejecuciones (logs) |
| GET  | `/etl/runs/{id}` | Detalle de una ejecución |

## Ciclo de vida de un run

```
PENDING → RUNNING → SUCCESS | FAILED
```

Cada run registra `started_at`, `finished_at`, `records_processed`,
`error_message`.

## Eventos

Publica `rating.updated` por cada jugador enriquecido y `sync.completed`
al final del run.

## Build & Run

```bash
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8086 --reload
```

## Docker

```bash
docker build -t ms-etl:latest .
```

## Tests

```bash
pytest tests/
```
