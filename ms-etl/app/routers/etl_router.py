from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from app.database import get_db
from app.services import etl_service

router = APIRouter()


@router.post("/sync/{source}", status_code=201)
async def trigger_sync(source: str, db: Session = Depends(get_db)):
    valid_sources = ["fide", "ajefech", "chess_results", "lichess"]
    if source not in valid_sources:
        raise HTTPException(status_code=400, detail=f"Invalid source. Valid: {valid_sources}")
    log = await etl_service.run_sync(source, db)
    return {
        "id": log.id,
        "source": log.source,
        "status": log.status,
        "recordsProcessed": log.records_processed,
        "recordsFailed": log.records_failed,
        "startedAt": log.started_at.isoformat() if log.started_at else None,
        "finishedAt": log.finished_at.isoformat() if log.finished_at else None,
        "circuitBreakerState": log.cb_state,
        "errorMessage": log.error_message,
    }


@router.get("/status")
def get_status(db: Session = Depends(get_db)):
    return etl_service.get_status(db)


@router.get("/logs")
def get_logs(
    source: str | None = None,
    limit: int = 20,
    db: Session = Depends(get_db),
):
    logs = etl_service.get_logs(source, limit, db)
    return [
        {
            "id": l.id,
            "source": l.source,
            "status": l.status,
            "recordsProcessed": l.records_processed,
            "recordsFailed": l.records_failed,
            "startedAt": l.started_at.isoformat() if l.started_at else None,
            "finishedAt": l.finished_at.isoformat() if l.finished_at else None,
            "circuitBreakerState": l.cb_state,
            "errorMessage": l.error_message,
        }
        for l in logs
    ]
