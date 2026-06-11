from app.sources.chesscom_real import map_chesscom_stats


def test_map_chesscom_stats_extracts_ratings_per_mode():
    stats = {
        "chess_bullet": {"last": {"rating": 2900}, "record": {"win": 500, "loss": 300, "draw": 50}},
        "chess_blitz": {"last": {"rating": 3100}, "record": {"win": 1000, "loss": 400, "draw": 100}},
        "chess_rapid": {"last": {"rating": 2750}, "record": {"win": 80, "loss": 30, "draw": 20}},
        "chess_daily": {"last": {"rating": 2400}, "record": {"win": 10, "loss": 5, "draw": 2}},
        "tactics": {"highest": {"rating": 3500}},  # debe ignorarse
    }
    r = map_chesscom_stats("Hikaru", stats)
    assert r["chesscomUsername"] == "Hikaru"
    assert r["eloChesscomBullet"] == 2900
    assert r["eloChesscomBlitz"] == 3100
    assert r["eloChesscomRapid"] == 2750
    assert r["eloChesscomDaily"] == 2400
    assert r["source"] == "CHESSCOM"
    # totalGames suma win+loss+draw de las 4 modalidades (no tactics)
    assert r["totalGames"] == (850 + 1500 + 130 + 17)


def test_map_chesscom_stats_handles_missing_modes():
    # Un jugador que solo juega blitz: el resto de modalidades no viene.
    stats = {"chess_blitz": {"last": {"rating": 1500}, "record": {"win": 10, "loss": 8, "draw": 1}}}
    r = map_chesscom_stats("novato_cl", stats)
    assert r["chesscomUsername"] == "novato_cl"
    assert r["eloChesscomBlitz"] == 1500
    assert r["eloChesscomBullet"] is None
    assert r["eloChesscomRapid"] is None
    assert r["eloChesscomDaily"] is None
    assert r["totalGames"] == 19


def test_map_chesscom_stats_handles_empty_stats():
    r = map_chesscom_stats("cuenta_nueva", {})
    assert r["chesscomUsername"] == "cuenta_nueva"
    assert r["eloChesscomBlitz"] is None
    assert r["totalGames"] == 0
    assert r["source"] == "CHESSCOM"
