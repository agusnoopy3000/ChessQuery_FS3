import random
import logging

logger = logging.getLogger(__name__)

SHOULD_FAIL = False


class LichessMockSource:
    async def extract(self) -> list[dict]:
        if SHOULD_FAIL and random.random() < 0.7:
            raise Exception("LichessMockSource: simulated failure")
        players = []
        for i in range(1, 21):
            players.append({
                "lichessUsername": f"player_lichess_{i}",
                "eloPlatform": random.randint(1000, 2500),
                "source": "LICHESS",
            })
        return players
