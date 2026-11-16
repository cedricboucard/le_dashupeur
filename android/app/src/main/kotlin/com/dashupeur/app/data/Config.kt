package com.dashupeur.app.data

object Config {
  // Flux YouTube d'une chaîne (gratuit, sans clé) : seules les 15 dernières vidéos y figurent
  val RSS_FEEDS: LinkedHashMap<String, String> = linkedMapOf(
    // --- MMO ---
    "MassivelyOP (MMO)" to "https://massivelyop.com/feed/",
    "MMORPG.com" to "https://www.mmorpg.com/rss",
    "Reddit r/MMORPG" to "https://www.reddit.com/r/MMORPG/hot.rss",

    // --- Presse jeu vidéo ---
    "ActuGaming" to "https://www.actugaming.net/feed/",
    "Jeuxvideo.com" to "https://www.jeuxvideo.com/rss/rss.xml",
    "Gamekult" to "https://www.gamekult.com/feed.xml",
    "PC Gamer" to "https://www.pcgamer.com/rss/",
    "PCGamesN" to "https://www.pcgamesn.com/mainrss.xml",
    "Rock Paper Shotgun" to "https://www.rockpapershotgun.com/feed",
    "Gematsu" to "https://www.gematsu.com/feed",

    // --- YouTube : chaînes de trailers (là où sortent les annonces) ---
    "YouTube · IGN" to "https://www.youtube.com/feeds/videos.xml?channel_id=UCKy1dAqELo0zrOtPkf0eTMw",
    "YouTube · GameSpot" to "https://www.youtube.com/feeds/videos.xml?channel_id=UCbu2SsF-Or3Rsn3NxqODImw",
    "YouTube · GameTrailers" to "https://www.youtube.com/feeds/videos.xml?channel_id=UCJx5KP-pCUmL9eZUv-mIcNw",
    "YouTube · PlayStation" to "https://www.youtube.com/feeds/videos.xml?channel_id=UC-2Y8dQb0S6DtpxNgAKoJKA",
    "YouTube · Xbox" to "https://www.youtube.com/feeds/videos.xml?channel_id=UCjBp_7RuDBUYbd1LegWEJ8g",
    "YouTube · Indie Games - SpiderGaming" to "https://www.youtube.com/feeds/videos.xml?channel_id=UC4aub8IRhPGPU9Kjo7oD5Uw",
    "YouTube · Story Mode Trailers" to "https://www.youtube.com/feeds/videos.xml?channel_id=UC3-vre4hz9O-DnzrGfCpjaA",

    // --- YouTube : chaînes officielles de jeux suivis ---
    "YouTube · The First Descendant" to "https://www.youtube.com/feeds/videos.xml?channel_id=UCRjkGad-W6aGpLcZ0AKealg",
  )

  val KEYWORDS_WEIGHTS: Map<String, Int> = mapOf(
    "mmorpg" to 15,
    "mmo" to 12,
    "free-to-play" to 15,
    "free to play" to 15,
    "f2p" to 12,
    "gratuit" to 12,
    "beta" to 10,
    "bêta" to 10,
    "early access" to 8,
    "accès anticipé" to 8,
    "multijoueur" to 10,
    "multiplayer" to 10,
    "coop" to 8,
    "co-op" to 8,
    "steam" to 5,
    "fermeture" to 12,
    "shutdown" to 12,
    "date de sortie" to 10,
    "release date" to 10,
    "trailer" to 6,
    "gameplay" to 6,
    "announcement" to 8,
    "annonce" to 8,
    "reveal" to 6,
    "annulé" to 10,
    "canceled" to 10,
  )

  const val USER_AGENT = "LeDashupeur/1.0 (+https://github.com/cedricboucard/le_dashupeur)"
}
