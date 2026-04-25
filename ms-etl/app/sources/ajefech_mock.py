import random
import logging
from datetime import date, timedelta

logger = logging.getLogger(__name__)

SHOULD_FAIL = False

FIRST_NAMES = [
    "Rodrigo", "Camila", "Felipe", "Valentina", "Diego", "Javiera",
    "Sebastián", "Antonia", "Matías", "Catalina", "Ignacio", "Fernanda",
    "Cristóbal", "Daniela", "Joaquín", "Florencia", "Vicente", "Constanza",
    "Tomás", "Isidora", "Benjamín", "Martina", "Agustín", "Trinidad",
    "Lucas", "Sofía", "Maximiliano", "Emilia", "Nicolás", "Amanda",
]

LAST_NAMES = [
    "González", "Muñoz", "Rojas", "Díaz", "Pérez", "Soto", "Contreras",
    "Silva", "Martínez", "Sepúlveda", "Morales", "Rodríguez", "Fuentes",
    "Espinoza", "Castillo", "Tapia", "Reyes", "Herrera", "Núñez", "Vargas",
    "Carrasco", "Araya", "Flores", "Castro", "Torres", "Vásquez", "Riquelme",
    "Mora", "Cortés", "Garrido",
]

CLUBS = [
    "Club de Ajedrez Lasker",
    "Club de Ajedrez Capablanca",
    "Club de Ajedrez Universidad de Chile",
    "Club de Ajedrez Torre",
    "Club de Ajedrez Valparaíso",
    "Club de Ajedrez Concepción",
    "Club de Ajedrez Antofagasta",
    "Club de Ajedrez Rancagua",
    "Club de Ajedrez La Serena",
]


def _generate_rut(i: int) -> str:
    base = 10_000_000 + i * 314_159 % 9_000_000
    digits = [int(c) for c in str(base)]
    factors = [2, 3, 4, 5, 6, 7, 2, 3]
    s = sum(d * factors[idx % len(factors)] for idx, d in enumerate(reversed(digits)))
    rem = 11 - (s % 11)
    dv = "0" if rem == 11 else "K" if rem == 10 else str(rem)
    return f"{base}-{dv}"


def _random_birth_date(rng: random.Random) -> str:
    today = date.today()
    age_days = rng.randint(8 * 365, 65 * 365)
    return (today - timedelta(days=age_days)).isoformat()


class AjefechMockSource:
    """Mock de la AJEFECH. La AJEFECH no expone API; los datos de su
    sitio web (nombres, apellidos, fecha de nacimiento, club, ELO nacional
    y ELO FIDE) se mockean aquí respetando el modelo de Player en ms-users.
    Post-demo se reemplaza por un scraper real (ms-scraper-ajefech).
    """

    async def extract(self) -> list[dict]:
        if SHOULD_FAIL and random.random() < 0.7:
            raise Exception("AjefechMockSource: simulated failure")

        rng = random.Random(42)
        players = []
        for i in range(1, 31):
            elo_nat = rng.randint(900, 2300)
            elo_fide = max(0, elo_nat + rng.randint(-150, 80)) if elo_nat >= 1400 else 0
            players.append({
                "rut": _generate_rut(i),
                "firstName": rng.choice(FIRST_NAMES),
                "lastName": f"{rng.choice(LAST_NAMES)} {rng.choice(LAST_NAMES)}",
                "birthDate": _random_birth_date(rng),
                "clubName": rng.choice(CLUBS),
                "eloNational": elo_nat,
                "eloFideStandard": elo_fide if elo_fide >= 1400 else None,
                "source": "AJEFECH",
            })
        return players
