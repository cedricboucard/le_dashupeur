"""Suivi de la liste de souhaits Steam : annonces récentes (playtests, dates de sortie, week-ends gratuits…)
de chaque jeu de la liste, ajoutées à la boîte de réception.

La liste doit être publique (Steam > Profil > Modifier le profil > Confidentialité > Détails du jeu : Public)
et le SteamID64 (17 chiffres) mis dans .env sous STEAM_ID=7656119…
"""
import hashlib
import re
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone

import feedparser
import requests

from config import STEAM_NEWS_FEED, STEAM_WISHLIST_API, USER_AGENT
from db import upsert_news
from engine import clean_html, entry_published_iso, score_article
from trends import load_env

SOURCE = "Steam · Liste de souhaits"
WISHLIST_BONUS = 10  # un jeu de la liste de souhaits intéresse forcément : on le remonte dans le tri
HEADERS = {"User-Agent": USER_AGENT}


def steam_id() -> str:
    """SteamID64 lu dans .env ; accepte aussi une URL steamcommunity.com/profiles/<id>."""
    raw = load_env().get("STEAM_ID", "")
    match = re.search(r"\d{17}", raw)
    return match.group(0) if match else ""


def fetch_wishlist(steamid: str) -> list[int]:
    """Identifiants Steam des jeux de la liste, les plus récemment ajoutés d'abord."""
    resp = requests.get(STEAM_WISHLIST_API.format(steamid), headers=HEADERS, timeout=10)
    resp.raise_for_status()
    items = resp.json().get("response", {}).get("items", [])
    items.sort(key=lambda i: i.get("date_added", 0), reverse=True)
    return [int(i["appid"]) for i in items if "appid" in i]


def _game_name(appid: int) -> str:
    try:
        resp = requests.get("https://store.steampowered.com/api/appdetails",
                            params={"appids": appid, "filters": "basic", "l": "french"},
                            headers=HEADERS, timeout=10)
        data = resp.json().get(str(appid), {})
        return data.get("data", {}).get("name", "") if data.get("success") else ""
    except (requests.RequestException, ValueError):
        return ""


def _recent_news(appid: int, since: datetime) -> list[dict]:
    """Annonces Steam du jeu publiées depuis `since` (vide si le flux est inaccessible)."""
    try:
        resp = requests.get(STEAM_NEWS_FEED.format(appid), headers=HEADERS, timeout=10)
        resp.raise_for_status()
    except requests.RequestException:
        return []
    news = []
    for entry in feedparser.parse(resp.content).entries[:10]:
        published = entry_published_iso(entry)
        if not entry.get("link") or not published or datetime.fromisoformat(published) < since:
            continue
        news.append({"title": entry.get("title", "Sans titre").strip(), "url": entry["link"],
                     "published": published,
                     "summary": clean_html(entry.get("summary", entry.get("description", "")))})
    return news


def scan_wishlist(max_age_days: int = 7, max_games: int = 200) -> int:
    """Ajoute les annonces des `max_age_days` derniers jours des jeux de la liste. Renvoie le nombre ajouté.
    Ne fait rien si aucun STEAM_ID n'est configuré."""
    steamid = steam_id()
    if not steamid:
        return 0
    appids = fetch_wishlist(steamid)[:max_games]
    if not appids:
        raise ValueError("liste de souhaits vide ou privée (rends « Détails du jeu » public sur Steam)")
    since = datetime.now(timezone.utc) - timedelta(days=max_age_days)
    with ThreadPoolExecutor(max_workers=8) as pool:
        per_game = dict(zip(appids, pool.map(lambda a: _recent_news(a, since), appids)))

    added = 0
    for appid, news in per_game.items():
        if not news:
            continue
        name = _game_name(appid) or f"Jeu Steam {appid}"
        for item in news:
            title = item["title"] if name.lower() in item["title"].lower() else f"{name} : {item['title']}"
            score, tags = score_article(title, item["summary"])
            inserted = upsert_news({
                "id": hashlib.md5(item["url"].encode("utf-8")).hexdigest(),
                "title": title,
                "url": item["url"],
                "source": SOURCE,
                "published": item["published"],
                "summary": item["summary"],
                "score": score + WISHLIST_BONUS,
                "tags": ", ".join(["liste de souhaits", *tags]),
            })
            added += inserted
    return added
