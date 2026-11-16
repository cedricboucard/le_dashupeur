package com.dashupeur.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

data class ApiKeysConfig(
  val twitchClientId: String = "",
  val twitchClientSecret: String = "",
  val youtubeApiKey: String = "",
) {
  val hasTwitch: Boolean get() = twitchClientId.isNotBlank() && twitchClientSecret.isNotBlank()
  val hasYoutube: Boolean get() = youtubeApiKey.isNotBlank()
}

object ApiKeysStore {
  private const val PREFS = "dashupeur_api_keys"
  private const val KEY_TWITCH_ID = "twitch_client_id"
  private const val KEY_TWITCH_SECRET = "twitch_client_secret"
  private const val KEY_YOUTUBE = "youtube_api_key"

  fun load(context: Context): ApiKeysConfig {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return ApiKeysConfig(
      twitchClientId = prefs.getString(KEY_TWITCH_ID, "").orEmpty().trim(),
      twitchClientSecret = prefs.getString(KEY_TWITCH_SECRET, "").orEmpty().trim(),
      youtubeApiKey = prefs.getString(KEY_YOUTUBE, "").orEmpty().trim(),
    )
  }

  fun save(context: Context, cfg: ApiKeysConfig) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putString(KEY_TWITCH_ID, cfg.twitchClientId.trim())
      .putString(KEY_TWITCH_SECRET, cfg.twitchClientSecret.trim())
      .putString(KEY_YOUTUBE, cfg.youtubeApiKey.trim())
      .apply()
  }
}

object TrendsRepository {
  private const val STEAM_TOP_PLAYERS_URL = "https://api.steampowered.com/ISteamChartsService/GetMostPlayedGames/v1/"
  private const val STEAM_ITEMS_URL = "https://api.steampowered.com/IStoreBrowseService/GetItems/v1/"
  private const val STEAM_PLAYERS_URL = "https://api.steampowered.com/ISteamUserStats/GetNumberOfCurrentPlayers/v1/"
  private const val TWITCH_TOP_PAGES = 2
  private const val YOUTUBE_MAX_GAMES = 40
  private const val YOUTUBE_MIN_INTERVAL_H = 20L

  val MIN_VALUES = mapOf("twitch" to 300, "steam" to 500, "youtube" to 20_000)
  val SOURCE_LABELS = mapOf("twitch" to "🟣 Twitch", "steam" to "🎮 Steam", "youtube" to "🔴 YouTube")
  val UNITS = mapOf("twitch" to "spectateurs", "steam" to "joueurs", "youtube" to "vues (7 j)")

  fun gameKey(name: String): String {
    val noSymbols = name.replace(Regex("[™®©]"), "")
    val normalized = Normalizer.normalize(noSymbols, Normalizer.Form.NFKD)
      .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
      .lowercase()
    return normalized.replace(Regex("[^a-z0-9]+"), " ").trim()
  }

  private fun baselineTime(
    times: List<DashupeurDb.SnapshotTime>,
    windowHours: Int,
  ): DashupeurDb.SnapshotTime? {
    if (times.size < 2) return null
    val latest = times.last().instant
    val target = latest.minus(windowHours.toLong(), ChronoUnit.HOURS)
    val toleranceMinutes = max(2.0, windowHours / 4.0) * 60.0
    val best = times.dropLast(1).minByOrNull { abs(ChronoUnit.MINUTES.between(it.instant, target)) }
      ?: return null
    val diffMinutes = abs(ChronoUnit.MINUTES.between(best.instant, target)).toDouble()
    return if (diffMinutes <= toleranceMinutes) best else null
  }

  fun computeTrends(
    db: DashupeurDb,
    windowHours: Int = 24,
    sources: List<String> = listOf("twitch", "steam", "youtube"),
  ): List<TrendGame> {
    val games = linkedMapOf<String, MutableTrendGame>()
    for (source in sources) {
      val times = db.snapshotTimes(source)
      val baseTime = baselineTime(times, windowHours) ?: continue
      val nowVals = db.valuesAt(source, times.last().raw)
      val baseVals = db.valuesAt(source, baseTime.raw)
      val minThreshold = MIN_VALUES[source] ?: 0

      for ((key, row) in nowVals) {
        if (row.value < minThreshold) continue
        val before = baseVals[key]?.value
        val growth = if (before == null) null else ((row.value - before).toDouble() / max(before, 1)) * 100.0
        val existing = games.getOrPut(key) {
          MutableTrendGame(
            key = key,
            name = row.gameName,
            url = row.url,
            image = row.image,
          )
        }
        if (existing.image.isBlank() && row.image.isNotBlank()) existing.image = row.image
        if (existing.url.isBlank()) {
          existing.url = row.url.ifBlank {
            "https://store.steampowered.com/search/?term=${URLEncoder.encode(row.gameName, "UTF-8")}"
          }
        }
        existing.sources[source] = TrendSourceMetric(now = row.value, before = before, growth = growth)
        if (growth == null) {
          existing.isNew = true
        } else if (existing.score == null || growth > existing.score!!) {
          existing.score = growth
        }
      }
    }

    return games.values
      .map { it.toImmutable() }
      .sortedWith(
        compareByDescending<TrendGame> { it.isNew && it.score == null }
          .thenByDescending { it.score ?: 0.0 }
      )
  }

  fun latestSnapshot(
    db: DashupeurDb,
    sources: List<String> = listOf("twitch", "steam"),
    limit: Int = 10,
  ): List<SnapshotRow> {
    val rows = mutableListOf<SnapshotRow>()
    for (source in sources) {
      val times = db.snapshotTimes(source)
      if (times.isNotEmpty()) {
        rows.addAll(db.valuesAt(source, times.last().raw).values)
      }
    }
    rows.sortByDescending { it.value }
    val seen = hashSetOf<String>()
    val unique = mutableListOf<SnapshotRow>()
    for (row in rows) {
      if (seen.add(row.gameKey)) {
        unique.add(row)
        if (unique.size >= limit) break
      }
    }
    return unique
  }

  // --- COLLECTE : STEAM ---

  private fun steamNames(appIds: List<Int>): Map<Int, String> {
    val names = mutableMapOf<Int, String>()
    for (chunk in appIds.chunked(50)) {
      val idsJson = chunk.joinToString(",") { """{"appid":$it}""" }
      val inputJson = """{"ids":[$idsJson],"context":{"language":"english","country_code":"FR"}}"""
      val body = HttpUtils.getText(STEAM_ITEMS_URL, mapOf("input_json" to inputJson))
      val items = JSONObject(body).optJSONObject("response")?.optJSONArray("store_items") ?: continue
      for (i in 0 until items.length()) {
        val item = items.getJSONObject(i)
        val name = item.optString("name", "")
        if (name.isNotBlank()) names[item.optInt("appid")] = name
      }
    }
    return names
  }

  private fun steamPlayerCount(appid: Int): Int? = runCatching {
    val body = HttpUtils.getText(STEAM_PLAYERS_URL, mapOf("appid" to appid.toString()), timeoutMs = 8000)
    JSONObject(body).getJSONObject("response").optInt("player_count")
  }.getOrNull()

  suspend fun collectSteam(): List<SnapshotRow> = withContext(Dispatchers.IO) {
    val body = HttpUtils.getText(STEAM_TOP_PLAYERS_URL)
    val ranks = JSONObject(body).getJSONObject("response").getJSONArray("ranks")
    val appIds = mutableListOf<Int>()
    for (i in 0 until ranks.length()) {
      appIds.add(ranks.getJSONObject(i).getInt("appid"))
    }
    val names = steamNames(appIds)
    val validIds = appIds.filter { names.containsKey(it) }
    val counts = coroutineScope {
      validIds.map { id -> async { id to steamPlayerCount(id) } }.awaitAll().toMap()
    }
    val takenAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
    validIds.mapNotNull { id ->
      val players = counts[id] ?: return@mapNotNull null
      val name = names[id] ?: return@mapNotNull null
      SnapshotRow(
        takenAt = takenAt,
        source = "steam",
        gameKey = gameKey(name),
        gameName = name,
        value = players,
        url = "https://store.steampowered.com/app/$id/",
        image = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$id/header.jpg",
      )
    }
  }

  // --- COLLECTE : TWITCH ---

  suspend fun collectTwitch(keys: ApiKeysConfig): List<SnapshotRow> = withContext(Dispatchers.IO) {
    val tokenBody = HttpUtils.postForm(
      "https://id.twitch.tv/oauth2/token",
      mapOf(
        "client_id" to keys.twitchClientId,
        "client_secret" to keys.twitchClientSecret,
        "grant_type" to "client_credentials",
      ),
    )
    val accessToken = JSONObject(tokenBody).getString("access_token")
    val headers = mapOf(
      "Client-Id" to keys.twitchClientId,
      "Authorization" to "Bearer $accessToken",
    )

    data class TwitchGame(val id: String, val name: String, val boxArt: String)
    val games = mutableListOf<TwitchGame>()
    var cursor: String? = null
    for (page in 0 until TWITCH_TOP_PAGES) {
      val params = linkedMapOf("first" to "100")
      if (!cursor.isNullOrBlank()) params["after"] = cursor
      val topJson = JSONObject(HttpUtils.getText("https://api.twitch.tv/helix/games/top", params, headers))
      val data = topJson.optJSONArray("data") ?: break
      for (i in 0 until data.length()) {
        val g = data.getJSONObject(i)
        if (g.optString("igdb_id").isBlank()) continue
        games.add(
          TwitchGame(
            id = g.getString("id"),
            name = g.getString("name"),
            boxArt = g.optString("box_art_url", "").replace("{width}x{height}", "144x192"),
          )
        )
      }
      cursor = topJson.optJSONObject("pagination")?.optString("cursor")?.takeIf { it.isNotBlank() } ?: break
    }

    val viewersList = coroutineScope {
      games.map { game ->
        async {
          runCatching {
            val streamsJson = JSONObject(
              HttpUtils.getText(
                "https://api.twitch.tv/helix/streams",
                mapOf("game_id" to game.id, "first" to "100", "type" to "live"),
                headers,
              )
            )
            val arr = streamsJson.optJSONArray("data") ?: return@runCatching 0
            var total = 0
            for (i in 0 until arr.length()) {
              total += arr.getJSONObject(i).optInt("viewer_count", 0)
            }
            total
          }.getOrDefault(0)
        }
      }.awaitAll()
    }

    val takenAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
    games.zip(viewersList).map { (g, count) ->
      SnapshotRow(
        takenAt = takenAt,
        source = "twitch",
        gameKey = gameKey(g.name),
        gameName = g.name,
        value = count,
        url = "https://www.twitch.tv/directory/game/${URLEncoder.encode(g.name, "UTF-8").replace("+", "%20")}",
        image = g.boxArt,
      )
    }
  }

  // --- COLLECTE : YOUTUBE ---

  private fun youtubeCandidates(db: DashupeurDb): List<String> {
    val trends = computeTrends(db, 24, listOf("twitch", "steam"))
    val rising = trends.filter { it.score != null }.take(25).map { it.name }
    val biggest = latestSnapshot(db, listOf("twitch", "steam"), limit = YOUTUBE_MAX_GAMES).map { it.gameName }
    val unique = linkedMapOf<String, String>()
    for (name in rising + biggest) {
      unique.putIfAbsent(gameKey(name), name)
    }
    return unique.values.take(YOUTUBE_MAX_GAMES)
  }

  suspend fun collectYoutube(db: DashupeurDb, keys: ApiKeysConfig): List<SnapshotRow> = withContext(Dispatchers.IO) {
    val since = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
      .withZone(ZoneOffset.UTC)
      .format(Instant.now().minus(7, ChronoUnit.DAYS))
    val takenAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
    val candidates = youtubeCandidates(db)
    val results = mutableListOf<SnapshotRow>()
    for (name in candidates) {
      val searchJson = JSONObject(
        HttpUtils.getText(
          "https://www.googleapis.com/youtube/v3/search",
          mapOf(
            "key" to keys.youtubeApiKey,
            "part" to "id",
            "type" to "video",
            "q" to name,
            "videoCategoryId" to "20",
            "publishedAfter" to since,
            "order" to "viewCount",
            "maxResults" to "25",
          ),
        )
      )
      val items = searchJson.optJSONArray("items")
      val ids = mutableListOf<String>()
      if (items != null) {
        for (i in 0 until items.length()) {
          val vid = items.getJSONObject(i).optJSONObject("id")?.optString("videoId").orEmpty()
          if (vid.isNotBlank()) ids.add(vid)
        }
      }
      var views = 0
      if (ids.isNotEmpty()) {
        val statsJson = JSONObject(
          HttpUtils.getText(
            "https://www.googleapis.com/youtube/v3/videos",
            mapOf(
              "key" to keys.youtubeApiKey,
              "part" to "statistics",
              "id" to ids.joinToString(","),
            ),
          )
        )
        val statItems = statsJson.optJSONArray("items")
        if (statItems != null) {
          for (i in 0 until statItems.length()) {
            views += statItems.getJSONObject(i)
              .optJSONObject("statistics")
              ?.optString("viewCount", "0")
              ?.toIntOrNull() ?: 0
          }
        }
      }
      results.add(
        SnapshotRow(
          takenAt = takenAt,
          source = "youtube",
          gameKey = gameKey(name),
          gameName = name,
          value = views,
          url = "https://www.youtube.com/results?search_query=${URLEncoder.encode(name, "UTF-8")}&sp=CAMSAhAB",
          image = "",
        )
      )
    }
    results
  }

  suspend fun collectAll(db: DashupeurDb, keys: ApiKeysConfig): Map<String, String> {
    val takenAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
    val report = linkedMapOf<String, String>()

    runCatching { collectSteam() }
      .onSuccess { rows ->
        withContext(Dispatchers.IO) { db.saveSnapshot("steam", takenAt, rows) }
        report["steam"] = "${rows.size} jeux"
      }
      .onFailure { e -> report["steam"] = "erreur (${e.message})" }

    if (keys.hasTwitch) {
      runCatching { collectTwitch(keys) }
        .onSuccess { rows ->
          withContext(Dispatchers.IO) { db.saveSnapshot("twitch", takenAt, rows) }
          report["twitch"] = "${rows.size} jeux"
        }
        .onFailure { e -> report["twitch"] = "erreur (${e.message})" }
    }

    if (keys.hasYoutube) {
      val lastYt = withContext(Dispatchers.IO) { db.snapshotTimes("youtube").lastOrNull()?.instant }
      if (lastYt != null && ChronoUnit.HOURS.between(lastYt, Instant.now()) < YOUTUBE_MIN_INTERVAL_H) {
        report["youtube"] = "déjà relevé aujourd'hui (quota)"
      } else {
        runCatching { collectYoutube(db, keys) }
          .onSuccess { rows ->
            withContext(Dispatchers.IO) { db.saveSnapshot("youtube", takenAt, rows) }
            report["youtube"] = "${rows.size} jeux"
          }
          .onFailure { e -> report["youtube"] = "erreur (${e.message})" }
      }
    }

    return report
  }

  fun trendSummary(game: TrendGame, windowHours: Int): String {
    val period = if (windowHours == 24) "24 h" else "${windowHours / 24} jours"
    val parts = game.sources.entries.map { (source, metric) ->
      val label = SOURCE_LABELS[source]?.substringAfter(" ") ?: source
      val nowStr = RadarRepository.formatIntFr(metric.now)
      if (metric.growth == null) {
        "$label : nouveau venu avec $nowStr ${UNITS[source]}"
      } else {
        "$label : $nowStr ${UNITS[source]} (%+.0f %% en $period)".format(metric.growth)
      }
    }
    return "Jeu en forte progression. ${parts.joinToString(" ; ")}."
  }

  suspend fun sendTrendToHebdo(
    db: DashupeurDb,
    game: TrendGame,
    windowHours: Int,
    status: String,
  ): String {
    val radarItem = RadarItem(
      title = game.name,
      url = game.url,
      source = "Tendances",
      kind = "📈 Tendance",
      platforms = "",
      image = game.image,
      summary = trendSummary(game, windowHours),
    )
    return RadarRepository.sendToHebdo(db, radarItem, status)
  }

  private class MutableTrendGame(
    val key: String,
    val name: String,
    var url: String,
    var image: String,
    val sources: LinkedHashMap<String, TrendSourceMetric> = linkedMapOf(),
    var score: Double? = null,
    var isNew: Boolean = false,
  ) {
    fun toImmutable() = TrendGame(key, name, url, image, sources, score, isNew)
  }
}
