"""Endpoints de scraping AJEFECH.

Estos endpoints exponen el scraper de la Federación Chilena de Ajedrez con
fallback a snapshot cache (last-known-good). Mientras
`AJEFECH_PUSH_TO_USERS != "true"` no escriben en ms-users — solo en el
cache local de etl_db.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.database import get_db
from app.services import ajefech_cache, player_enrichment
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
async def get_player_ficha(federation_id: str, db: Session = Depends(get_db)):
    ficha, payload = await player_enrichment.enrich_by_federation_id(federation_id, db)
    if ficha is None:
        raise HTTPException(status_code=404, detail=f"AJEFECH player {federation_id} not found")
    return {"ficha": ficha.model_dump(mode="json"), "chessquery": payload.model_dump(mode="json")}


@router.get("/search")
async def search_player(
    firstName: str = Query(..., min_length=1),
    lastName: str = Query(..., min_length=1),
    db: Session = Depends(get_db),
):
    ficha, payload = await player_enrichment.enrich_by_name(firstName, lastName, db)
    if ficha is None:
        raise HTTPException(
            status_code=404,
            detail=f"AJEFECH: no match for {firstName} {lastName}",
        )
    return {"ficha": ficha.model_dump(mode="json"), "chessquery": payload.model_dump(mode="json")}


@router.get("/cache/stats")
def cache_stats(db: Session = Depends(get_db)):
    return {
        "totalSnapshots": ajefech_cache.count(db),
        "recent": [
            {
                "federationId": s.federation_id,
                "firstName": s.first_name,
                "lastName": s.last_name,
                "eloNational": s.elo_national,
                "fetchedAt": s.fetched_at.isoformat() if s.fetched_at else None,
            }
            for s in ajefech_cache.list_recent(db, limit=10)
        ],
    }
