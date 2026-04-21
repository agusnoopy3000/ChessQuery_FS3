from fastapi import FastAPI
from app.routers.etl_router import router as etl_router
from app.database import engine, Base
from app.models.sync_log import EtlSyncLog  # noqa: F401 - ensure table created

Base.metadata.create_all(bind=engine)

app = FastAPI(title="MS-ETL ChessQuery", version="1.0.0")
app.include_router(etl_router, prefix="/etl", tags=["ETL"])


@app.get("/health")
def health():
    return {"status": "UP", "service": "ms-etl"}
