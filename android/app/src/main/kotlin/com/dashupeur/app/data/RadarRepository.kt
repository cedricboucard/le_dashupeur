package com.dashupeur.app.data

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object RadarRepository {
  private const val EPIC_URL = "https://store-site-backend-static.ak.epicgames.com/freeGamesPromotions"
  private const val GAMERPOWER_URL = "https://www.gamerpower.com/api/filter"
  private const val GAMERPOWER_PLATFORMS = "steam.gog.epic-games-store.itchio.ubisoft.origin.battlenet.drm-free"
  private const val STEAM_SEARCH_URL = "https://store.steampowered.com/search/results/"
  private const val STEAM_PLAYERS_URL = "https://api.steampowered.com/ISteamUserStats/GetNumberOfCurrentPlayers/v1/"
  private const val STEAM_DETAILS_URL = "https://store.steampowered.com/api/appdetails"

  private val GAMERPOWER_TYPES = mapOf(
    "Game" to "🎮 Jeu offert",
    "DLC" to "🧩 DLC offert",
    "Early Access" to "🧪 Bêta / Playtest",
  )

  private val FRENCH_MONTHS = mapOf(
    "janv" to 1, "févr" to 2, "fevr" to 2, "mars" to 3, "avr" to 4, "mai" to 5, "juin" to 6,
    "juil" to 7, "août" to 8, "aout" to 8, "sept" to 9, "oct" to 10, "nov" to 11, "déc" to 12, "dec" to 12,
  )

  private fun parseIsoInstant(value: String?): Instant? {
    if (value.isNullOrBlank() || value == "N/A") return null
    return runCatching { Instant.parse(value) }
      .recoverCatching { OffsetDateTime.parse(value).toInstant() }
      .getOrNull()
  }

  private fun parseLocalDateTime(value: String?): Instant? {
    if (value.isNullOrBlank() || value == "N/A") return null
    return runCatching {
      val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
      LocalDateTime.parse(value, fmt).atZone(ZoneId.systemDefault()).toInstant()
    }.getOrNull()
  }

  suspend fun fetchEpicFree(): List<RadarItem> = withContext(Dispatchers.IO) {
    val body = HttpUtils.getText(
      EPIC_URL,
      mapOf("locale" to "fr", "country" to "FR", "allowCountries" to "FR"),
    )
    val root = JSONObject(body)
    val elements = root.getJSONObject("data")
      .getJSONObject("Catalog")
      .getJSONObject("searchStore")
      .getJSONArray("elements")

    val items = mutableListOf<RadarItem>()
    for (i in 0 until elements.length()) {
      val el = elements.getJSONObject(i)
      val promos = el.optJSONObject("promotions") ?: continue
      val checks = listOf(
        "promotionalOffers" to "🟢 Gratuit maintenant",
        "upcomingPromotionalOffers" to "⏭️ Gratuit la semaine prochaine",
      )
      for ((key, kind) in checks) {
        val groups = promos.optJSONArray(key) ?: continue
        val freeOffers = mutableListOf<JSONObject>()
        for (g in 0 until groups.length()) {
          val offers = groups.getJSONObject(g).optJSONArray("promotionalOffers") ?: continue
          for (o in 0 until offers.length()) {
            val offer = offers.getJSONObject(o)
            val pct = offer.optJSONObject("discountSetting")?.optInt("discountPercentage", -1) ?: -1
            if (pct == 0) freeOffers.add(offer)
          }
        }
        if (freeOffers.isEmpty()) continue
        val slug = epicSlug(el)
        val worth = el.optJSONObject("price")
          ?.optJSONObject("totalPrice")
          ?.optJSONObject("fmtPrice")
          ?.optString("originalPrice", "")
          .orEmpty()
        items.add(
          RadarItem(
            title = el.optString("title", "Jeu Epic"),
            url = if (slug.isNotBlank()) "https://store.epicgames.com/fr/p/$slug" else "https://store.epicgames.com/fr/free-games",
            source = "Epic Games Store",
            kind = kind,
            platforms = "PC, Epic Games Store",
            image = epicImage(el),
            summary = NewsEngine.cleanHtml(el.optString("description", "")),
            starts = parseIsoInstant(freeOffers[0].optString("startDate")),
            ends = parseIsoInstant(freeOffers[0].optString("endDate")),
            worth = worth,
          )
        )
      }
    }
    items
  }

  private fun epicSlug(el: JSONObject): String {
    val productSlug = el.optString("productSlug", "").substringBefore("/")
    if (productSlug.isNotBlank()) return productSlug
    val mappings = el.optJSONObject("catalogNs")?.optJSONArray("mappings")
    if (mappings != null) {
      for (i in 0 until mappings.length()) {
        val slug = mappings.getJSONObject(i).optString("pageSlug", "")
        if (slug.isNotBlank()) return slug
      }
    }
    val offerMappings = el.optJSONArray("offerMappings")
    if (offerMappings != null) {
      for (i in 0 until offerMappings.length()) {
        val slug = offerMappings.getJSONObject(i).optString("pageSlug", "")
        if (slug.isNotBlank()) return slug
      }
    }
    return ""
  }

  private fun epicImage(el: JSONObject): String {
    val keyImages = el.optJSONArray("keyImages") ?: return ""
    val byType = linkedMapOf<String, String>()
    for (i in 0 until keyImages.length()) {
      val img = keyImages.getJSONObject(i)
      byType[img.optString("type")] = img.optString("url")
    }
    for (wanted in listOf("OfferImageWide", "DieselStoreFrontWide", "Thumbnail", "OfferImageTall")) {
      byType[wanted]?.let { if (it.isNotBlank()) return it }
    }
    return byType.values.firstOrNull().orEmpty()
  }

  suspend fun fetchGamerPower(): List<RadarItem> = withContext(Dispatchers.IO) {
    val body = HttpUtils.getText(
      GAMERPOWER_URL,
      mapOf(
        "platform" to GAMERPOWER_PLATFORMS,
        "type" to "game.loot.beta",
        "sort-by" to "date",
      ),
    )
    if (!body.trimStart().startsWith("[")) return@withContext emptyList()
    val arr = JSONArray(body)
    val now = Instant.now()
    val items = mutableListOf<RadarItem>()
    for (i in 0 until arr.length()) {
      val g = arr.getJSONObject(i)
      if (g.optString("status") != "Active") continue
      val ends = parseLocalDateTime(g.optString("end_date"))
      if (ends != null && ends.isBefore(now)) continue
      val rawTitle = g.optString("title", "")
        .replace(Regex("""\s*(Steam Key\s*)?Giveaway$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*\((Steam|Epic Games|GOG|Itch\.io|PC)\)$"""), "")
        .trim()
      val rawType = g.optString("type", "")
      val worth = g.optString("worth", "").let { if (it == "N/A" || it == "null") "" else it }
      items.add(
        RadarItem(
          title = rawTitle,
          url = g.optString("gamerpower_url").ifBlank { g.optString("open_giveaway_url", "") },
          source = "GamerPower",
          kind = GAMERPOWER_TYPES[rawType] ?: rawType,
          platforms = g.optString("platforms", ""),
          image = g.optString("thumbnail").ifBlank { g.optString("image", "") },
          summary = g.optString("description", ""),
          starts = parseLocalDateTime(g.optString("published_date")),
          ends = ends,
          worth = worth,
          claimed = if (g.has("users") && !g.isNull("users")) g.optInt("users") else null,
        )
      )
    }
    items
  }

  private fun parseSteamDate(text: String): Instant? {
    val m = Regex("""(\d{1,2})\s+([^\s.]+)\.?\s+(\d{4})""").find(text.trim()) ?: return null
    val day = m.groupValues[1].toIntOrNull() ?: return null
    val month = FRENCH_MONTHS[m.groupValues[2].lowercase()] ?: return null
    val year = m.groupValues[3].toIntOrNull() ?: return null
    return runCatching {
      LocalDateTime.of(year, month, day, 0, 0).toInstant(ZoneOffset.UTC)
    }.getOrNull()
  }

  /** Recherche Steam des jeux les plus récents : free-to-play par défaut, tous les jeux si tags = null. */
  private fun steamSearch(extraParams: Map<String, String>, tags: String? = "113"): List<RadarItem> {
    val params = linkedMapOf(
      "query" to "",
      "start" to "0",
      "category1" to "998",
      "sort_by" to "Released_DESC",
      "infinite" to "1",
      "json" to "1",
      "cc" to "fr",
      "l" to "french",
    ).apply {
      if (tags != null) put("tags", tags)
      putAll(extraParams)
    }

    val body = HttpUtils.getText(STEAM_SEARCH_URL, params)
    val html = JSONObject(body).optString("results_html", "")
    // Steam écrit data-ds-appid avant class="search_result_row" : on ne dépend pas de l'ordre des attributs
    val rowRegex = Regex("""<a\s[^>]*?data-ds-appid="([^"]+)"[^>]*>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL))
    val titleRegex = Regex("""<span class="title"[^>]*>(.*?)</span>""", setOf(RegexOption.DOT_MATCHES_ALL))
    val imgRegex = Regex("""<img[^>]*src="([^"]+)"[^>]*>""")
    val relRegex = Regex("""search_released[^>]*>(.*?)</div>""", setOf(RegexOption.DOT_MATCHES_ALL))

    val items = mutableListOf<RadarItem>()
    for (match in rowRegex.findAll(html)) {
      val appid = match.groupValues[1].substringBefore(",").trim()
      val block = match.groupValues[2]
      val rawTitle = titleRegex.find(block)?.groupValues?.get(1) ?: continue
      val title = Html.fromHtml(rawTitle, Html.FROM_HTML_MODE_LEGACY).toString().trim()
      if (appid.isBlank() || title.isBlank()) continue
      val img = imgRegex.find(block)?.groupValues?.get(1).orEmpty()
      val rawRel = relRegex.find(block)?.groupValues?.get(1).orEmpty()
      val released = Html.fromHtml(rawRel, Html.FROM_HTML_MODE_LEGACY).toString().trim()
      items.add(
        RadarItem(
          title = title,
          url = "https://store.steampowered.com/app/$appid/",
          source = "Steam",
          kind = "🆓 Nouveau Free-to-Play",
          platforms = "PC, Steam",
          image = img,
          summary = "",
          starts = parseSteamDate(released),
          released = released,
          steamAppId = appid,
        )
      )
    }
    return items
  }

  private fun steamPlayers(appid: String): Int? = runCatching {
    val body = HttpUtils.getText(STEAM_PLAYERS_URL, mapOf("appid" to appid), timeoutMs = 8000)
    JSONObject(body).getJSONObject("response").optInt("player_count")
  }.getOrNull()

  private fun steamDescription(appid: String): String = runCatching {
    val body = HttpUtils.getText(STEAM_DETAILS_URL, mapOf("appids" to appid, "l" to "french", "cc" to "fr"), timeoutMs = 8000)
    val shortDesc = JSONObject(body).getJSONObject(appid).getJSONObject("data").optString("short_description", "")
    NewsEngine.cleanHtml(shortDesc)
  }.getOrDefault("")

  suspend fun fetchSteamF2p(maxAgeDays: Long = 365, limit: Int = 25): List<RadarItem> = withContext(Dispatchers.IO) {
    val found = linkedMapOf<String, RadarItem>()
    var lastError: Throwable? = null
    for (params in listOf(mapOf("filter" to "popularnew", "count" to "50"), mapOf("count" to "100"))) {
      runCatching { steamSearch(params) }
        .onFailure { lastError = it }
        .getOrDefault(emptyList()).forEach { item ->
          val id = item.steamAppId ?: return@forEach
          if (!found.containsKey(id)) found[id] = item
        }
    }
    // Si Steam n'a rien renvoyé à cause d'une erreur, on la remonte au lieu d'afficher une liste vide
    if (found.isEmpty()) lastError?.let { throw it }
    val now = Instant.now()
    val recent = found.values.filter { item ->
      item.starts != null && ChronoUnit.DAYS.between(item.starts, now) <= maxAgeDays
    }
    val withPlayers = coroutineScope {
      recent.map { item ->
        async {
          val count = item.steamAppId?.let { steamPlayers(it) }
          item.copy(players = count)
        }
      }.awaitAll()
    }
    withPlayers.sortedByDescending { it.players ?: 0 }.take(limit)
  }

  /**
   * Sorties Steam des derniers jours pour le scan de l'actualité : les « nouveautés populaires »
   * de Steam, et les nouveaux free-to-play ayant au moins minF2pPlayers joueurs connectés
   * (pour écarter les dizaines de petits jeux gratuits publiés chaque jour).
   */
  suspend fun scanSteamReleases(db: DashupeurDb, maxAgeDays: Long = 3, minF2pPlayers: Int = 50): Int =
    withContext(Dispatchers.IO) {
      val now = Instant.now()
      fun recent(items: List<RadarItem>) =
        items.filter { it.starts != null && ChronoUnit.DAYS.between(it.starts, now) <= maxAgeDays }

      val found = linkedMapOf<String, RadarItem>()
      recent(steamSearch(mapOf("filter" to "popularnew", "count" to "50"), tags = null)).forEach { item ->
        item.steamAppId?.let { found[it] = item.copy(kind = "🎮 Nouveauté Steam populaire") }
      }
      val f2p = recent(steamSearch(mapOf("count" to "100"))).filter { it.steamAppId !in found }
      coroutineScope {
        f2p.map { item -> async { item.copy(players = item.steamAppId?.let { steamPlayers(it) }) } }.awaitAll()
      }.filter { (it.players ?: 0) >= minF2pPlayers }.forEach { found[it.steamAppId!!] = it }

      var added = 0
      for (item in found.values) {
        val newsId = radarNewsId(item)
        if (db.hasNews(newsId)) continue // déjà en base : inutile de redemander sa description à Steam
        val free = item.kind == "🆓 Nouveau Free-to-Play"
        val title = "Sortie Steam : ${item.title}" + if (free) " (free-to-play)" else ""
        val summary = buildSummary(item)
        val (score, tags) = NewsEngine.scoreArticle(title, summary)
        val cleanKind = item.kind.replace(Regex("""^\W+\s*"""), "")
        val inserted = db.upsertNews(
          NewsItem(
            id = newsId,
            title = title,
            url = item.url,
            source = "Steam · Nouveautés",
            published = item.starts.toString(),
            summary = summary,
            score = score,
            tags = (listOf(cleanKind) + tags).joinToString(", "),
            status = "inbox",
          )
        )
        if (inserted) added++
      }
      added
    }

  fun radarNewsId(item: RadarItem): String = HttpUtils.md5Hex(item.url)

  fun formatDeadline(item: RadarItem): String {
    val now = Instant.now()
    val localZone = ZoneId.systemDefault()
    val dayMonthFmt = DateTimeFormatter.ofPattern("dd/MM")
    val dayMonthTimeFmt = DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm")
    if (item.source != "Steam" && item.starts != null && item.starts.isAfter(now)) {
      val s = item.starts.atZone(localZone).format(dayMonthFmt)
      return if (item.ends != null) {
        val e = item.ends.atZone(localZone).format(dayMonthFmt)
        "📆 du $s au $e"
      } else {
        "📆 dès le $s"
      }
    }
    val ends = item.ends ?: return ""
    val secondsLeft = ChronoUnit.SECONDS.between(now, ends)
    if (secondsLeft < 86400) {
      val hours = (secondsLeft / 3600).coerceAtLeast(0)
      return "⏳ se termine dans $hours h"
    }
    return "⏳ jusqu'au ${ends.atZone(localZone).format(dayMonthTimeFmt)}"
  }

  /** Description Steam (si besoin), valeur, dates et joueurs connectés, en une phrase par info. */
  private fun buildSummary(item: RadarItem): String {
    val desc = item.summary.ifBlank {
      item.steamAppId?.let { steamDescription(it) }.orEmpty()
    }
    val parts = mutableListOf<String>()
    if (desc.isNotBlank()) parts.add(desc)
    if (item.worth.isNotBlank()) parts.add("Valeur : ${item.worth}.")
    val now = Instant.now()
    val zone = ZoneId.systemDefault()
    if (item.starts != null && item.starts.isAfter(now)) {
      parts.add("Gratuit à partir du ${item.starts.atZone(zone).format(DateTimeFormatter.ofPattern("dd/MM"))}.")
    }
    if (item.ends != null) {
      parts.add("Offre valable jusqu'au ${item.ends.atZone(zone).format(DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm"))}.")
    }
    if (item.players != null) {
      parts.add("${formatIntFr(item.players)} joueurs connectés sur Steam au moment de la veille.")
    }
    if (item.released.isNotBlank()) {
      parts.add("Sorti le ${item.released}.")
    }
    return parts.joinToString(" ")
  }

  suspend fun sendToHebdo(db: DashupeurDb, item: RadarItem, status: String): String = withContext(Dispatchers.IO) {
    val newsId = radarNewsId(item)
    val now = Instant.now()
    val summary = buildSummary(item)
    val (score, tags) = NewsEngine.scoreArticle(item.title, summary)
    val cleanKind = item.kind.replace(Regex("""^\W+\s*"""), "")
    db.upsertNews(
      NewsItem(
        id = newsId,
        title = item.title,
        url = item.url,
        source = "Radar · ${item.source}",
        published = (item.starts ?: now).toString(),
        summary = summary,
        score = score,
        tags = (listOf(cleanKind) + tags).joinToString(", "),
        status = status,
      )
    )
    db.updateStatus(newsId, status)
    newsId
  }

  fun formatIntFr(n: Int): String = "%,d".format(n).replace(',', ' ')
}
