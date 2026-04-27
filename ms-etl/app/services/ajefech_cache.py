"""Repository del cache last-known-good de AJEFECH."""
from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from typing import Optional

from sqlalchemy import or_
from sqlalchemy.orm import Session

from app.models.ajefech_snapshot import AjefechPlayerSnapshot
from app.schemas.ajefech import AjefechPlayerFicha

logger = logging.getLogger(__name__)


def _ficha_to_row(ficha: AjefechPlayerFicha) -> dict:
    return {
        "federation_id": ficha.federation_id,
        "rut": ficha.rut,
        "fide_id": ficha.fide_id,
        "first_name": ficha.nombre,
        "last_name": ficha.apellidos,
        "birth_date": ficha.fecha_nacimiento,
        "club_name": ficha.club,
        "elo_national": ficha.elo_nacional,
        "elo_fide_std": ficha.elo_internacional,
        "raw_payload": json.loads(ficha.model_dump_json()),
        "fetched_at": datetime.now(timezone.utc),
        "source_url": ficha.profile_url,
    }


def _row_to_ficha(row: AjefechPlayerSnapshot) -> AjefechPlayerFicha:
    return AjefechPlayerFicha.model_validate(row.raw_payload)


def upsert(db: Session, ficha: AjefechPlayerFicha) -> AjefechPlayerSnapshot:
    data = _ficha_to_row(ficha)
    existing = db.get(AjefechPlayerSnapshot, ficha.federation_id)
    if existing is None:
        existing = AjefechPlayerSnapshot(**data)
        db.add(existing)
    else:
        for key, value in data.items():
            setattr(existing, key, value)
    db.commit()
    db.refresh(existing)
    return existing


def get_by_federation_id(db: Session, federation_id: str) -> Optional[AjefechPlayerFicha]:
    row = db.get(AjefechPlayerSnapshot, str(federation_id))
    return _row_to_ficha(row) if row else None


def find_by_name(db: Session, first_name: str, last_name: str) -> Optional[AjefechPlayerFicha]:
    """Busca por coincidencia case-insensitive en nombres y apellidos."""
    fn = (first_name or "").strip().lower()
    ln = (last_name or "").strip().lower()
    if not fn or not ln:
        return None
    rows = (
        db.query(AjefechPlayerSnapshot)
        .filter(AjefechPlayerSnapshot.first_name.ilike(f"%{fn}%"))
        .filter(AjefechPlayerSnapshot.last_name.ilike(f"%{ln}%"))
        .order_by(AjefechPlayerSnapshot.fetched_at.desc())
        .limit(5)
        .all()
    )
    if not rows:
        return None
    return _row_to_ficha(rows[0])


def count(db: Session) -> int:
    return db.query(AjefechPlayerSnapshot).count()


def list_recent(db: Session, limit: int = 50) -> list[AjefechPlayerSnapshot]:
    return (
        db.query(AjefechPlayerSnapshot)
        .order_by(AjefechPlayerSnapshot.fetched_at.desc())
        .limit(limit)
        .all()
    )
