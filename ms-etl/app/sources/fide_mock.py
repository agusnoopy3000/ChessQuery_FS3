import random
import logging

logger = logging.getLogger(__name__)

SHOULD_FAIL = False  # Set to True to test circuit breaker


class FideMockSource:
    async def extract(self) -> list[dict]:
        if SHOULD_FAIL and random.random() < 0.7:
            raise Exception("FideMockSource: simulated failure for circuit breaker test")
        players = []
        for i in range(1, 51):
            players.append({
                "fideId": f"160{i:04d}",
                "firstName": f"Jugador{i}",
                "lastName": f"FIDE{i}",
                "eloFideStandard": random.randint(1000, 2800),
                "eloFideRapid": random.randint(1000, 2700),
                "eloFideBlitz": random.randint(1000, 2700),
                "source": "FIDE",
            })
        return players
