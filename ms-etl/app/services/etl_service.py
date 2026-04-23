import logging
import os
import json
from datetime import datetime
from sqlalchemy.orm import Session

import redis

from app.models.sync_log import EtlSyncLog
from app.services.circuit_breaker import CircuitBreaker
from app.services.rabbitmq_publisher import publish_event
from app.sources.fide_mock import FideMockSource
from app.sources.ajefech_mock import AjefechMockSource
from app.sources.chess_results_mock import ChessResultsMockSource
from app.sources.lichess_mock import LichessMockSource

logger = logging.getLogger(__name__)

REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379/0")
MS_USERS_URL = os.getenv("MS_USERS_URL", "http://localhost:8081")

# Un CircuitBreaker por fuente
circuit_breakers: dict[str, CircuitBreaker] = {
    "fide":          CircuitBreaker("fide"),
    "ajefech":       CircuitBreaker("ajefech"),
    "chess_results": CircuitBreaker("chess_results"),
    "lichess":       CircuitBreaker("lichess"),
}

sources = {
    "fide":          FideMockSource(),
    "ajefech":       AjefechMockSource(),
    "chess_results": ChessResultsMockSource(),
    "lichess":       LichessMockSource(),
}


def _get_redis():
    return redis.from_url(REDIS_URL, decode_responses=True)


async def run_sync(source: str, db: Session) -> EtlSyncLog:
    if source not in circuit_breakers:
        raise ValueError(f"Unknown source: {source}")

    cb = circuit_breakers[source]
    log = EtlSyncLog(
        source=source.upper(),
        started_at=datetime.utcnow(),
        status="RUNNING",
        cb_state=cb.get_state(),
    )
    db.add(log)
    db.commit()
    db.refresh(log)

    try:
        records = await cb.call(sources[source].extract)
        log.records_processed = len(records)
        log.records_failed = 0
        log.status = "SUCCESS"
        log.finished_at = datetime.utcnow()
        log.cb_state = cb.get_state()

        # Cachear en Redis
        try:
            r = _get_redis()
            r.setex(
                f"etl:last_sync:{source}",
                30 * 24 * 3600,
                json.dumps({
                    "timestamp": datetime.utcnow().isoformat(),
                    "records": len(records),
                    "status": "SUCCESS",
                }),
            )
        except Exception as e:
            logger.warning(f"Redis cache failed: {e}")

        # Publicar eventos
        publish_event(
            "rating.updated",
            {
                "source": source.upper(),
                "playersUpdated": len(records),
                "ratingType": "NATIONAL",
                "syncId": log.id,
            },
        )
        publish_event(
            "sync.completed",
            {
                "source": source.upper(),
                "status": "SUCCESS",
                "recordsProcessed": len(records),
                "recordsFailed": 0,
                "durationMs": int(
                    (datetime.utcnow() - log.started_at).total_seconds() * 1000
                ),
                "circuitBreakerState": cb.get_state(),
            },
        )

    except Exception as e:
        log.status = "FAILED"
        log.error_message = str(e)
        log.finished_at = datetime.utcnow()
        log.cb_state = cb.get_state()

        # Intentar leer de Redis como fallback
        try:
            r = _get_redis()
            cached = r.get(f"etl:last_sync:{source}")
            if cached:
                logger.info(f"Circuit breaker open for {source}, using cached data: {cached}")
        except Exception as redis_e:
            logger.warning(f"Redis fallback failed: {redis_e}")

        publish_event(
            "sync.completed",
            {
                "source": source.upper(),
                "status": "FAILED",
                "recordsProcessed": 0,
                "recordsFailed": 0,
                "durationMs": int(
                    (datetime.utcnow() - log.started_at).total_seconds() * 1000
                ),
                "circuitBreakerState": cb.get_state(),
            },
        )

    db.commit()
    db.refresh(log)
    return log


def get_status(db: Session) -> list[dict]:
    results = []
    for source, cb in circuit_breakers.items():
        last_log = (
            db.query(EtlSyncLog)
            .filter(EtlSyncLog.source == source.upper())
            .order_by(EtlSyncLog.id.desc())
            .first()
        )

        cached_data = None
        try:
            r = _get_redis()
            cached = r.get(f"etl:last_sync:{source}")
            if cached:
                cached_data = json.loads(cached)
        except Exception:
            pass

        results.append({
            "source": source.upper(),
            "circuitBreakerState": cb.get_state(),
            "lastSync": {
                "status": last_log.status if last_log else None,
                "startedAt": last_log.started_at.isoformat() if last_log else None,
                "finishedAt": (
                    last_log.finished_at.isoformat()
                    if last_log and last_log.finished_at
                    else None
                ),
                "recordsProcessed": last_log.records_processed if last_log else 0,
            } if last_log else None,
            "cachedData": cached_data,
        })
    return results


def get_logs(source: str | None, limit: int, db: Session) -> list[EtlSyncLog]:
    query = db.query(EtlSyncLog)
    if source:
        query = query.filter(EtlSyncLog.source == source.upper())
    return query.order_by(EtlSyncLog.id.desc()).limit(limit).all()
