"""Cliente para la API GraphQL pública de AJEFECH.

La Federación Chilena de Ajedrez expone su SPA contra
`POST https://www.federacionchilenadeajedrez.cl/graphql`. La API es pública
(sin auth) y tiene introspección abierta. Usarla es muchísimo más estable
que parsear HTML, y respetuoso con la federación (no renderizamos JS ni
golpeamos el front).

Queries usadas:
  - `person(id: String!)`           → ficha completa del jugador.
  - `players(firstName, lastName)`  → listado/búsqueda (server-side).

Si el contrato GraphQL cambiara, este módulo es el único que hay que tocar.
"""
from __future__ import annotations

import logging
import os
from typing import Optional

import httpx

from app.schemas.ajefech import AjefechListingEntry, AjefechPlayerFicha

logger = logging.getLogger(__name__)

DEFAULT_BASE_URL = os.getenv("AJEFECH_BASE_URL", "https://www.federacionchilenadeajedrez.cl")
DEFAULT_TIMEOUT = float(os.getenv("AJEFECH_TIMEOUT_SECONDS", "30"))
USER_AGENT = os.getenv(
    "AJEFECH_USER_AGENT",
    "ChessQuery-ETL/1.0 (+contacto: agucastro.656@gmail.com)",
)

PERSON_QUERY = """
query Person($id: String!) {
  person(id: $id) {
    id
    firstName secondName lastName lastNameSecond fullName
    birthdayFormated
    identificator identificatorFormat
    fideIdentificator federationId
    eloNat eloInter
    clubBasic { id name }
  }
}
""".strip()

# La query `players` acepta firstName/lastName/lastNameSecond/identificator (todos opcionales).
PLAYERS_QUERY = """
query Players($firstName: String, $lastName: String, $lastNameSecond: String, $identificator: String) {
  players(firstName: $firstName, lastName: $lastName, lastNameSecond: $lastNameSecond, identificator: $identificator) {
    id firstName secondName lastName lastNameSecond fullName
    identificator fideIdentificator eloNat
    clubBasic { id name }
  }
}
""".strip()


class AjefechScraperError(RuntimeError):
    pass


class AjefechScraper:
    def __init__(
        self,
        base_url: str = DEFAULT_BASE_URL,
        timeout: float = DEFAULT_TIMEOUT,
        client: Optional[httpx.AsyncClient] = None,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self._timeout = timeout
        self._client = client
        self._owns_client = client is None

    async def __aenter__(self) -> "AjefechScraper":
        if self._client is None:
            self._client = httpx.AsyncClient(
                base_url=self.base_url,
                timeout=self._timeout,
                headers={
                    "User-Agent": USER_AGENT,
                    "Accept": "application/json",
                    "Content-Type": "application/json",
                },
                follow_redirects=True,
            )
        return self

    async def __aexit__(self, *_exc) -> None:
        if self._owns_client and self._client is not None:
            await self._client.aclose()
            self._client = None

    async def _gql(self, query: str, variables: dict) -> dict:
        assert self._client is not None, "use AjefechScraper como async context manager"
        try:
            response = await self._client.post("/graphql", json={"query": query, "variables": variables})
        except httpx.HTTPError as e:
            raise AjefechScraperError(f"network error calling AJEFECH GraphQL: {e}") from e
        if response.status_code >= 400:
            raise AjefechScraperError(f"HTTP {response.status_code} from AJEFECH GraphQL")
        body = response.json()
        if body.get("errors"):
            raise AjefechScraperError(f"GraphQL errors: {body['errors']}")
        return body.get("data") or {}

    def _profile_url(self, federation_id: str) -> str:
        return f"{self.base_url}/player/{federation_id}"

    async def fetch_player_ficha(self, federation_id: str) -> Optional[AjefechPlayerFicha]:
        data = await self._gql(PERSON_QUERY, {"id": str(federation_id)})
        person = data.get("person")
        if not person:
            return None
        return AjefechPlayerFicha.from_graphql(person, self._profile_url(str(person["id"])))

    async def list_players(
        self,
        first_name: Optional[str] = None,
        last_name: Optional[str] = None,
        last_name_second: Optional[str] = None,
        identificator: Optional[str] = None,
    ) -> list[AjefechListingEntry]:
        """Listado/búsqueda server-side.

        Sin filtros devuelve la lista completa (puede ser pesada). Con
        filtros AJEFECH hace match por prefijo case-insensitive.
        """
        data = await self._gql(
            PLAYERS_QUERY,
            {
                "firstName": first_name,
                "lastName": last_name,
                "lastNameSecond": last_name_second,
                "identificator": identificator,
            },
        )
        rows = data.get("players") or []
        entries: list[AjefechListingEntry] = []
        for row in rows:
            try:
                elo_nat = int(row["eloNat"]) if row.get("eloNat") not in (None, "") else None
            except ValueError:
                elo_nat = None
            entries.append(
                AjefechListingEntry(
                    federation_id=str(row["id"]),
                    full_name=row.get("fullName") or f"player-{row['id']}",
                    profile_url=self._profile_url(str(row["id"])),
                    fide_id=row.get("fideIdentificator") or None,
                    identificator=row.get("identificator") or None,
                    elo_national=elo_nat,
                )
            )
        return entries

    async def search_by_name(
        self, first_name: str, last_name: str
    ) -> Optional[AjefechPlayerFicha]:
        """Flujo principal del registro: nombre+apellido → ficha completa.

        Estrategia: AJEFECH hace match por prefijo en el campo. Mandamos
        `firstName` y `lastName` y, si hay varios, elegimos el match cuya
        concatenación coincida mejor token-a-token con la entrada del usuario.
        """
        candidates = await self.list_players(first_name=first_name, last_name=last_name)
        match = _best_match(candidates, first_name, last_name)
        if match is None:
            logger.info("AJEFECH: no match for '%s %s'", first_name, last_name)
            return None
        logger.info("AJEFECH: matched '%s' -> id=%s", match.full_name, match.federation_id)
        return await self.fetch_player_ficha(match.federation_id)


def _normalize(text: str) -> set[str]:
    import re
    import unicodedata

    text = "".join(c for c in unicodedata.normalize("NFKD", text or "") if not unicodedata.combining(c))
    return {t for t in re.split(r"\s+", text.lower().strip()) if t}


def _best_match(
    entries: list[AjefechListingEntry], first_name: str, last_name: str
) -> Optional[AjefechListingEntry]:
    needle = _normalize(f"{first_name} {last_name}")
    if not needle:
        return None
    best: Optional[AjefechListingEntry] = None
    best_score = 0
    for entry in entries:
        score = len(needle & _normalize(entry.full_name))
        if score > best_score:
            best, best_score = entry, score
    # Exigimos al menos 2 tokens (un nombre + un apellido) para evitar
    # matches espurios.
    return best if best_score >= 2 else None
