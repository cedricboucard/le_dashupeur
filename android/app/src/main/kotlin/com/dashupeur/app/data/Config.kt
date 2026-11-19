package com.dashupeur.app.data

object Config {
  // Flux YouTube d'une chaîne (gratuit, sans clé) : seules les 15 dernières vidéos y figurent
  val RSS_FEEDS: LinkedHashMap<String, String> = linkedMapOf(
    // --- MMO ---
    "MassivelyOP (MMO)" to "https://massivelyop.com/feed/",
    "MMORPG.com" to "https://www.mmorpg.com/rss",

    // --- Presse jeu vidéo ---
    "ActuGaming" to "https://www.actugaming.net/feed/",
    "Jeuxvideo.com" to "https://www.jeuxvideo.com/rss/rss.xml",
    "Gamekult" to "https://www.gamekult.com/feed.xml",
    "PC Gamer" to "https://www.pcgamer.com/rss/",
    "PCGamesN" to "https://www.pcgamesn.com/mainrss.xml",
    "Rock Paper Shotgun" to "https://www.rockpapershotgun.com/feed",
    "Gematsu" to "https://www.gematsu.com/feed",

    // --- Google News : recherches par mot-clé (versions PC, sorties mondiales, bêtas, fermetures) ---
    "Google News · Versions PC" to "https://news.google.com/rss/search?q=%22PC+version%22+(gacha+OR+mobile+OR+MMORPG)+when:7d&hl=en-US&gl=US&ceid=US:en",
    "Google News · Lancements mondiaux" to "https://news.google.com/rss/search?q=(%22global+launch%22+OR+%22global+release%22+OR+%22pre-registration%22)+game+when:7d&hl=en-US&gl=US&ceid=US:en",
    "Google News · Bêtas MMO et gacha" to "https://news.google.com/rss/search?q=(%22closed+beta%22+OR+%22open+beta%22+OR+playtest)+(MMO+OR+MMORPG+OR+gacha)+when:7d&hl=en-US&gl=US&ceid=US:en",
    "Google News · Playtests et accès anticipé" to "https://news.google.com/rss/search?q=(playtest+OR+%22early+access%22+OR+%22free+weekend%22)+(Steam+OR+PC)+-review+when:3d&hl=en-US&gl=US&ceid=US:en",
    "Google News · Fermetures" to "https://news.google.com/rss/search?q=(MMO+OR+gacha+OR+%22live+service%22)+(%22end+of+service%22+OR+shutdown)+when:7d&hl=en-US&gl=US&ceid=US:en",
    "Google News · Com2uS" to "https://news.google.com/rss/search?q=Com2uS+when:7d&hl=en-US&gl=US&ceid=US:en",
    // TEMPORAIRE : salon G-Star (Busan, 19-22 novembre 2026), à retirer après le salon
    "Google News · G-Star 2026" to "https://news.google.com/rss/search?q=(%22G-Star+2026%22+OR+%22G-Star%22+Busan)+when:7d&hl=en-US&gl=US&ceid=US:en",
    "Google News · MMO et gacha (FR)" to "https://news.google.com/rss/search?q=(MMORPG+OR+gacha)+when:7d&hl=fr&gl=FR&ceid=FR:fr",

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
    // --- Steam : actualités des jeux suivis ---
    "Steam · Under a Rock" to "https://store.steampowered.com/feeds/news/app/1667040/?l=french",
    "Steam · The First Descendant" to "https://store.steampowered.com/feeds/news/app/2074920/?l=french",
    "Steam · ARES: The Iron Vanguard" to "https://store.steampowered.com/feeds/news/app/5366290/?l=french",
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
    "playtest" to 10,
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

  const val SHOW_NAME = "On épluche l'actu" // Nom de l'émission YouTube

  // GlobeNewswire refuse les requêtes de l'app : liens de veille manuelle (nom → recherche)
  val MANUAL_WATCH_LINKS: List<Pair<String, String>> = listOf(
    "🎮 Jeu vidéo" to "https://www.globenewswire.com/en/search/keyword/video%20game",
    "⚔️ MMORPG" to "https://www.globenewswire.com/en/search/keyword/MMORPG",
  )
  // MMO coréens et asiatiques : suivis à la main, pas scannés (l'émission reste centrée sur les sorties locales)
  val ASIA_WATCH_LINKS: List<Pair<String, String>> = listOf(
    "▶️ IceXgame" to "https://www.youtube.com/@IceXgame/videos",
    "📰 Inven" to "https://www.invenglobal.com/",
    "📰 MMO Culture" to "https://mmoculture.com/",
  )
  /**
   * Sujets rangés à part dans la boîte de réception (pour ne pas noyer les news MMO / F2P) :
   * une news y va si son titre correspond à l'une des expressions (sans tenir compte des majuscules).
   */
  val SEPARATE_TOPICS: Map<String, List<Regex>> = mapOf(
    "🚗 GTA" to listOf("""\bgta""", "grand theft auto", """\brockstar games\b""")
      .map { Regex(it, RegexOption.IGNORE_CASE) },
  )

  /** Sujet « à part » de la news (ex. « 🚗 GTA ») d'après son titre, ou null. */
  fun separateTopic(title: String): String? =
    SEPARATE_TOPICS.entries.firstOrNull { (_, patterns) -> patterns.any { it.containsMatchIn(title) } }?.key

  const val USER_AGENT = "LeDashupeur/1.0 (+https://github.com/cedricboucard/le_dashupeur)"
}
