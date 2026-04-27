"""Tests para el mapeo GraphQL → ChessQuery.

No hacen red: usan respuestas GraphQL canned (la respuesta real de
`person(id: 738)` está congelada en `tests/fixtures/person_738.graphql.json`).
"""
from __future__ import annotations

import asyncio
import json
from datetime import date
from pathlib import Path

import httpx
import pytest

from app.schemas.ajefech import AjefechPlayerFicha
from app.sources.ajefech_scraper import AjefechScraper, _best_match
from app.schemas.ajefech import AjefechListingEntry

FIXTURES = Path(__file__).parent / "fixtures"


def _fixture_json(name: str) -> dict:
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))


def test_from_graphql_maps_real_player_738():
    person = _fixture_json("person_738.graphql.json")["data"]["person"]
    ficha = AjefechPlayerFicha.from_graphql(person, "https://www.federacionchilenadeajedrez.cl/player/738")
    assert ficha.federation_id == "738"
    assert ficha.nombre == "Jorge Moises"
    assert ficha.apellidos == "Sepulveda Rojas"
    assert ficha.fecha_nacimiento == date(1981, 3, 24)
    assert ficha.club == "Club Deportivo Chess Viña del Mar"
    assert ficha.fide_id == "3404803"
    assert ficha.elo_internacional == 2159
    assert ficha.elo_nacional == 2053
    assert ficha.rut == "9914860-8"


def test_to_chessquery_uses_camel_case_and_marks_source():
    person = _fixture_json("person_738.graphql.json")["data"]["person"]
    payload = AjefechPlayerFicha.from_graphql(person, "https://example/player/738").to_chessquery()
    assert payload.firstName == "Jorge Moises"
    assert payload.lastName == "Sepulveda Rojas"
    assert payload.federationId == "738"
    assert payload.fideId == "3404803"
    assert payload.eloFideStandard == 2159
    assert payload.eloNational == 2053
    assert payload.rut == "9914860-8"
    assert payload.source == "AJEFECH"


def test_from_graphql_tolerates_missing_optional_fields():
    person = {"id": "999", "firstName": "Solo", "lastName": "Nombre"}
    ficha = AjefechPlayerFicha.from_graphql(person, "https://example/player/999")
    assert ficha.nombre == "Solo"
    assert ficha.apellidos == "Nombre"
    assert ficha.fecha_nacimiento is None
    assert ficha.elo_nacional is None
    assert ficha.club is None


def test_best_match_prefers_more_token_overlap():
    entries = [
        AjefechListingEntry(federation_id="1", full_name="Otro Jugador", profile_url=""),
        AjefechListingEntry(federation_id="738", full_name="Jorge Sepulveda Rojas", profile_url=""),
    ]
    match = _best_match(entries, "Jorge", "Sepulveda")
    assert match is not None and match.federation_id == "738"


def test_best_match_requires_two_tokens():
    entries = [AjefechListingEntry(federation_id="1", full_name="Jorge Otro", profile_url="")]
    assert _best_match(entries, "Jorge", "Inexistente") is None


def test_scraper_calls_graphql_endpoint(monkeypatch):
    """Smoke test del cliente: intercepta httpx y devuelve la fixture."""
    fixture = _fixture_json("person_738.graphql.json")

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/graphql"
        body = json.loads(request.content)
        assert body["variables"] == {"id": "738"}
        assert "person(id:" in body["query"].replace(" ", "")
        return httpx.Response(200, json=fixture)

    transport = httpx.MockTransport(handler)
    client = httpx.AsyncClient(
        transport=transport,
        base_url="https://www.federacionchilenadeajedrez.cl",
        headers={"User-Agent": "test"},
    )

    async def run():
        async with AjefechScraper(client=client) as scraper:
            ficha = await scraper.fetch_player_ficha("738")
        assert ficha is not None
        assert ficha.fide_id == "3404803"
        assert ficha.elo_nacional == 2053

    asyncio.run(run())
