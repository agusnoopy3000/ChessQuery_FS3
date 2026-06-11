import random
import logging

logger = logging.getLogger(__name__)

SHOULD_FAIL = False

# Pool de usernames Chess.com. En producción esta lista vendrá del campo
# `chesscom_username` de la tabla player en ms-users (los usuarios lo
# vinculan desde su perfil). Mockeamos 20 cuentas para ejercer el flujo.
USERNAMES = [
    "hikaru_fan_cl", "gothamfan_chile", "danya_student", "pragg_cl",
    "torre_blanca_cl", "gambito_dama", "el_peoncito", "alfil_oscuro",
    "caballito_cl", "mate_pastor", "enroque_largo", "reina_andina",
    "peon_doblado", "fianchetto_rey", "tiempo_perdido", "final_de_torres",
    "rapido_santiago", "blitz_valpo", "bullet_conce", "daily_chile",
]


class ChesscomMockSource:
    """Mock de la API real de Chess.com (api.chess.com/pub/player/{u}/stats).
    Devuelve ELO por modalidad: bullet, blitz, rapid, daily.
    El usuario vincula su `chesscomUsername` en el perfil; ms-users guarda
    el campo y ms-etl lo usa para sincronizar los ratings.
    Con CHESSCOM_USE_MOCK=false se usa ChesscomRealSource (API pública).
    """

    async def extract(self) -> list[dict]:
        if SHOULD_FAIL and random.random() < 0.7:
            raise Exception("ChesscomMockSource: simulated failure")

        rng = random.Random(17)
        players = []
        for username in USERNAMES:
            base = rng.randint(1000, 2300)
            players.append({
                "chesscomUsername": username,
                "eloChesscomBullet": max(700, base + rng.randint(-250, 50)),
                "eloChesscomBlitz":  max(700, base + rng.randint(-200, 100)),
                "eloChesscomRapid":  max(700, base + rng.randint(-150, 150)),
                "eloChesscomDaily":  max(700, base + rng.randint(-100, 200)),
                "totalGames": rng.randint(50, 4000),
                "source": "CHESSCOM",
            })
        return players
