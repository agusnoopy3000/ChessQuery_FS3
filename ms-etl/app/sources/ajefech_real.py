"""Fuente real AJEFECH para sync masivo.

Itera un rango de federation_ids (la API GraphQL `players` sin filtros
devuelve vacío, pero `person(id)` funciona para ids 1..30000+ secuenciales),
fetcha la ficha detallada de cada uno con throttling y persiste en el
snapshot cache.

Patrones:
- Reusa AjefechScraper (httpx async) como cliente.
- Throttling: máx N requests concurrentes contra AJEFECH.
- Tolerancia parcial: si una ficha falla, se cuenta en records_failed pero
  no aborta el sync entero.
- Cache: cada ficha exitosa se upsert al snapshot table.
- Rango configurable vía AJEFECH_SYNC_ID_START / AJEFECH_SYNC_ID_END /
  AJEFECH_SYNC_ID_STEP para controlar el alcance del sync por entorno.
"""
from __future__ import annotations

import asyncio
import logging
import os
from typing import Optional

from sqlalchemy.orm import Session

from app.database import SessionLocal
from app.services import ajefech_cache
from app.sources.ajefech_scraper import AjefechScraper, AjefechScraperError

logger = logging.getLogger(__name__)

DEFAULT_CONCURRENCY = int(os.getenv("AJEFECH_SYNC_CONCURRENCY", "5"))
INTER_BATCH_SLEEP_SECS = float(os.getenv("AJEFECH_SYNC_SLEEP_SECS", "0.05"))

# Rango de federation_ids a barrer en cada sync. Por defecto un sample
# representativo: 200 ids esparcidos entre 1 y 30000 (step 150).
DEFAULT_ID_START = int(os.getenv("AJEFECH_SYNC_ID_START", "1"))
DEFAULT_ID_END = int(os.getenv("AJEFECH_SYNC_ID_END", "30000"))
DEFAULT_ID_STEP = int(os.getenv("AJEFECH_SYNC_ID_STEP", "150"))


class AjefechRealSource:
    """Sync masivo: barre rango de IDs → fetcha fichas → upsert al cache."""

    def __init__(
        self,
        concurrency: int = DEFAULT_CONCURRENCY,
        id_start: int = DEFAULT_ID_START,
        id_end: int = DEFAULT_ID_END,
        id_step: int = DEFAULT_ID_STEP,
    ) -> None:
        self.concurrency = max(1, concurrency)
        self.id_start = max(1, id_start)
        self.id_end = max(self.id_start + 1, id_end)
        self.id_step = max(1, id_step)

    def _ids(self) -> list[int]:
        return list(range(self.id_start, self.id_end + 1, self.id_step))

    async def extract(self) -> list[dict]:
        """Devuelve la lista de payloads ChessQueryPlayerUpdate (camelCase)."""
        db: Session = SessionLocal()
        try:
            return await self._run(db)
        finally:
            db.close()

    async def _run(self, db: Session) -> list[dict]:
        ids = self._ids()
        logger.info(
            "AJEFECH sync: barriendo %d ids (rango %d-%d step %d, conc=%d)",
            len(ids),
            self.id_start,
            self.id_end,
            self.id_step,
            self.concurrency,
        )

        async with AjefechScraper() as scraper:
            sem = asyncio.Semaphore(self.concurrency)
            results: list[dict] = []
            failed = 0
            not_found = 0

            async def fetch_one(fid: int) -> Optional[dict]:
                nonlocal failed, not_found
                async with sem:
                    try:
                        ficha = await scraper.fetch_player_ficha(str(fid))
                    except AjefechScraperError as e:
                        logger.warning("AJEFECH /player/%s scraper error: %s", fid, e)
                        failed += 1
                        return None
                    await asyncio.sleep(INTER_BATCH_SLEEP_SECS)
                    if ficha is None:
                        not_found += 1
                        return None
                    try:
                        ajefech_cache.upsert(db, ficha)
                    except Exception as e:
                        logger.warning(
                            "Cache upsert failed for %s: %s", ficha.federation_id, e
                        )
                        failed += 1
                        return None
                    return ficha.to_chessquery().model_dump(mode="json")

            payloads = await asyncio.gather(*[fetch_one(fid) for fid in ids])

            for payload in payloads:
                if payload is not None:
                    results.append(payload)

            logger.info(
                "AJEFECH sync done: ok=%d failed=%d not_found=%d total_input=%d",
                len(results),
                failed,
                not_found,
                len(ids),
            )
            return results
