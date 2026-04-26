"""Enriquecimiento de jugadores desde AJEFECH.

Flujo objetivo (una vez validado el scraper):

    Usuario se registra en ChessQuery con (firstName, lastName)
        -> ms-etl.search_by_name(firstName, lastName)
        -> ficha AJEFECH normalizada al modelo player
        -> PUT/PATCH a ms-users con los campos descubiertos.

En esta rama el último paso (push a ms-users) está apagado por feature flag
para poder validar la fase de extracción contra producción de la federación
sin contaminar la BD de jugadores.
"""
from __future__ import annotations

import logging
import os
from typing import Optional

from app.schemas.ajefech import AjefechPlayerFicha, ChessQueryPlayerUpdate
from app.sources.ajefech_scraper import AjefechScraper, AjefechScraperError

logger = logging.getLogger(__name__)

PUSH_ENABLED = os.getenv("AJEFECH_PUSH_TO_USERS", "false").lower() == "true"


async def enrich_by_name(
    first_name: str, last_name: str
) -> tuple[Optional[AjefechPlayerFicha], Optional[ChessQueryPlayerUpdate]]:
    """Busca al jugador en AJEFECH y devuelve (ficha cruda, payload ChessQuery).

    Devuelve (None, None) si no hay match.
    """
    async with AjefechScraper() as scraper:
        try:
            ficha = await scraper.search_by_name(first_name, last_name)
        except AjefechScraperError as e:
            logger.warning("AJEFECH scraper failed for %s %s: %s", first_name, last_name, e)
            return None, None

    if ficha is None:
        return None, None

    return ficha, ficha.to_chessquery()


async def enrich_by_federation_id(
    federation_id: str,
) -> tuple[Optional[AjefechPlayerFicha], Optional[ChessQueryPlayerUpdate]]:
    async with AjefechScraper() as scraper:
        try:
            ficha = await scraper.fetch_player_ficha(federation_id)
        except AjefechScraperError as e:
            logger.warning("AJEFECH scraper failed for /player/%s: %s", federation_id, e)
            return None, None
    return ficha, ficha.to_chessquery()
