from app.sources.lichess_real import map_lichess_user


def test_map_lichess_user_extracts_ratings_per_mode():
    user = {
        "id": "drnykterstein",
        "username": "DrNykterstein",
        "perfs": {
            "bullet": {"rating": 3200, "games": 1000},
            "blitz": {"rating": 2950, "games": 500},
            "rapid": {"rating": 2800, "games": 200},
            "classical": {"rating": 2700, "games": 50},
            "puzzle": {"rating": 3000, "games": 9999},  # debe ignorarse
        },
    }
    r = map_lichess_user(user)
    assert r["lichessUsername"] == "DrNykterstein"
    assert r["eloPlatformBullet"] == 3200
    assert r["eloPlatformBlitz"] == 2950
    assert r["eloPlatformRapid"] == 2800
    assert r["eloPlatformClassical"] == 2700
    assert r["source"] == "LICHESS"
    # totalGames suma solo las 4 modalidades estándar (no puzzle)
    assert r["totalGames"] == 1000 + 500 + 200 + 50


def test_map_lichess_user_handles_missing_perfs():
    r = map_lichess_user({"username": "novato"})
    assert r["lichessUsername"] == "novato"
    assert r["eloPlatformBlitz"] is None
    assert r["totalGames"] == 0
    assert r["source"] == "LICHESS"
