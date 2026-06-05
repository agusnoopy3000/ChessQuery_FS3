"""Fuente real de Lichess.

Trae el rating por modalidad (bullet/blitz/rapid/classical) de los jugadores
que tienen `lichessUsername`, usando la API pública de Lichess (sin OAuth):

    POST https://lichess.org/api/users   (cuerpo: usernames separados por coma,
                                           hasta 300 por request)

Devuelve registros con el mismo shape que `LichessMockSource`, para que el
publicador de `rating.updated` y el consumidor en ms-users no cambien.

Origen de usernames (en orden):
    1. env `LICHESS_USERNAMES` (coma-separados) — útil para pruebas/acotar.
    2. endpoint de ms-users `GET /users/lichess-usernames` (los players que
       proveyeron su usuario de Lichess en el registro).
"""
from __future__ import annotations

import logging
import os

import httpx

logger = logging.getLogger(__name__)

LICHESS_API_BASE = os.getenv("LICHESS_API_BASE", "https://lichess.org")
MS_USERS_URL = os.getenv("MS_USERS_URL", "http://localhost:8081")
_BATCH = 300  # máximo de usernames por request en POST /api/users


def map_lichess_user(u: dict) -> dict:
    """Mapea un objeto user de la API de Lichess al registro de ChessQuery.

    `u` tiene forma {id, username, perfs: {bullet:{rating,games}, blitz:{...}, ...}}.
    """
    perfs = u.get("perfs") or {}

    def rating(mode: str):
        p = perfs.get(mode) or {}
        return p.get("rating")

    total = 0
    for mode in ("bullet", "blitz", "rapid", "classical"):
        total += int((perfs.get(mode) or {}).get("games", 0) or 0)

    return {
        "lichessUsername": u.get("username") or u.get("id"),
        "eloLichessRapid": rating("rapid"),
        "eloLichessBlitz": rating("blitz"),
        "eloLichessBullet": rating("bullet"),
        "eloLichessClassical": rating("classical"),
        "totalGames": total,
        "source": "LICHESS",
    }


def _chunks(items: list[str], n: int):
    for i in range(0, len(items), n):
        yield items[i : i + n]


class LichessRealSource:
    async def extract(self) -> list[dict]:
        usernames = self._usernames()
        if not usernames:
            logger.warning("LichessRealSource: sin usernames para sincronizar")
            return []

        records: list[dict] = []
        async with httpx.AsyncClient(timeout=20.0) as client:
            for batch in _chunks(usernames, _BATCH):
                resp = await client.post(
                    f"{LICHESS_API_BASE}/api/users",
                    content=",".join(batch),
                    headers={"Content-Type": "text/plain"},
                )
                resp.raise_for_status()
                for u in resp.json():
                    records.append(map_lichess_user(u))
        logger.info("LichessRealSource: %d jugadores sincronizados", len(records))
        return records

    def _usernames(self) -> list[str]:
        env = os.getenv("LICHESS_USERNAMES", "").strip()
        if env:
            return [u.strip() for u in env.split(",") if u.strip()]
        # Fallback: pedir a ms-users los lichessUsername registrados.
        try:
            with httpx.Client(timeout=10.0) as c:
                resp = c.get(f"{MS_USERS_URL}/users/lichess-usernames")
                if resp.status_code == 200:
                    return [u for u in resp.json() if u]
                logger.warning("ms-users /users/lichess-usernames → %s", resp.status_code)
        except Exception as e:  # noqa: BLE001
            logger.warning("No se pudieron obtener usernames de ms-users: %s", e)
        return []
