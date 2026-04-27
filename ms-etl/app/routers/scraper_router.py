"""Endpoints de scraping AJEFECH (rama de validación).

Estos endpoints sirven para validar la calidad del scraper antes de
promoverlo a fuente oficial. NO escriben en ms-users mientras
`AJEFECH_PUSH_TO_USERS != "true"`.
"""
from __future__ import annotations

from fastapi import APIRouter, HTTPException, Query

from app.services import player_enrichment
from app.sources.ajefech_scraper import AjefechScraper, AjefechScraperError

router = APIRouter()


@router.get("/players")
async def list_players(limit: int = Query(50, ge=1, le=500)):
    async with AjefechScraper() as scraper:
        try:
            entries = await scraper.list_players()
        except AjefechScraperError as e:
            raise HTTPException(status_code=502, detail=str(e))
    return [entry.model_dump() for entry in entries[:limit]]


@router.get("/player/{federation_id}")
async def get_player_ficha(federation_id: str):
    ficha, payload = await player_enrichment.enrich_by_federation_id(federation_id)
    if ficha is None:
        raise HTTPException(status_code=404, detail=f"AJEFECH player {federation_id} not found")
    return {"ficha": ficha.model_dump(mode="json"), "chessquery": payload.model_dump(mode="json")}


@router.get("/search")
async def search_player(
    firstName: str = Query(..., min_length=1),
    lastName: str = Query(..., min_length=1),
):
    ficha, payload = await player_enrichment.enrich_by_name(firstName, lastName)
    if ficha is None:
        raise HTTPException(
            status_code=404,
            detail=f"AJEFECH: no match for {firstName} {lastName}",
        )
    return {"ficha": ficha.model_dump(mode="json"), "chessquery": payload.model_dump(mode="json")}
