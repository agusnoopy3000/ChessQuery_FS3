import random
import logging

logger = logging.getLogger(__name__)

SHOULD_FAIL = False


def _generate_rut(i: int) -> str:
    base = 10000000 + i * 314159 % 9000000
    dv_map = "0123456789K"
    s = 2
    m = base
    while m > 0:
        s = (s + m % 10 * [2, 3, 4, 5, 6, 7][((base - m // 10) % 6)]) % 11
        m //= 10
    return f"{base}-{dv_map[s % 11]}"


class AjefechMockSource:
    async def extract(self) -> list[dict]:
        if SHOULD_FAIL and random.random() < 0.7:
            raise Exception("AjefechMockSource: simulated failure")
        players = []
        for i in range(1, 31):
            players.append({
                "rut": _generate_rut(i),
                "firstName": f"Jugador{i}",
                "lastName": f"AJEFECH{i}",
                "eloNational": random.randint(800, 2200),
                "source": "AJEFECH",
            })
        return players
