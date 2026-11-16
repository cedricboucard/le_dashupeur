package com.dashupeur.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dashupeur.app.data.ApiKeysConfig
import com.dashupeur.app.data.ApiKeysStore
import com.dashupeur.app.data.DashupeurDb
import com.dashupeur.app.data.HebdoExportResult
import com.dashupeur.app.data.NewsEngine
import com.dashupeur.app.data.NewsItem
import com.dashupeur.app.data.RadarItem
import com.dashupeur.app.data.RadarRepository
import com.dashupeur.app.data.SnapshotRow
import com.dashupeur.app.data.TrendGame
import com.dashupeur.app.data.TrendPoint
import com.dashupeur.app.data.TrendsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class MainTab(val label: String, val headerTitle: String) {
  ACTUS("Actus", "Actus Newsroom"),
  RADAR("Radar F2P", "Radar F2P"),
  TENDANCES("Tendances", "Tendances Gaming"),
  HEBDO("Hebdo", "Hebdo"),
}

enum class NewsSort(val label: String) {
  PERTINENCE("🔥 Pertinence"),
  RECENT("🕒 Récentes"),
  OLDEST("🕰️ Anciennes"),
}

enum class RadarSource(val label: String) {
  EPIC("Epic Games"),
  GAMERPOWER("GamerPower"),
  STEAM("Steam F2P"),
}

data class DashupeurUiState(
  val currentTab: MainTab = MainTab.TENDANCES,
  val flashMessage: String? = null,

  // Actus / Inbox
  val inboxItems: List<NewsItem> = emptyList(),
  val majorItems: List<NewsItem> = emptyList(),
  val vracItems: List<NewsItem> = emptyList(),
  val searchQuery: String = "",
  val sortBy: NewsSort = NewsSort.PERTINENCE,
  val minScore: Int = 0,
  val isScanningFeeds: Boolean = false,

  // Radar
  val radarSource: RadarSource = RadarSource.EPIC,
  val epicItems: List<RadarItem> = emptyList(),
  val gpItems: List<RadarItem> = emptyList(),
  val steamItems: List<RadarItem> = emptyList(),
  val gpSelectedKinds: Set<String> = setOf("🎮 Jeu offert", "🧩 DLC offert", "🧪 Bêta / Playtest"),
  val isRadarLoading: Boolean = false,
  val radarError: String? = null,
  val radarUpdatedAt: String? = null,

  // Tendances
  val trendWindowHours: Int = 24,
  val trendMinGrowth: Int = 25,
  val multiPlatformOnly: Boolean = false,
  val trends: List<TrendGame> = emptyList(),
  val trendHistories: Map<String, Map<String, List<TrendPoint>>> = emptyMap(),
  val latestTops: Map<String, List<SnapshotRow>> = emptyMap(),
  val sourceLastTimes: Map<String, String> = emptyMap(),
  val isCollectingTrends: Boolean = false,
  val apiKeys: ApiKeysConfig = ApiKeysConfig(),
  val showApiKeysDialog: Boolean = false,

  // Hebdo
  val hebdoExport: HebdoExportResult? = null,
) {
  val inHebdoMap: Map<String, String>
    get() = majorItems.associate { it.id to "major" } + vracItems.associate { it.id to "vrac" }
}

class DashupeurViewModel(app: Application) : AndroidViewModel(app) {
  private val db = DashupeurDb(app)
  private val _state = MutableStateFlow(DashupeurUiState())
  val state: StateFlow<DashupeurUiState> = _state.asStateFlow()

  private val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

  init {
    _state.update { it.copy(apiKeys = ApiKeysStore.load(app)) }
    reloadAllFromDb()
    loadRadar(RadarSource.EPIC, forceRefresh = false)
  }

  fun openApiKeysDialog() = _state.update { it.copy(showApiKeysDialog = true) }
  fun closeApiKeysDialog() = _state.update { it.copy(showApiKeysDialog = false) }
  fun saveApiKeys(keys: ApiKeysConfig) {
    ApiKeysStore.save(getApplication(), keys)
    _state.update {
      it.copy(
        apiKeys = keys,
        showApiKeysDialog = false,
        flashMessage = "Clés API enregistrées sur l'appareil.",
      )
    }
  }

  fun selectTab(tab: MainTab) {
    _state.update { it.copy(currentTab = tab, flashMessage = null) }
    if (tab == MainTab.RADAR) {
      loadRadar(_state.value.radarSource, forceRefresh = false)
    }
  }

  fun clearFlash() {
    _state.update { it.copy(flashMessage = null) }
  }

  // --- ACTUS / NEWS ---

  fun setSearchQuery(q: String) = _state.update { it.copy(searchQuery = q) }
  fun setSortBy(sort: NewsSort) = _state.update { it.copy(sortBy = sort) }
  fun setMinScore(score: Int) {
    _state.update { it.copy(minScore = score) }
    reloadNewsFromDb()
  }

  fun scanFeeds() {
    if (_state.value.isScanningFeeds) return
    _state.update { it.copy(isScanningFeeds = true, flashMessage = null) }
    viewModelScope.launch {
      val (added, errors) = NewsEngine.fetchAllFeeds(db)
      reloadNewsFromDb()
      val msg = if (errors.isEmpty()) {
        "Scan terminé : $added nouvelle(s) news."
      } else {
        "Scan terminé : $added news (${errors.size} flux en erreur)."
      }
      _state.update { it.copy(isScanningFeeds = false, flashMessage = msg) }
    }
  }

  fun clearInbox() {
    viewModelScope.launch(Dispatchers.IO) {
      db.clearInbox()
      reloadNewsFromDb()
      _state.update { it.copy(flashMessage = "Boîte de réception vidée.") }
    }
  }

  fun updateNewsStatus(newsId: String, newStatus: String, title: String? = null) {
    viewModelScope.launch(Dispatchers.IO) {
      db.updateStatus(newsId, newStatus)
      reloadNewsFromDb()
      if (title != null) {
        val label = when (newStatus) {
          "major" -> "Gros Sujets"
          "vrac" -> "News en Vrac"
          "inbox" -> "Boîte de réception"
          else -> "Ignorés"
        }
        _state.update { it.copy(flashMessage = "« $title » déplacé vers $label.") }
      }
    }
  }

  // --- RADAR ---

  fun selectRadarSource(source: RadarSource) {
    _state.update { it.copy(radarSource = source, radarError = null) }
    loadRadar(source, forceRefresh = false)
  }

  fun toggleGpKind(kind: String) {
    _state.update { st ->
      val next = st.gpSelectedKinds.toMutableSet()
      if (!next.add(kind) && next.size > 1) next.remove(kind)
      st.copy(gpSelectedKinds = next)
    }
  }

  fun loadRadar(source: RadarSource = _state.value.radarSource, forceRefresh: Boolean = true) {
    val st = _state.value
    val alreadyLoaded = when (source) {
      RadarSource.EPIC -> st.epicItems.isNotEmpty()
      RadarSource.GAMERPOWER -> st.gpItems.isNotEmpty()
      RadarSource.STEAM -> st.steamItems.isNotEmpty()
    }
    if (!forceRefresh && alreadyLoaded) return

    _state.update { it.copy(isRadarLoading = true, radarError = null) }
    viewModelScope.launch {
      runCatching {
        when (source) {
          RadarSource.EPIC -> RadarRepository.fetchEpicFree()
          RadarSource.GAMERPOWER -> RadarRepository.fetchGamerPower()
          RadarSource.STEAM -> RadarRepository.fetchSteamF2p()
        }
      }.onSuccess { items ->
        val nowStr = timeFmt.format(java.time.Instant.now())
        _state.update { cur ->
          when (source) {
            RadarSource.EPIC -> cur.copy(epicItems = items, isRadarLoading = false, radarUpdatedAt = nowStr)
            RadarSource.GAMERPOWER -> cur.copy(gpItems = items, isRadarLoading = false, radarUpdatedAt = nowStr)
            RadarSource.STEAM -> cur.copy(steamItems = items, isRadarLoading = false, radarUpdatedAt = nowStr)
          }
        }
      }.onFailure { e ->
        _state.update { it.copy(isRadarLoading = false, radarError = e.message ?: "Source indisponible") }
      }
    }
  }

  fun sendRadarToHebdo(item: RadarItem, status: String) {
    viewModelScope.launch {
      RadarRepository.sendToHebdo(db, item, status)
      reloadNewsFromDb()
      val where = if (status == "major") "Gros Sujets" else "News en Vrac"
      _state.update { it.copy(flashMessage = "« ${item.title} » ajouté aux $where de L'Hebdo.") }
    }
  }

  // --- TENDANCES ---

  fun setTrendWindow(hours: Int) {
    _state.update { it.copy(trendWindowHours = hours) }
    reloadTrendsFromDb()
  }

  fun setTrendMinGrowth(minGrowth: Int) {
    _state.update { it.copy(trendMinGrowth = minGrowth) }
  }

  fun toggleMultiPlatformOnly() {
    _state.update { it.copy(multiPlatformOnly = !it.multiPlatformOnly) }
  }

  fun collectSteamTrendsNow() {
    if (_state.value.isCollectingTrends) return
    _state.update { it.copy(isCollectingTrends = true, flashMessage = null) }
    viewModelScope.launch {
      runCatching {
        TrendsRepository.collectAll(db, _state.value.apiKeys)
      }.onSuccess { report ->
        reloadTrendsFromDb()
        val summary = report.entries.joinToString(" | ") { (src, res) ->
          val label = TrendsRepository.SOURCE_LABELS[src] ?: src
          "$label : $res"
        }
        _state.update {
          it.copy(
            isCollectingTrends = false,
            flashMessage = "Relevé terminé — $summary",
          )
        }
      }.onFailure { e ->
        _state.update {
          it.copy(
            isCollectingTrends = false,
            flashMessage = "Erreur relevé : ${e.message}",
          )
        }
      }
    }
  }

  fun sendTrendToHebdo(game: TrendGame, status: String) {
    viewModelScope.launch {
      TrendsRepository.sendTrendToHebdo(db, game, _state.value.trendWindowHours, status)
      reloadNewsFromDb()
      val where = if (status == "major") "Gros Sujets" else "News en Vrac"
      _state.update { it.copy(flashMessage = "« ${game.name} » ajouté aux $where de L'Hebdo.") }
    }
  }

  // --- HEBDO ---

  fun moveMajorItem(index: Int, delta: Int) {
    val list = _state.value.majorItems.toMutableList()
    val target = index + delta
    if (index !in list.indices || target !in list.indices) return
    val item = list.removeAt(index)
    list.add(target, item)
    _state.update { it.copy(majorItems = list) }
  }

  fun generateHebdoPack() {
    val st = _state.value
    if (st.majorItems.isEmpty() && st.vracItems.isEmpty()) return
    val result = NewsEngine.exportHebdo(getApplication(), st.majorItems, st.vracItems)
    _state.update { it.copy(hebdoExport = result) }
  }

  fun dismissHebdoExport() {
    _state.update { it.copy(hebdoExport = null) }
  }

  // --- DB LOADERS ---

  private fun reloadAllFromDb() {
    reloadNewsFromDb()
    reloadTrendsFromDb()
  }

  private fun reloadNewsFromDb() {
    viewModelScope.launch(Dispatchers.IO) {
      val minScore = _state.value.minScore
      val inbox = db.getNews(status = "inbox", minScore = minScore)
      val major = db.getNews(status = "major", minScore = 0)
      val vrac = db.getNews(status = "vrac", minScore = 0)
      _state.update { it.copy(inboxItems = inbox, majorItems = major, vracItems = vrac) }
    }
  }

  private fun reloadTrendsFromDb() {
    viewModelScope.launch {
      val windowHours = _state.value.trendWindowHours
      val data = withContext(Dispatchers.IO) {
        val trends = TrendsRepository.computeTrends(db, windowHours)
        val histories = trends.take(30).associate { it.key to db.history(it.key, days = 7) }
        val lastTimes = listOf("twitch", "steam", "youtube").associateWith { src ->
          val times = db.snapshotTimes(src)
          if (times.isNotEmpty()) timeFmt.format(times.last().instant) else "—"
        }
        val tops = listOf("twitch", "steam", "youtube").associateWith { src ->
          TrendsRepository.latestSnapshot(db, listOf(src), limit = 10)
        }
        Quad(trends, histories, lastTimes, tops)
      }
      _state.update {
        it.copy(
          trends = data.first,
          trendHistories = data.second,
          sourceLastTimes = data.third,
          latestTops = data.fourth,
        )
      }
    }
  }

  private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
