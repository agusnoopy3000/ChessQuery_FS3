"""Endpoints de consulta a la API pública de Lichess.

El usuario provee su `lichessUsername` en el registro; estos endpoints sirven
para que el portal muestre el ELO de plataforma por modalidad (bullet, blitz,
rapid, classical, correspondence) sin que cada microservicio tenga que hablar
directamente con lichess.org.
"""
from __future__ import annotations

import json
import logging
import os
from typing import Optional

import httpx
from fastapi import APIRouter, HTTPException, Query

logger = logging.getLogger(__name__)

LICHESS_BASE = os.getenv("LICHESS_BASE_URL", "https://lichess.org")
TIMEOUT = float(os.getenv("LICHESS_TIMEOUT_SECONDS", "10"))

router = APIRouter()


def _normalize_user(payload: dict) -> dict:
    perfs = payload.get("perfs") or {}
    ratings = []
    for variant, data in perfs.items():
        if not isinstance(data, dict):
            continue
        rating = data.get("rating")
        if rating is None:
            continue
        ratings.append({
            "variant": variant,
            "rating": rating,
            "games": data.get("games"),
            "prog": data.get("prog"),
            "rd": data.get("rd"),
        })
    profile = payload.get("profile") or {}
    counts = payload.get("count") or {}
    return {
        "username": payload.get("username") or payload.get("id"),
        "displayName": profile.get("realName") or payload.get("username"),
        "title": payload.get("title"),
        "profileUrl": payload.get("url") or f"{LICHESS_BASE}/@/{payload.get('id', '')}",
        "playTimeTotal": (payload.get("playTime") or {}).get("total"),
        "counts": {
            "all": counts.get("all"),
            "rated": counts.get("rated"),
            "win": counts.get("win"),
            "loss": counts.get("loss"),
            "draw": counts.get("draw"),
        },
        "ratings": sorted(ratings, key=lambda r: -(r.get("rating") or 0)),
        "found": True,
    }


@router.get("/users/{username}")
async def get_user(username: str):
    url = f"{LICHESS_BASE}/api/user/{username}"
    try:
        async with httpx.AsyncClient(timeout=TIMEOUT) as client:
            r = await client.get(url, headers={"User-Agent": "ChessQuery-ETL/1.0"})
    except httpx.RequestError as e:
        logger.warning("Lichess API request failed for %s: %s", username, e)
        raise HTTPException(status_code=502, detail=f"Lichess upstream error: {e}")

    if r.status_code == 404:
        return {"username": username, "found": False, "error": "Usuario no existe en Lichess"}
    if r.status_code != 200:
        raise HTTPException(status_code=r.status_code, detail=f"Lichess respondió {r.status_code}")

    return _normalize_user(r.json())


@router.get("/users/{username}/games")
async def get_recent_games(username: str, max: int = Query(10, ge=1, le=50)):
    url = f"{LICHESS_BASE}/api/games/user/{username}"
    params = {"max": max, "moves": "false", "tags": "true", "opening": "true"}
    headers = {
        "Accept": "application/x-ndjson",
        "User-Agent": "ChessQuery-ETL/1.0",
    }
    try:
        async with httpx.AsyncClient(timeout=TIMEOUT) as client:
            r = await client.get(url, params=params, headers=headers)
    except httpx.RequestError as e:
        logger.warning("Lichess games request failed for %s: %s", username, e)
        raise HTTPException(status_code=502, detail=f"Lichess upstream error: {e}")

    if r.status_code == 404:
        return []
    if r.status_code != 200:
        raise HTTPException(status_code=r.status_code, detail=f"Lichess respondió {r.status_code}")

    games = []
    for line in r.text.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            g = json.loads(line)
        except json.JSONDecodeError:
            continue
        white = g.get("players", {}).get("white", {})
        black = g.get("players", {}).get("black", {})
        games.append({
            "id": g.get("id"),
            "url": f"{LICHESS_BASE}/{g.get('id')}" if g.get("id") else None,
            "speed": g.get("speed"),
            "perf": g.get("perf"),
            "rated": g.get("rated"),
            "winner": g.get("winner"),
            "moves": g.get("moves"),
            "createdAt": g.get("createdAt"),
            "whiteName": (white.get("user") or {}).get("name"),
            "whiteRating": white.get("rating"),
            "blackName": (black.get("user") or {}).get("name"),
            "blackRating": black.get("rating"),
            "opening": (g.get("opening") or {}).get("name"),
        })
    return games
