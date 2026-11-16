# Flux YouTube d'une chaîne (gratuit, sans clé) : seules les 15 dernières vidéos y figurent
YOUTUBE_FEED = "https://www.youtube.com/feeds/videos.xml?channel_id={}"

RSS_FEEDS = {
    # --- MMO ---
    "MassivelyOP (MMO)": "https://massivelyop.com/feed/",
    "MMORPG.com": "https://www.mmorpg.com/rss",
    "Reddit r/MMORPG": "https://www.reddit.com/r/MMORPG/hot.rss",

    # --- Presse jeu vidéo ---
    "ActuGaming": "https://www.actugaming.net/feed/",
    "Jeuxvideo.com": "https://www.jeuxvideo.com/rss/rss.xml",
    "Gamekult": "https://www.gamekult.com/feed.xml",
    "PC Gamer": "https://www.pcgamer.com/rss/",
    "PCGamesN": "https://www.pcgamesn.com/mainrss.xml",
    "Rock Paper Shotgun": "https://www.rockpapershotgun.com/feed",
    "Gematsu": "https://www.gematsu.com/feed",

    # --- YouTube : chaînes de trailers (là où sortent les annonces) ---
    "YouTube · IGN": YOUTUBE_FEED.format("UCKy1dAqELo0zrOtPkf0eTMw"),
    "YouTube · GameSpot": YOUTUBE_FEED.format("UCbu2SsF-Or3Rsn3NxqODImw"),
    "YouTube · GameTrailers": YOUTUBE_FEED.format("UCJx5KP-pCUmL9eZUv-mIcNw"),
    "YouTube · PlayStation": YOUTUBE_FEED.format("UC-2Y8dQb0S6DtpxNgAKoJKA"),
    "YouTube · Xbox": YOUTUBE_FEED.format("UCjBp_7RuDBUYbd1LegWEJ8g"),
    "YouTube · Indie Games - SpiderGaming": YOUTUBE_FEED.format("UC4aub8IRhPGPU9Kjo7oD5Uw"),
    "YouTube · Story Mode Trailers": YOUTUBE_FEED.format("UC3-vre4hz9O-DnzrGfCpjaA"),

    # --- YouTube : chaînes officielles de jeux suivis (ajoute les tiennes ici) ---
    "YouTube · The First Descendant": YOUTUBE_FEED.format("UCRjkGad-W6aGpLcZ0AKealg"),
}

# Mots-clés qui boostent le score de pertinence d'une news
KEYWORDS_WEIGHTS = {
    "mmorpg": 15,
    "mmo": 12,
    "free-to-play": 15,
    "free to play": 15,
    "f2p": 12,
    "gratuit": 12,
    "beta": 10,
    "bêta": 10,
    "early access": 8,
    "accès anticipé": 8,
    "multijoueur": 10,
    "multiplayer": 10,
    "coop": 8,
    "co-op": 8,
    "steam": 5,
    "fermeture": 12,
    "shutdown": 12,
    "date de sortie": 10,
    "release date": 10,
    "trailer": 6,
    "gameplay": 6,
    "announcement": 8,
    "annonce": 8,
    "reveal": 6,
    "annulé": 10,
    "canceled": 10,
}

USER_AGENT = "LeDashupeur/1.0 (+https://github.com/cedricboucard/le_dashupeur)"
