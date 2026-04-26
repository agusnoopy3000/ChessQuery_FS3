"""Pydantic schemas para el scraper AJEFECH.

Modelan la respuesta de la API GraphQL pública de la Federación Chilena de
Ajedrez (`POST https://www.federacionchilenadeajedrez.cl/graphql`,
query `person(id)` → `PersonType`) y su mapeo al modelo interno de ChessQuery
(tabla `ms-users.player`).
"""
from __future__ import annotations

from datetime import date, datetime
from typing import Optional

from pydantic import BaseModel, ConfigDict, Field


class AjefechListingEntry(BaseModel):
    """Fila del listado público (query `players`)."""

    federation_id: str = Field(..., description="ID interno AJEFECH (PersonType.id).")
    full_name: str
    profile_url: str
    fide_id: Optional[str] = None
    identificator: Optional[str] = None  # RUT sin formato
    elo_national: Optional[int] = None


class AjefechPlayerFicha(BaseModel):
    """Ficha completa del jugador devuelta por `person(id)`.

    Conserva la nomenclatura de la federación (campo a campo) para que la
    auditoría sea trivial; el mapeo a ChessQuery se hace en `to_chessquery`.
    """

    model_config = ConfigDict(populate_by_name=True)

    federation_id: str = Field(..., description="PersonType.id — id interno AJEFECH.")
    profile_url: str
    nombre: Optional[str] = None
    apellidos: Optional[str] = None
    fecha_nacimiento: Optional[date] = None
    club: Optional[str] = None
    fide_id: Optional[str] = Field(None, description="PersonType.fideIdentificator.")
    elo_internacional: Optional[int] = None
    elo_nacional: Optional[int] = None
    rut: Optional[str] = Field(None, description="RUT chileno con formato 12345678-9.")

    @staticmethod
    def _parse_birthday(value: Optional[str]) -> Optional[date]:
        if not value:
            return None
        for fmt in ("%d/%m/%Y", "%Y-%m-%d", "%d-%m-%Y"):
            try:
                return datetime.strptime(value, fmt).date()
            except ValueError:
                continue
        return None

    @staticmethod
    def _parse_int(value) -> Optional[int]:
        if value in (None, ""):
            return None
        try:
            return int(str(value).strip())
        except ValueError:
            return None

    @classmethod
    def from_graphql(cls, person: dict, profile_url: str) -> "AjefechPlayerFicha":
        """Construye la ficha desde la respuesta GraphQL `person`."""
        first = (person.get("firstName") or "").strip()
        second = (person.get("secondName") or "").strip()
        last1 = (person.get("lastName") or "").strip()
        last2 = (person.get("lastNameSecond") or "").strip()
        nombre = " ".join(filter(None, [first, second])) or None
        apellidos = " ".join(filter(None, [last1, last2])) or None
        club = (person.get("clubBasic") or {}).get("name") if person.get("clubBasic") else None

        return cls(
            federation_id=str(person["id"]),
            profile_url=profile_url,
            nombre=nombre,
            apellidos=apellidos,
            fecha_nacimiento=cls._parse_birthday(person.get("birthdayFormated")),
            club=club,
            fide_id=person.get("fideIdentificator") or None,
            elo_internacional=cls._parse_int(person.get("eloInter")),
            elo_nacional=cls._parse_int(person.get("eloNat")),
            rut=person.get("identificatorFormat") or None,
        )

    def to_chessquery(self) -> "ChessQueryPlayerUpdate":
        return ChessQueryPlayerUpdate(
            firstName=self.nombre,
            lastName=self.apellidos,
            birthDate=self.fecha_nacimiento,
            clubName=self.club,
            fideId=self.fide_id,
            federationId=self.federation_id,
            rut=self.rut,
            eloFideStandard=self.elo_internacional,
            eloNational=self.elo_nacional,
            source="AJEFECH",
            sourceUrl=self.profile_url,
        )


class ChessQueryPlayerUpdate(BaseModel):
    """Payload normalizado al modelo de ms-users.player (camelCase)."""

    firstName: Optional[str] = None
    lastName: Optional[str] = None
    birthDate: Optional[date] = None
    clubName: Optional[str] = None
    fideId: Optional[str] = None
    federationId: Optional[str] = None
    rut: Optional[str] = None
    eloFideStandard: Optional[int] = None
    eloNational: Optional[int] = None
    source: str = "AJEFECH"
    sourceUrl: Optional[str] = None
