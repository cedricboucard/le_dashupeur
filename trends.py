"""Détecteur de tendances : repère les jeux qui décollent sur Twitch, Steam et YouTube.

Principe : un relevé régulier (toutes les 3 h via collect_trends.py) enregistre, pour chaque jeu,
une mesure par source. Le calcul des tendances compare le dernier relevé à celui d'il y a 24 h
ou 7 jours (même heure de la journée, pour ne pas confondre avec le rythme jour/nuit).

Mesures enregistrées :
- twitch  : spectateurs cumulés des 100 plus gros lives du jeu (clé Twitch requise)
- steam   : joueurs connectés en direct (aucune clé)
- youtube : vues cumulées des vidéos du jeu publiées ces 7 derniers jours (clé YouTube requise,
            un relevé par jour maximum à cause du quota gratuit)
"""
import os
import re
import unicodedata
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import quote, quote_plus

import requests

from config import USER_AGENT
from db import get_conn

ENV_PATH = Path(__file__).parent / ".env"
HEADERS = {"User-Agent": USER_AGENT}
TIMEOUT = 15

TWITCH_TOP_PAGES = 2            # 2 pages de 100 = les 200 catégories les plus regardées
YOUTUBE_MAX_GAMES = 40          # 40 recherches x 100 unités = 4 000 unités/jour (quota gratuit : 10 000)
YOUTUBE_MIN_INTERVAL_H = 20     # un relevé YouTube par jour maximum
STEAM_TOP_PLAYERS_URL = "https://api.steampowered.com/ISteamChartsService/GetMostPlayedGames/v1/"
STEAM_ITEMS_URL = "https://api.steampowered.com/IStoreBrowseService/GetItems/v1/"
STEAM_PLAYERS_URL = "https://api.steampowered.com/ISteamUserStats/GetNumberOfCurrentPlayers/v1/"

# En dessous de ces seuils, une hausse n'est pas significative (passer de 2 à 10 = +400 %)
MIN_VALUES = {"twitch": 300, "steam": 500, "youtube": 20_000}
SOURCE_LABELS = {"twitch": "🟣 Twitch", "steam": "🎮 Steam", "youtube": "🔴 YouTube"}
UNITS = {"twitch": "spectateurs", "steam": "joueurs", "youtube": "vues (7 j)"}


# --- CONFIGURATION ------------------------------------------------------------

def load_env() -> dict:
    """Lit les clés du fichier .env (format CLE=valeur), sans les écraser si déjà définies."""
    values = {}
    if ENV_PATH.exists():
        for line in ENV_PATH.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, _, value = line.partition("=")
                values[key.strip()] = value.strip().strip('"').strip("'")
    return {k: os.environ.get(k) or values.get(k, "") for k in
            ("TWITCH_CLIENT_ID", "TWITCH_CLIENT_SECRET", "YOUTUBE_API_KEY")}


def active_sources(env: dict | None = None) -> dict[str, bool]:
    env = env or load_env()
    return {
        "twitch": bool(env["TWITCH_CLIENT_ID"] and env["TWITCH_CLIENT_SECRET"]),
        "steam": True,
        "youtube": bool(env["YOUTUBE_API_KEY"]),
    }


def game_key(name: str) -> str:
    """Clé commune à toutes les sources : « Counter-Strike™ 2 » -> « counter strike 2 »."""
    name = re.sub(r"[™®©]", "", name)  # sinon NFKD transforme ™ en « TM »
    text = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", " ", text).strip()


def _get(url: str, **kwargs) -> requests.Response:
    kwargs.setdefault("timeout", TIMEOUT)
    headers = {**HEADERS, **kwargs.pop("headers", {})}
    resp = requests.get(url, headers=headers, **kwargs)
    resp.raise_for_status()
    return resp


# --- COLLECTE : TWITCH --------------------------------------------------------

def collect_twitch(env: dict) -> list[dict]:
    token = requests.post(
        "https://id.twitch.tv/oauth2/token",
        params={"client_id": env["TWITCH_CLIENT_ID"], "client_secret": env["TWITCH_CLIENT_SECRET"],
                "grant_type": "client_credentials"},
        timeout=TIMEOUT,
    )
    token.raise_for_status()
    headers = {"Client-Id": env["TWITCH_CLIENT_ID"], "Authorization": f"Bearer {token.json()['access_token']}"}

    games, cursor = [], None
    for _ in range(TWITCH_TOP_PAGES):
        params = {"first": 100, **({"after": cursor} if cursor else {})}
        data = _get("https://api.twitch.tv/helix/games/top", headers=headers, params=params).json()
        # Les catégories sans igdb_id ne sont pas des jeux (Just Chatting, Music, IRL…)
        games += [g for g in data.get("data", []) if g.get("igdb_id")]
        cursor = data.get("pagination", {}).get("cursor")
        if not cursor:
            break

    def viewers(game: dict) -> int:
        data = _get("https://api.twitch.tv/helix/streams", headers=headers,
                    params={"game_id": game["id"], "first": 100, "type": "live"}).json()
        return sum(s.get("viewer_count", 0) for s in data.get("data", []))

    with ThreadPoolExecutor(max_workers=8) as pool:
        counts = list(pool.map(viewers, games))

    return [{
        "game_name": g["name"],
        "value": count,
        "url": f"https://www.twitch.tv/directory/game/{quote(g['name'])}",
        "image": g.get("box_art_url", "").replace("{width}x{height}", "144x192"),
    } for g, count in zip(games, counts)]


# --- COLLECTE : STEAM ---------------------------------------------------------

def _steam_names(appids: list[int]) -> dict[int, str]:
    names = {}
    for i in range(0, len(appids), 50):
        ids = ",".join(f'{{"appid":{a}}}' for a in appids[i:i + 50])
        data = _get(STEAM_ITEMS_URL, params={
            "input_json": f'{{"ids":[{ids}],"context":{{"language":"english","country_code":"FR"}}}}',
        }).json()
        for item in data.get("response", {}).get("store_items", []):
            if item.get("name"):
                names[item["appid"]] = item["name"]
    return names


def _steam_players(appid: int) -> int | None:
    try:
        return _get(STEAM_PLAYERS_URL, params={"appid": appid}).json()["response"].get("player_count")
    except Exception:
        return None


def collect_steam() -> list[dict]:
    """Top 100 Steam + nouveautés F2P populaires, avec les joueurs connectés en direct."""
    ranks = _get(STEAM_TOP_PLAYERS_URL).json().get("response", {}).get("ranks", [])
    appids = [r["appid"] for r in ranks]
    try:
        from radar import _steam_search  # nouveautés Free-to-Play populaires (petits jeux qui montent)
        appids += [int(i["steam_appid"]) for i in _steam_search(filter="popularnew", count=50)]
    except Exception:
        pass
    appids = list(dict.fromkeys(appids))

    names = _steam_names(appids)
    appids = [a for a in appids if a in names]
    with ThreadPoolExecutor(max_workers=8) as pool:
        players = list(pool.map(_steam_players, appids))

    return [{
        "game_name": names[a],
        "value": p,
        "url": f"https://store.steampowered.com/app/{a}/",
        "image": f"https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/{a}/header.jpg",
    } for a, p in zip(appids, players) if p is not None]


# --- COLLECTE : YOUTUBE -------------------------------------------------------

def _youtube_candidates() -> list[str]:
    """Jeux à suivre sur YouTube : ceux qui montent le plus + les plus gros, d'après Twitch et Steam."""
    trends = compute_trends(24, sources=("twitch", "steam"))
    rising = [t["name"] for t in trends if t["score"] is not None][:25]
    biggest = [r["game_name"] for r in latest_snapshot(("twitch", "steam"), limit=YOUTUBE_MAX_GAMES)]
    unique = {}
    for name in rising + biggest:  # dédoublonne « AION 2 » / « AION™ 2 »
        unique.setdefault(game_key(name), name)
    return list(unique.values())[:YOUTUBE_MAX_GAMES]


def collect_youtube(env: dict) -> list[dict]:
    key = env["YOUTUBE_API_KEY"]
    since = (datetime.now(timezone.utc) - timedelta(days=7)).strftime("%Y-%m-%dT%H:%M:%SZ")
    results = []
    for name in _youtube_candidates():
        search = _get("https://www.googleapis.com/youtube/v3/search", params={
            "key": key, "part": "id", "type": "video", "q": name, "videoCategoryId": "20",
            "publishedAfter": since, "order": "viewCount", "maxResults": 25,
        }).json()
        ids = [i["id"]["videoId"] for i in search.get("items", []) if i.get("id", {}).get("videoId")]
        views = 0
        if ids:
            stats = _get("https://www.googleapis.com/youtube/v3/videos", params={
                "key": key, "part": "statistics", "id": ",".join(ids),
            }).json()
            views = sum(int(v.get("statistics", {}).get("viewCount", 0)) for v in stats.get("items", []))
        results.append({
            "game_name": name,
            "value": views,
            "url": f"https://www.youtube.com/results?search_query={quote_plus(name)}&sp=CAMSAhAB",
            "image": "",
        })
    return results


# --- RELEVÉ COMPLET -----------------------------------------------------------

def _last_taken(source: str) -> datetime | None:
    with get_conn() as conn:
        row = conn.execute("SELECT MAX(taken_at) FROM trend_snapshots WHERE source = ?", (source,)).fetchone()
    return datetime.fromisoformat(row[0]) if row and row[0] else None


def save_snapshot(source: str, taken_at: str, rows: list[dict]):
    with get_conn() as conn:
        conn.executemany("""
            INSERT OR REPLACE INTO trend_snapshots (taken_at, source, game_key, game_name, value, url, image)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, [(taken_at, source, game_key(r["game_name"]), r["game_name"], int(r["value"]),
               r.get("url", ""), r.get("image", "")) for r in rows])


def collect_all() -> dict:
    """Fait un relevé de toutes les sources actives. Renvoie {source: nombre de jeux ou message d'erreur}."""
    env = load_env()
    active = active_sources(env)
    taken_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    report = {}

    collectors = [("steam", collect_steam)]
    if active["twitch"]:
        collectors.append(("twitch", lambda: collect_twitch(env)))
    for source, collector in collectors:
        try:
            rows = collector()
            save_snapshot(source, taken_at, rows)
            report[source] = len(rows)
        except Exception as e:
            report[source] = f"erreur : {e}"

    # YouTube après Twitch/Steam : il choisit ses jeux d'après leurs tendances
    if active["youtube"]:
        last = _last_taken("youtube")
        if last and datetime.now(timezone.utc) - last < timedelta(hours=YOUTUBE_MIN_INTERVAL_H):
            report["youtube"] = "déjà relevé aujourd'hui (quota)"
        else:
            try:
                rows = collect_youtube(env)
                save_snapshot("youtube", taken_at, rows)
                report["youtube"] = len(rows)
            except Exception as e:
                report["youtube"] = f"erreur : {e}"
    return report


# --- CALCUL DES TENDANCES -----------------------------------------------------

def snapshot_times(source: str) -> list[datetime]:
    with get_conn() as conn:
        rows = conn.execute("SELECT DISTINCT taken_at FROM trend_snapshots WHERE source = ? ORDER BY taken_at",
                            (source,)).fetchall()
    return [datetime.fromisoformat(r[0]) for r in rows]


def _values_at(source: str, taken_at: datetime) -> dict[str, dict]:
    with get_conn() as conn:
        rows = conn.execute("SELECT * FROM trend_snapshots WHERE source = ? AND taken_at = ?",
                            (source, taken_at.isoformat())).fetchall()
    return {r["game_key"]: dict(r) for r in rows}


def latest_snapshot(sources=("twitch", "steam"), limit: int = 50) -> list[dict]:
    rows = []
    for source in sources:
        times = snapshot_times(source)
        if times:
            rows += _values_at(source, times[-1]).values()
    rows.sort(key=lambda r: r["value"], reverse=True)
    seen, unique = set(), []
    for r in rows:  # un même jeu peut être sur Twitch et Steam : on garde sa plus grosse mesure
        if r["game_key"] not in seen:
            seen.add(r["game_key"])
            unique.append(r)
    return unique[:limit]


def _baseline_time(times: list[datetime], window_hours: int) -> datetime | None:
    """Relevé le plus proche de « dernier relevé - fenêtre » (tolérance : un quart de la fenêtre, 2 h min)."""
    if len(times) < 2:
        return None
    target = times[-1] - timedelta(hours=window_hours)
    tolerance = timedelta(hours=max(2, window_hours / 4))
    best = min(times[:-1], key=lambda t: abs(t - target))
    return best if abs(best - target) <= tolerance else None


def compute_trends(window_hours: int = 24, sources=("twitch", "steam", "youtube")) -> list[dict]:
    """Jeux classés par croissance sur la fenêtre choisie.

    Chaque jeu : {key, name, url, image, score (croissance max en %, None si nouveau), new (bool),
    sources: {source: {now, before, growth}}}. Les nouveaux venus (absents du relevé de référence
    mais au-dessus du seuil) sont marqués new=True et placés en tête.
    """
    games: dict[str, dict] = {}
    for source in sources:
        times = snapshot_times(source)
        base_time = _baseline_time(times, window_hours)
        if not base_time:
            continue
        now_vals, base_vals = _values_at(source, times[-1]), _values_at(source, base_time)
        for key, row in now_vals.items():
            if row["value"] < MIN_VALUES[source]:
                continue
            before = base_vals.get(key, {}).get("value")
            growth = None if before is None else (row["value"] - before) / max(before, 1) * 100
            game = games.setdefault(key, {
                "key": key, "name": row["game_name"], "url": row["url"], "image": row["image"],
                "sources": {}, "score": None, "new": False,
            })
            if not game["image"] and row["image"]:
                game["image"] = row["image"]
            if not game["url"]:
                game["url"] = row["url"] or f"https://store.steampowered.com/search/?term={quote_plus(row['game_name'])}"
            game["sources"][source] = {"now": row["value"], "before": before, "growth": growth}
            if growth is None:
                game["new"] = True
            elif game["score"] is None or growth > game["score"]:
                game["score"] = growth

    ranked = list(games.values())
    ranked.sort(key=lambda g: (g["new"] and g["score"] is None, g["score"] or 0), reverse=True)
    return ranked


def history(game_key_: str, days: int = 7) -> dict[str, list[tuple[datetime, int]]]:
    """Historique d'un jeu par source, pour les mini-courbes."""
    since = (datetime.now(timezone.utc) - timedelta(days=days)).isoformat()
    with get_conn() as conn:
        rows = conn.execute("""
            SELECT source, taken_at, value FROM trend_snapshots
            WHERE game_key = ? AND taken_at >= ? ORDER BY taken_at
        """, (game_key_, since)).fetchall()
    result: dict[str, list] = {}
    for r in rows:
        result.setdefault(r["source"], []).append((datetime.fromisoformat(r["taken_at"]), r["value"]))
    return result


def trend_summary(game: dict, window_hours: int) -> str:
    """Phrase prête pour le script de L'Hebdo."""
    period = "24 h" if window_hours == 24 else f"{window_hours // 24} jours"
    parts = []
    for source, s in game["sources"].items():
        label = SOURCE_LABELS[source].split(" ", 1)[1]
        now = f"{s['now']:,}".replace(",", " ")
        if s["growth"] is None:
            parts.append(f"{label} : nouveau venu avec {now} {UNITS[source]}")
        else:
            parts.append(f"{label} : {now} {UNITS[source]} ({s['growth']:+.0f} % en {period})")
    return "Jeu en forte progression. " + " ; ".join(parts) + "."
