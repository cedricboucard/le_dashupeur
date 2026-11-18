package com.dashupeur.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Suivi de la liste de souhaits Steam : annonces récentes (playtests, dates de sortie, week-ends gratuits…)
 * des jeux ajoutés le plus récemment à la liste, ajoutées à la boîte de réception.
 * La liste doit être publique (« Détails du jeu » sur Public dans la confidentialité du profil Steam).
 */
object SteamWishlist {
  const val SOURCE = "Steam · Liste de souhaits"
  private const val WISHLIST_URL = "https://api.steampowered.com/IWishlistService/GetWishlist/v1/"
  private const val APP_DETAILS_URL = "https://store.steampowered.com/api/appdetails"
  private const val NEWS_FEED = "https://store.steampowered.com/feeds/news/app/%d/?l=french"
  /** Un jeu de la liste de souhaits intéresse forcément : on le remonte dans le tri. */
  private const val WISHLIST_BONUS = 10
  private const val MAX_GAMES = 200
  private const val MAX_AGE_DAYS = 7L
  private const val PARALLEL_REQUESTS = 8
  private val STEAM_ID_RE = Regex("""\d{17}""")

  /** SteamID64 extrait de la saisie (accepte aussi une URL steamcommunity.com/profiles/<id>), ou "". */
  fun parseSteamId(raw: String): String = STEAM_ID_RE.find(raw)?.value.orEmpty()

  /** Préfixe le titre par le nom du jeu, sauf s'il le contient déjà. */
  fun titleWithGame(name: String, title: String): String =
    if (title.contains(name, ignoreCase = true)) title else "$name : $title"

  private fun fetchWishlist(steamId: String): List<Int> {
    val json = JSONObject(HttpUtils.getText(WISHLIST_URL, mapOf("steamid" to steamId), timeoutMs = 10000))
    val items = json.optJSONObject("response")?.optJSONArray("items") ?: return emptyList()
    return (0 until items.length())
      .map { items.getJSONObject(it) }
      .sortedByDescending { it.optLong("date_added") }
      .map { it.optInt("appid") }
      .filter { it > 0 }
  }

  private fun gameName(appId: Int): String = runCatching {
    val body = HttpUtils.getText(
      APP_DETAILS_URL,
      mapOf("appids" to appId.toString(), "filters" to "basic", "l" to "french"),
      timeoutMs = 10000,
    )
    val entry = JSONObject(body).optJSONObject(appId.toString())
    if (entry?.optBoolean("success") == true) entry.optJSONObject("data")?.optString("name").orEmpty() else ""
  }.getOrDefault("")

  private fun recentNews(appId: Int, since: Instant): List<NewsEngine.FeedEntry> = runCatching {
    val xml = HttpUtils.getText(NEWS_FEED.format(appId), timeoutMs = 10000)
    NewsEngine.parseRssOrAtom(xml).take(10).filter { entry ->
      val published = NewsEngine.parsePublished(entry.published)
      entry.url.isNotBlank() && published != null && !published.isBefore(since)
    }
  }.getOrDefault(emptyList())

  /** Ajoute les annonces des 7 derniers jours. Renvoie le nombre ajouté (0 si aucun SteamID). */
  suspend fun scan(db: DashupeurDb, rawSteamId: String): Int = withContext(Dispatchers.IO) {
    val steamId = parseSteamId(rawSteamId)
    if (steamId.isEmpty()) return@withContext 0
    val appIds = fetchWishlist(steamId).take(MAX_GAMES)
    if (appIds.isEmpty()) {
      error("liste de souhaits vide ou privée (rends « Détails du jeu » public sur Steam)")
    }
    val since = Instant.now().minus(MAX_AGE_DAYS, ChronoUnit.DAYS)
    val limit = Semaphore(PARALLEL_REQUESTS)
    val perGame = coroutineScope {
      appIds.map { id -> async { id to limit.withPermit { recentNews(id, since) } } }.awaitAll()
    }

    var added = 0
    for ((appId, entries) in perGame) {
      if (entries.isEmpty()) continue
      val name = gameName(appId).ifBlank { "Jeu Steam $appId" }
      for (entry in entries) {
        val title = titleWithGame(name, entry.title.ifBlank { "Sans titre" })
        val summary = NewsEngine.cleanHtml(entry.rawSummary)
        val (score, tags) = NewsEngine.scoreArticle(title, summary)
        val inserted = db.upsertNews(
          NewsItem(
            id = HttpUtils.md5Hex(entry.url),
            title = title,
            url = entry.url,
            source = SOURCE,
            published = NewsEngine.parsePublished(entry.published)?.toString().orEmpty(),
            summary = summary,
            score = score + WISHLIST_BONUS,
            tags = (listOf("liste de souhaits") + tags).joinToString(", "),
            status = "inbox",
          )
        )
        if (inserted) added++
      }
    }
    added
  }
}
