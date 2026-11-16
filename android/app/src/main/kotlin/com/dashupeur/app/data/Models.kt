package com.dashupeur.app.data

import java.time.Instant

data class NewsItem(
  val id: String,
  val title: String,
  val url: String,
  val source: String,
  val published: String,
  val summary: String,
  val score: Int,
  val tags: String,
  val status: String,
  val createdAt: String = "",
)

data class RadarItem(
  val title: String,
  val url: String,
  val source: String,
  val kind: String,
  val platforms: String,
  val image: String,
  val summary: String,
  val starts: Instant? = null,
  val ends: Instant? = null,
  val worth: String = "",
  val players: Int? = null,
  val released: String = "",
  val claimed: Int? = null,
  val steamAppId: String? = null,
)

data class TrendSourceMetric(
  val now: Int,
  val before: Int?,
  val growth: Double?,
)

data class TrendGame(
  val key: String,
  val name: String,
  val url: String,
  val image: String,
  val sources: Map<String, TrendSourceMetric>,
  val score: Double?,
  val isNew: Boolean,
)

data class SnapshotRow(
  val takenAt: String,
  val source: String,
  val gameKey: String,
  val gameName: String,
  val value: Int,
  val url: String,
  val image: String,
)

data class TrendPoint(
  val takenAt: Instant,
  val value: Int,
)

data class HebdoExportResult(
  val folderPath: String,
  val scriptMarkdown: String,
  val youtubeDescription: String,
)
