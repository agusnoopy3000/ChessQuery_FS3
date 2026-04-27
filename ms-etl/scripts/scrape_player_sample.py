"""CLI para volcar la ficha de un jugador AJEFECH a JSON.

Uso:
    python -m scripts.scrape_player_sample --id 738
    python -m scripts.scrape_player_sample --first "Pablo" --last "Salinas Herrera"

Sirve para validar el scraper contra producción antes de promoverlo a
fuente oficial. La salida (stdout) es un JSON con dos secciones:
  - "ficha": atributos crudos tal como aparecen en la federación.
  - "chessquery": payload normalizado al modelo de ms-users.player.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import sys

from app.services import player_enrichment


async def _main() -> int:
    parser = argparse.ArgumentParser(description="AJEFECH player scraper")
    parser.add_argument("--id", dest="federation_id", help="ID AJEFECH (path /player/{id})")
    parser.add_argument("--first", dest="first_name", help="Nombre")
    parser.add_argument("--last", dest="last_name", help="Apellidos")
    args = parser.parse_args()

    if args.federation_id:
        ficha, payload = await player_enrichment.enrich_by_federation_id(args.federation_id)
    elif args.first_name and args.last_name:
        ficha, payload = await player_enrichment.enrich_by_name(args.first_name, args.last_name)
    else:
        parser.error("Se requiere --id o (--first y --last)")
        return 2

    if ficha is None:
        print(json.dumps({"error": "not_found"}), file=sys.stderr)
        return 1

    output = {
        "ficha": ficha.model_dump(mode="json"),
        "chessquery": payload.model_dump(mode="json"),
    }
    print(json.dumps(output, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(_main()))
