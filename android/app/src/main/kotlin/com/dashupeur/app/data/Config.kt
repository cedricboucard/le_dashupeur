package com.dashupeur.app.data

object Config {
  val RSS_FEEDS: LinkedHashMap<String, String> = linkedMapOf(
    "MassivelyOP (MMO)" to "https://massivelyop.com/feed/",
    "MMORPG.com" to "https://www.mmorpg.com/rss",
    "ActuGaming" to "https://www.actugaming.net/feed/",
    "PC Gamer" to "https://www.pcgamer.com/rss/",
    "Rock Paper Shotgun" to "https://www.rockpapershotgun.com/feed",
    "Reddit r/MMORPG" to "https://www.reddit.com/r/MMORPG/hot.rss",
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
    "annulé" to 10,
    "canceled" to 10,
  )

  const val USER_AGENT = "LeDashupeur/1.0 (+https://github.com/cedricboucard/le_dashupeur)"
}
