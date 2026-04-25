import random
import logging
from datetime import date, timedelta

logger = logging.getLogger(__name__)

SHOULD_FAIL = False

FIRST_NAMES = [
    "Rodrigo", "Camila", "Felipe", "Valentina", "Diego", "Javiera",
    "Sebastián", "Antonia", "Matías", "Catalina", "Ignacio", "Fernanda",
    "Cristóbal", "Daniela", "Joaquín", "Florencia", "Vicente", "Constanza",
]

LAST_NAMES = [
    "González", "Muñoz", "Rojas", "Díaz", "Pérez", "Soto", "Contreras",
    "Silva", "Martínez", "Sepúlveda", "Morales", "Rodríguez", "Fuentes",
    "Espinoza", "Castillo", "Tapia", "Reyes", "Herrera", "Núñez", "Vargas",
]

TITLES = [None, None, None, None, "CM", "FM", "IM", "GM", "WCM", "WFM", "WIM", "WGM"]


def _random_birth_date(rng: random.Random) -> str:
    today = date.today()
    age_days = rng.randint(10 * 365, 70 * 365)
    return (today - timedelta(days=age_days)).isoformat()


class FideMockSource:
    """Mock del listado FIDE filtrado por federación CHI. La API real
    de FIDE no está abierta al público; los datos vienen de scraping
    o exports CSV. Se mockea aquí respetando el contrato de eventos
    rating.updated (rating_history) y title_history en ms-users.
    """

    async def extract(self) -> list[dict]:
        if SHOULD_FAIL and random.random() < 0.7:
            raise Exception("FideMockSource: simulated failure for circuit breaker test")

        rng = random.Random(7)
        players = []
        for i in range(1, 51):
            base = rng.randint(1400, 2700)
            players.append({
                "fideId": f"32{i:05d}",
                "firstName": rng.choice(FIRST_NAMES),
                "lastName": f"{rng.choice(LAST_NAMES)} {rng.choice(LAST_NAMES)}",
                "birthDate": _random_birth_date(rng),
                "federation": "CHI",
                "title": rng.choice(TITLES),
                "eloFideStandard": base,
                "eloFideRapid": max(1000, base + rng.randint(-80, 40)),
                "eloFideBlitz": max(1000, base + rng.randint(-100, 30)),
                "source": "FIDE",
            })
        return players
