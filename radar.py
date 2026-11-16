"""Radar Jeux Gratuits & F2P : Epic Games Store, GamerPower et Steam, en temps réel.

Chaque fonction fetch_* renvoie une liste de jeux au même format (voir _item),
que l'interface affiche et que send_to_hebdo peut envoyer dans le conducteur de L'Hebdo.
"""
import hashlib
import re
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone

import requests
from bs4 import BeautifulSoup

from config import USER_AGENT
from db import update_status, upsert_news
from engine import clean_html, score_article

HEADERS = {"User-Agent": USER_AGENT}
TIMEOUT = 15

EPIC_URL = "https://store-site-backend-static.ak.epicgames.com/freeGamesPromotions"
GAMERPOWER_URL = "https://www.gamerpower.com/api/filter"
# Plateformes PC suivies sur GamerPower (séparées par des points, format de l'API)
GAMERPOWER_PLATFORMS = "steam.gog.epic-games-store.itchio.ubisoft.origin.battlenet.drm-free"
STEAM_SEARCH_URL = "https://store.steampowered.com/search/results/"
STEAM_PLAYERS_URL = "https://api.steampowered.com/ISteamUserStats/GetNumberOfCurrentPlayers/v1/"
STEAM_DETAILS_URL = "https://store.steampowered.com/api/appdetails"
STEAM_TAG_FREE_TO_PLAY = 113

GAMERPOWER_TYPES = {"Game": "🎮 Jeu offert", "DLC": "🧩 DLC offert", "Early Access": "🧪 Bêta / Playtest"}


def _get(url: str, **params) -> requests.Response:
    resp = requests.get(url, params=params, headers=HEADERS, timeout=TIMEOUT)
    resp.raise_for_status()
    return resp


def _parse_dt(value: str | None, fmt: str | None = None, naive_is_local: bool = False) -> datetime | None:
    """Date avec fuseau horaire. Une date sans fuseau est lue en UTC, ou en heure locale si naive_is_local."""
    if not value or value == "N/A":
        return None
    try:
        dt = datetime.strptime(value, fmt) if fmt else datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
    if dt.tzinfo:
        return dt
    return dt.astimezone() if naive_is_local else dt.replace(tzinfo=timezone.utc)


def _item(**fields) -> dict:
    """Format commun d'un jeu du radar."""
    base = {
        "title": "", "url": "", "source": "", "kind": "", "platforms": "", "image": "",
        "summary": "", "starts": None, "ends": None, "worth": "", "players": None,
        "released": "", "claimed": None, "steam_appid": None,
    }
    base.update(fields)
    return base


# --- EPIC GAMES STORE ---------------------------------------------------------

def _epic_slug(el: dict) -> str:
    slug = (el.get("productSlug") or "").split("/")[0]
    if slug:
        return slug
    for mappings in ((el.get("catalogNs") or {}).get("mappings"), el.get("offerMappings")):
        for m in mappings or []:
            if m.get("pageSlug"):
                return m["pageSlug"]
    return ""


def _epic_image(el: dict) -> str:
    images = {img["type"]: img["url"] for img in el.get("keyImages") or []}
    for wanted in ("OfferImageWide", "DieselStoreFrontWide", "Thumbnail", "OfferImageTall"):
        if wanted in images:
            return images[wanted]
    return next(iter(images.values()), "")


def fetch_epic_free() -> list[dict]:
    """Jeux gratuits sur l'Epic Games Store : ceux du moment et ceux de la semaine prochaine."""
    data = _get(EPIC_URL, locale="fr", country="FR", allowCountries="FR").json()
    elements = data["data"]["Catalog"]["searchStore"]["elements"]
    items = []
    for el in elements:
        promos = el.get("promotions") or {}
        for key, kind in (("promotionalOffers", "🟢 Gratuit maintenant"),
                          ("upcomingPromotionalOffers", "⏭️ Gratuit la semaine prochaine")):
            offers = [o for g in promos.get(key) or [] for o in g.get("promotionalOffers") or []]
            # discountPercentage = pourcentage du prix restant : 0 signifie gratuit
            free = [o for o in offers if o.get("discountSetting", {}).get("discountPercentage") == 0]
            if not free:
                continue
            slug = _epic_slug(el)
            items.append(_item(
                title=el["title"],
                url=f"https://store.epicgames.com/fr/p/{slug}" if slug else "https://store.epicgames.com/fr/free-games",
                source="Epic Games Store",
                kind=kind,
                platforms="PC, Epic Games Store",
                image=_epic_image(el),
                summary=clean_html(el.get("description", "")),
                starts=_parse_dt(free[0].get("startDate")),
                ends=_parse_dt(free[0].get("endDate")),
                worth=(el.get("price") or {}).get("totalPrice", {}).get("fmtPrice", {}).get("originalPrice", ""),
            ))
    return items


# --- GAMERPOWER ---------------------------------------------------------------

def _clean_giveaway_title(title: str) -> str:
    title = re.sub(r"\s*(Steam Key\s*)?Giveaway$", "", title, flags=re.IGNORECASE)
    return re.sub(r"\s*\((Steam|Epic Games|GOG|Itch\.io|PC)\)$", "", title).strip()


def fetch_gamerpower() -> list[dict]:
    """Jeux, DLC et bêtas offerts sur les boutiques PC (Steam, GOG, Epic, Itch.io…)."""
    resp = _get(GAMERPOWER_URL, platform=GAMERPOWER_PLATFORMS, type="game.loot.beta", **{"sort-by": "date"})
    data = resp.json()
    if not isinstance(data, list):  # l'API renvoie un objet quand il n'y a aucun résultat
        return []
    now = datetime.now(timezone.utc)
    items = []
    for g in data:
        ends = _parse_dt(g.get("end_date"), "%Y-%m-%d %H:%M:%S", naive_is_local=True)
        if g.get("status") != "Active" or (ends and ends < now):
            continue
        items.append(_item(
            title=_clean_giveaway_title(g["title"]),
            url=g.get("gamerpower_url") or g.get("open_giveaway_url", ""),
            source="GamerPower",
            kind=GAMERPOWER_TYPES.get(g.get("type"), g.get("type", "")),
            platforms=g.get("platforms", ""),
            image=g.get("thumbnail") or g.get("image", ""),
            summary=g.get("description", ""),
            starts=_parse_dt(g.get("published_date"), "%Y-%m-%d %H:%M:%S", naive_is_local=True),
            ends=ends,
            worth="" if g.get("worth") in (None, "N/A") else g["worth"],
            claimed=g.get("users"),
        ))
    return items


# --- STEAM --------------------------------------------------------------------

def _steam_players(appid: str) -> int | None:
    try:
        data = _get(STEAM_PLAYERS_URL, appid=appid).json()
        return data["response"].get("player_count")
    except Exception:
        return None


FRENCH_MONTHS = {
    "janv": 1, "févr": 2, "fevr": 2, "mars": 3, "avr": 4, "mai": 5, "juin": 6,
    "juil": 7, "août": 8, "aout": 8, "sept": 9, "oct": 10, "nov": 11, "déc": 12, "dec": 12,
}


def _parse_steam_date(text: str) -> datetime | None:
    """Convertit une date Steam en français (« 5 oct. 2026 ») en datetime."""
    m = re.match(r"(\d{1,2})\s+([^\s.]+)\.?\s+(\d{4})", text.strip())
    if not m or m.group(2).lower() not in FRENCH_MONTHS:
        return None
    return datetime(int(m.group(3)), FRENCH_MONTHS[m.group(2).lower()], int(m.group(1)), tzinfo=timezone.utc)


def _steam_search(**params) -> list[dict]:
    data = _get(
        STEAM_SEARCH_URL, query="", start=0, tags=STEAM_TAG_FREE_TO_PLAY, category1=998,
        sort_by="Released_DESC", infinite=1, json=1, cc="fr", l="french", **params,
    ).json()
    soup = BeautifulSoup(data.get("results_html", ""), "html.parser")
    items = []
    for row in soup.select("a.search_result_row"):
        appid = row.get("data-ds-appid", "").split(",")[0]
        title = row.select_one(".title")
        if not appid or not title:
            continue
        img = row.select_one(".search_capsule img")
        released = row.select_one(".search_released")
        items.append(_item(
            title=title.get_text(strip=True),
            url=f"https://store.steampowered.com/app/{appid}/",
            source="Steam",
            kind="🆓 Nouveau Free-to-Play",
            platforms="PC, Steam",
            image=img.get("src", "") if img else "",
            released=released.get_text(strip=True) if released else "",
            starts=_parse_steam_date(released.get_text()) if released else None,
            steam_appid=appid,
        ))
    return items


def fetch_steam_f2p(max_age_days: int = 365, limit: int = 25) -> list[dict]:
    """Free-to-Play sortis sur Steam ces derniers mois, classés par nombre de joueurs connectés en direct.

    Combine la sélection « nouveautés populaires » de Steam et les toutes dernières sorties,
    pour repérer aussi bien les gros lancements que les petites surprises.
    """
    found = {}
    for params in ({"filter": "popularnew", "count": 50}, {"count": 100}):
        for item in _steam_search(**params):
            found.setdefault(item["steam_appid"], item)

    now = datetime.now(timezone.utc)
    items = [i for i in found.values() if i["starts"] and (now - i["starts"]).days <= max_age_days]

    # Nombre de joueurs en direct, récupéré en parallèle
    with ThreadPoolExecutor(max_workers=8) as pool:
        for item, players in zip(items, pool.map(_steam_players, [i["steam_appid"] for i in items])):
            item["players"] = players
    items.sort(key=lambda i: i["players"] or 0, reverse=True)
    return items[:limit]


def _steam_description(appid: str) -> str:
    try:
        data = _get(STEAM_DETAILS_URL, appids=appid, l="french", cc="fr").json()
        return clean_html(data[appid]["data"].get("short_description", ""))
    except Exception:
        return ""


# --- PONT VERS L'HEBDO --------------------------------------------------------

def radar_news_id(item: dict) -> str:
    """Même identifiant que les news RSS (hash de l'URL), pour éviter les doublons."""
    return hashlib.md5(item["url"].encode("utf-8")).hexdigest()


def build_summary(item: dict) -> str:
    parts = []
    description = item["summary"]
    if not description and item["steam_appid"]:
        description = _steam_description(item["steam_appid"])
    if description:
        parts.append(description)
    if item["worth"]:
        parts.append(f"Valeur : {item['worth']}.")
    if item["starts"] and item["starts"] > datetime.now(timezone.utc):
        parts.append(f"Gratuit à partir du {item['starts'].astimezone():%d/%m}.")
    if item["ends"]:
        parts.append(f"Offre valable jusqu'au {item['ends'].astimezone():%d/%m à %H:%M}.")
    if item["players"] is not None:
        parts.append(f"{item['players']:,} joueurs connectés sur Steam au moment de la veille.".replace(",", " "))
    if item["released"]:
        parts.append(f"Sorti le {item['released']}.")
    return " ".join(parts)


def send_to_hebdo(item: dict, status: str) -> str:
    """Ajoute un jeu du radar au conducteur de L'Hebdo ('major' ou 'vrac'). Renvoie l'id de la news."""
    news_id = radar_news_id(item)
    summary = build_summary(item)
    score, tags = score_article(item["title"], summary)
    kind = re.sub(r"^\W+\s*", "", item["kind"])  # sans l'emoji
    upsert_news({
        "id": news_id,
        "title": item["title"],
        "url": item["url"],
        "source": f"Radar · {item['source']}",
        "published": (item["starts"] or datetime.now(timezone.utc)).isoformat(),
        "summary": summary,
        "score": score,
        "tags": ", ".join([kind] + tags),
    })
    update_status(news_id, status)
    return news_id
