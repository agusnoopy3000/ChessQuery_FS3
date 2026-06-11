"""Fuente real de Chess.com.

Trae el rating por modalidad (bullet/blitz/rapid/daily) de los jugadores que
tienen `chesscomUsername`, usando la API pública de datos de Chess.com
(sin OAuth ni API key):

    GET https://api.chess.com/pub/player/{username}/stats

Documentación oficial: https://www.chess.com/news/view/published-data-api
Notas de esa documentación que esta implementación respeta:
  - No hay endpoint batch: es un request por username (serial, no paralelo,
    para respetar el rate limit de requests concurrentes de Chess.com).
  - Hay que enviar un User-Agent identificable con contacto; sin él,
    Cloudflare puede responder 403.
  - 404 = el username no existe → se omite (no aborta el run completo).

Devuelve registros con el mismo shape-pattern que `LichessRealSource`, para
que el publicador de `rating.updated` y el consumidor en ms-users sigan el
mismo contrato (match por username, campos elo por modalidad).

Origen de usernames (en orden):
    1. env `CHESSCOM_USERNAMES` (coma-separados) — útil para pruebas/acotar.
    2. endpoint de ms-users `GET /users/chesscom-usernames` (los players que
       vincularon su usuario de Chess.com en su perfil).
"""
from __future__ import annotations

import logging
import os

import httpx

logger = logging.getLogger(__name__)

CHESSCOM_API_BASE = os.getenv("CHESSCOM_API_BASE", "https://api.chess.com")
MS_USERS_URL = os.getenv("MS_USERS_URL", "http://localhost:8081")
# La doc oficial pide un User-Agent identificable con un contacto.
USER_AGENT = os.getenv(
    "CHESSCOM_USER_AGENT",
    "ChessQuery-ETL/1.0 (proyecto academico; contact: chessquery.fs3@gmail.com)",
)

# Modalidades de la API pública → sufijo del campo ChessQuery.
_MODES = {
    "chess_bullet": "Bullet",
    "chess_blitz": "Blitz",
    "chess_rapid": "Rapid",
    "chess_daily": "Daily",
}


def map_chesscom_stats(username: str, stats: dict) -> dict:
    """Mapea la respuesta de /pub/player/{username}/stats al registro ChessQuery.

    `stats` tiene forma {chess_blitz: {last: {rating}, record: {win, loss, draw}}, ...}.
    El rating vigente es `last.rating`; `record` suma los juegos por modalidad.
    """
    record: dict = {"chesscomUsername": username, "source": "CHESSCOM"}
    total = 0
    for api_mode, suffix in _MODES.items():
        mode = stats.get(api_mode) or {}
        last = mode.get("last") or {}
        record[f"eloChesscom{suffix}"] = last.get("rating")
        wld = mode.get("record") or {}
        for k in ("win", "loss", "draw"):
            total += int(wld.get(k, 0) or 0)
    record["totalGames"] = total
    return record


class ChesscomRealSource:
    async def extract(self) -> list[dict]:
        usernames = self._usernames()
        if not usernames:
            logger.warning("ChesscomRealSource: sin usernames para sincronizar")
            return []

        records: list[dict] = []
        headers = {"User-Agent": USER_AGENT, "Accept": "application/json"}
        async with httpx.AsyncClient(timeout=20.0, headers=headers) as client:
            for username in usernames:
                # La API exige el username en minúsculas en la URL.
                url = f"{CHESSCOM_API_BASE}/pub/player/{username.strip().lower()}/stats"
                resp = await client.get(url)
                if resp.status_code == 404:
                    logger.warning("ChesscomRealSource: username '%s' no existe en Chess.com", username)
                    continue
                resp.raise_for_status()
                records.append(map_chesscom_stats(username.strip(), resp.json()))
        logger.info("ChesscomRealSource: %d jugadores sincronizados", len(records))
        return records

    def _usernames(self) -> list[str]:
        env = os.getenv("CHESSCOM_USERNAMES", "").strip()
        if env:
            return [u.strip() for u in env.split(",") if u.strip()]
        # Fallback: pedir a ms-users los chesscomUsername registrados.
        try:
            with httpx.Client(timeout=10.0) as c:
                resp = c.get(f"{MS_USERS_URL}/users/chesscom-usernames")
                if resp.status_code == 200:
                    return [u for u in resp.json() if u]
                logger.warning("ms-users /users/chesscom-usernames → %s", resp.status_code)
        except Exception as e:  # noqa: BLE001
            logger.warning("No se pudieron obtener usernames de ms-users: %s", e)
        return []
