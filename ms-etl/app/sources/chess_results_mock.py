import random
import logging

logger = logging.getLogger(__name__)

SHOULD_FAIL = False


class ChessResultsMockSource:
    async def extract(self) -> list[dict]:
        if SHOULD_FAIL and random.random() < 0.7:
            raise Exception("ChessResultsMockSource: simulated failure")
        tournaments = []
        for i in range(1, 6):
            tournaments.append({
                "tournamentName": f"Torneo Mock {i}",
                "location": "Santiago, CHL",
                "players": random.randint(8, 32),
                "rounds": random.randint(5, 9),
                "source": "CHESS_RESULTS",
            })
        return tournaments
