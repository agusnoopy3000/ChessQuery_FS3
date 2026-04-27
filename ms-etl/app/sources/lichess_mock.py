import random
import logging

logger = logging.getLogger(__name__)

SHOULD_FAIL = False

# Pool de usernames Lichess. En producción esta lista vendrá del campo
# `lichess_username` de la tabla player en ms-users (los usuarios lo
# proveen en el registro). Mockeamos 20 cuentas para ejercer el flujo.
USERNAMES = [
    "magnusish_cl", "kasparovillo", "anand_jr_cl", "carlsen_fan_cl",
    "elastic_pawn", "queens_gambit_cl", "torre_negra", "alfil_blanco",
    "caballo_de_troya", "jaque_mate_cl", "rey_enroque", "dama_chilena",
    "peon_pasado", "fianchetto_cl", "zugzwang_cl", "endgame_master_cl",
    "rapid_chile", "blitz_lover_cl", "bullet_king_cl", "classical_cl",
]


class LichessMockSource:
    """Mock de la API real de Lichess (https://lichess.org/api/user/{username}).
    Devuelve ELO por modalidad: rapid, blitz, bullet, classical.
    El usuario provee su `lichessUsername` en el registro; ms-users guarda
    el campo y ms-etl lo usa para sincronizar el ELO de plataforma.
    Reemplazar por LichessRealSource (HTTP + token) post-demo.
    """

    async def extract(self) -> list[dict]:
        if SHOULD_FAIL and random.random() < 0.7:
            raise Exception("LichessMockSource: simulated failure")

        rng = random.Random(13)
        players = []
        for username in USERNAMES:
            base = rng.randint(1100, 2400)
            players.append({
                "lichessUsername": username,
                "eloPlatformRapid":     max(800, base + rng.randint(-150, 150)),
                "eloPlatformBlitz":     max(800, base + rng.randint(-200, 100)),
                "eloPlatformBullet":    max(800, base + rng.randint(-250, 50)),
                "eloPlatformClassical": max(800, base + rng.randint(-100, 200)),
                "totalGames": rng.randint(50, 4000),
                "source": "LICHESS",
            })
        return players
