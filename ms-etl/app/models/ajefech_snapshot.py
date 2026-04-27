"""Snapshot last-known-good de fichas AJEFECH.

Cada fetch exitoso del scraper se persiste aquí. Si AJEFECH cae, el servicio
de enriquecimiento lee de esta tabla en lugar de fallar — garantiza que la
plataforma siempre tenga datos federados que mostrar.
"""
from __future__ import annotations

from datetime import datetime, timezone

from sqlalchemy import Column, Date, DateTime, Integer, String, Text
from sqlalchemy.dialects.postgresql import JSONB

from app.database import Base


def _utcnow():
    return datetime.now(timezone.utc)


class AjefechPlayerSnapshot(Base):
    __tablename__ = "ajefech_player_snapshot"

    federation_id = Column(String(20), primary_key=True)
    rut = Column(String(15), index=True, nullable=True)
    fide_id = Column(String(20), index=True, nullable=True)
    first_name = Column(String(100), nullable=True)
    last_name = Column(String(150), nullable=True)
    birth_date = Column(Date, nullable=True)
    club_name = Column(String(200), nullable=True)
    elo_national = Column(Integer, nullable=True)
    elo_fide_std = Column(Integer, nullable=True)
    raw_payload = Column(JSONB, nullable=False)
    fetched_at = Column(DateTime(timezone=True), nullable=False, default=_utcnow)
    source_url = Column(Text, nullable=True)
