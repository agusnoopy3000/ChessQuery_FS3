"""Enriquecimiento de jugadores desde AJEFECH con cache last-known-good.

Estrategia de resilencia:
    1. Intenta scraper en vivo contra AJEFECH GraphQL.
    2. Si éxito → upsert al snapshot cache (etl_db.ajefech_player_snapshot).
    3. Si falla → consulta el snapshot guardado.
    4. Si tampoco hay snapshot → (None, None) y el caller decide qué mostrar.

Esto garantiza que la plataforma siempre tenga datos federados que servir
incluso si AJEFECH está caída o cambia su contrato GraphQL.
"""
from __future__ import annotations

import logging
import os
from typing import Optional

from sqlalchemy.orm import Session

from app.schemas.ajefech import AjefechPlayerFicha, ChessQueryPlayerUpdate
from app.services import ajefech_cache
from app.sources.ajefech_scraper import AjefechScraper, AjefechScraperError

logger = logging.getLogger(__name__)

PUSH_ENABLED = os.getenv("AJEFECH_PUSH_TO_USERS", "false").lower() == "true"


async def enrich_by_name(
    first_name: str, last_name: str, db: Optional[Session] = None
) -> tuple[Optional[AjefechPlayerFicha], Optional[ChessQueryPlayerUpdate]]:
    """Busca al jugador en AJEFECH y devuelve (ficha cruda, payload ChessQuery)."""
    ficha: Optional[AjefechPlayerFicha] = None
    async with AjefechScraper() as scraper:
        try:
            ficha = await scraper.search_by_name(first_name, last_name)
        except AjefechScraperError as e:
            logger.warning("AJEFECH scraper failed for %s %s: %s", first_name, last_name, e)
            ficha = None

    if ficha is None and db is not None:
        ficha = ajefech_cache.find_by_name(db, first_name, last_name)
        if ficha is not None:
            logger.info("Served AJEFECH %s %s from snapshot cache", first_name, last_name)

    if ficha is None:
        return None, None

    if db is not None:
        try:
            ajefech_cache.upsert(db, ficha)
        except Exception as e:
            logger.warning("AJEFECH cache upsert failed: %s", e)

    return ficha, ficha.to_chessquery()


async def enrich_by_federation_id(
    federation_id: str, db: Optional[Session] = None
) -> tuple[Optional[AjefechPlayerFicha], Optional[ChessQueryPlayerUpdate]]:
    ficha: Optional[AjefechPlayerFicha] = None
    async with AjefechScraper() as scraper:
        try:
            ficha = await scraper.fetch_player_ficha(federation_id)
        except AjefechScraperError as e:
            logger.warning("AJEFECH scraper failed for /player/%s: %s", federation_id, e)
            ficha = None

    if ficha is None and db is not None:
        ficha = ajefech_cache.get_by_federation_id(db, federation_id)
        if ficha is not None:
            logger.info("Served AJEFECH player %s from snapshot cache", federation_id)

    if ficha is None:
        return None, None

    if db is not None:
        try:
            ajefech_cache.upsert(db, ficha)
        except Exception as e:
            logger.warning("AJEFECH cache upsert failed: %s", e)

    return ficha, ficha.to_chessquery()
