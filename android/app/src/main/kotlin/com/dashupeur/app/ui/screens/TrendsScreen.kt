package com.dashupeur.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dashupeur.app.data.RadarItem
import com.dashupeur.app.data.RadarRepository
import com.dashupeur.app.data.TrendGame
import com.dashupeur.app.data.TrendPoint
import com.dashupeur.app.data.TrendsRepository
import com.dashupeur.app.ui.DashupeurUiState
import com.dashupeur.app.ui.theme.OnPrimaryContainer
import com.dashupeur.app.ui.theme.OnSurface
import com.dashupeur.app.ui.theme.OnSurfaceVariant
import com.dashupeur.app.ui.theme.Outline
import com.dashupeur.app.ui.theme.Primary
import com.dashupeur.app.ui.theme.PrimaryContainer
import com.dashupeur.app.ui.theme.Secondary
import com.dashupeur.app.ui.theme.SecondaryContainer
import com.dashupeur.app.ui.theme.SurfaceContainer
import com.dashupeur.app.ui.theme.SurfaceContainerHigh
import com.dashupeur.app.ui.theme.SurfaceContainerHighest
import com.dashupeur.app.ui.theme.SurfaceContainerLow
import com.dashupeur.app.ui.theme.SurfaceContainerLowest
import com.dashupeur.app.ui.theme.Tertiary

@Composable
fun TrendsScreen(
  state: DashupeurUiState,
  onSelectWindow: (Int) -> Unit,
  onSelectPlatform: (String?) -> Unit,
  onMinGrowthChange: (Int) -> Unit,
  onToggleMultiPlatform: () -> Unit,
  onCollectNow: () -> Unit,
  onSendToHebdo: (TrendGame, String) -> Unit,
) {
  val context = LocalContext.current
  val inHebdo = state.inHebdoMap

  val rising = state.trends.filter { game ->
    val meetsGrowth = game.score == null || game.score >= state.trendMinGrowth
    val meetsMulti = !state.multiPlatformOnly || game.sources.size >= 2
    meetsGrowth && meetsMulti
  }.take(30)

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Content Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Rounded.TrendingUp, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
              Text("Tendances Gaming", style = MaterialTheme.typography.headlineMedium, color = OnSurface)
            }
            Text("Multi-plateformes en direct", style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
          }

          Surface(
            color = SurfaceContainerHigh,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.clickable(enabled = !state.isCollectingTrends, onClick = onCollectNow),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              if (state.isCollectingTrends) {
                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = Primary)
              } else {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
              }
              Text(
                text = if (state.isCollectingTrends) "Relevé..." else "Relevé",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurface,
              )
            }
          }
        }

        // Telemetry Status Strip
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TelemetryPill("TWITCH", state.sourceLastTimes["twitch"] ?: "—", Tertiary, Modifier.weight(1f))
          TelemetryPill("STEAM", state.sourceLastTimes["steam"] ?: "—", OnSurface, Modifier.weight(1f))
          TelemetryPill("YT", state.sourceLastTimes["youtube"] ?: "—", Primary, Modifier.weight(1f))
        }

        // Filter & Timeframe Controls
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(SurfaceContainerLowest)
              .padding(3.dp),
          ) {
            TimeframeChip("24 h", state.trendWindowHours == 24) { onSelectWindow(24) }
            TimeframeChip("7 jours", state.trendWindowHours == 168) { onSelectWindow(168) }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceContainerHigh)
                .padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(Icons.Rounded.NorthEast, contentDescription = null, tint = Secondary, modifier = Modifier.size(14.dp))
              Text("+${state.trendMinGrowth}% min", style = MaterialTheme.typography.labelSmall, color = OnSurface)
            }

            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (state.multiPlatformOnly) PrimaryContainer else SurfaceContainerHighest)
                .clickable(onClick = onToggleMultiPlatform)
                .padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = if (state.multiPlatformOnly) OnPrimaryContainer else PrimaryContainer,
                modifier = Modifier.size(14.dp),
              )
              Text(
                "Multi",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.multiPlatformOnly) OnPrimaryContainer else OnSurface,
              )
            }
          }
        }

        // Plateforme : chaque source a son propre classement (spectateurs, vues et joueurs ne se comparent pas)
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLowest)
            .padding(3.dp),
        ) {
          TimeframeChip("Toutes", state.trendPlatform == null) { onSelectPlatform(null) }
          TimeframeChip("Twitch", state.trendPlatform == "twitch") { onSelectPlatform("twitch") }
          TimeframeChip("YouTube", state.trendPlatform == "youtube") { onSelectPlatform("youtube") }
          TimeframeChip("Steam", state.trendPlatform == "steam") { onSelectPlatform("steam") }
        }

        Slider(
          value = state.trendMinGrowth.toFloat(),
          onValueChange = { onMinGrowthChange(((it / 5).toInt()) * 5) },
          valueRange = 10f..200f,
          colors = SliderDefaults.colors(
            thumbColor = PrimaryContainer,
            activeTrackColor = PrimaryContainer,
            inactiveTrackColor = SurfaceContainerHighest,
          ),
          modifier = Modifier.height(24.dp),
        )
      }
    }

    if (state.trends.isEmpty()) {
      item {
        Surface(color = SurfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val period = if (state.trendWindowHours == 24) "24 h" else "7 jours"
            Text(
              text = "Pas encore assez d'historique pour la période $period.",
              style = MaterialTheme.typography.bodyMedium,
              color = OnSurface,
            )
            Text(
              text = "Voici le top du moment, source par source :",
              style = MaterialTheme.typography.bodySmall,
              color = OnSurfaceVariant,
            )
          }
        }
      }
      val anyTops = state.latestTops.values.any { it.isNotEmpty() }
      if (anyTops) {
        items(listOf("twitch", "steam", "youtube")) { src ->
          val rows = state.latestTops[src].orEmpty()
          if (rows.isNotEmpty()) {
            Surface(color = SurfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  text = "${TrendsRepository.SOURCE_LABELS[src]} · ${TrendsRepository.UNITS[src]}",
                  style = MaterialTheme.typography.titleMedium,
                  color = Tertiary,
                )
                rows.take(5).forEachIndexed { idx, row ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text(
                      text = "${idx + 1}. ${row.gameName}",
                      style = MaterialTheme.typography.bodySmall,
                      color = OnSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f),
                    )
                    Text(
                      text = RadarRepository.formatIntFr(row.value),
                      style = MaterialTheme.typography.labelMedium,
                      color = Secondary,
                    )
                  }
                }
              }
            }
          }
        }
      }
    } else {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
              modifier = Modifier
                .width(4.dp)
                .height(14.dp)
                .clip(CircleShape)
                .background(PrimaryContainer)
            )
            Text("Jeux en explosion", style = MaterialTheme.typography.headlineSmall, color = OnSurface)
          }
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(50))
              .background(SurfaceContainerLow)
              .padding(horizontal = 10.dp, vertical = 4.dp)
          ) {
            Text(
              text = "${rising.size} DÉTECTÉS",
              style = MaterialTheme.typography.labelSmall,
              color = Primary,
            )
          }
        }
      }

      if (rising.isEmpty()) {
        item {
          Surface(color = SurfaceContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "Aucun jeu ne dépasse +${state.trendMinGrowth} %. Baisse le curseur, change de plateforme ou désactive le filtre Multi.",
              style = MaterialTheme.typography.bodyMedium,
              color = OnSurfaceVariant,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
      } else {
        items(rising, key = { it.key }) { game ->
          val trendNewsId = RadarRepository.radarNewsId(
            RadarItem(title = game.name, url = game.url, source = "Tendances", kind = "📈 Tendance", platforms = "", image = game.image, summary = "")
          )
          TrendGameCard(
            game = game,
            history = state.trendHistories[game.key].orEmpty(),
            hebdoStatus = inHebdo[trendNewsId],
            onOpenUrl = {
              runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(game.url))) }
            },
            onMajor = { onSendToHebdo(game, "major") },
            onVrac = { onSendToHebdo(game, "vrac") },
          )
        }
      }
    }
  }
}

@Composable
private fun TelemetryPill(label: String, time: String, labelColor: Color, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(50))
      .background(SurfaceContainerLow)
      .padding(horizontal = 8.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (time == "—") Outline else Secondary))
    Spacer(Modifier.width(5.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor, maxLines = 1)
    Spacer(Modifier.width(4.dp))
    Text(time, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, maxLines = 1)
  }
}

@Composable
private fun TimeframeChip(text: String, selected: Boolean, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(9.dp))
      .background(if (selected) PrimaryContainer else Color.Transparent)
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 6.dp),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium,
      color = if (selected) OnPrimaryContainer else OnSurfaceVariant,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
    )
  }
}

@Composable
private fun TrendGameCard(
  game: TrendGame,
  history: Map<String, List<TrendPoint>>,
  hebdoStatus: String?,
  onOpenUrl: () -> Unit,
  onMajor: () -> Unit,
  onVrac: () -> Unit,
) {
  Surface(
    color = SurfaceContainer,
    shape = RoundedCornerShape(16.dp),
    shadowElevation = 6.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Top Utility Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = PrimaryContainer, modifier = Modifier.size(13.dp))
          Text(
            text = if (game.sources.size >= 2) "CROSS-PLATFORM x${game.sources.size}" else game.sources.keys.firstOrNull()?.uppercase() ?: "TREND",
            style = MaterialTheme.typography.labelSmall,
            color = Primary,
          )
        }
        val badgeText = if (game.score == null) "🆕 Nouveau venu" else "🚀 %+.0f %%".format(game.score)
        Text(
          text = badgeText,
          style = MaterialTheme.typography.labelMedium,
          color = Secondary,
          fontWeight = FontWeight.Bold,
        )
      }

      // Game Media & Title
      Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenUrl),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLowest),
          contentAlignment = Alignment.Center,
        ) {
          if (game.image.isNotBlank()) {
            AsyncImage(
              model = game.image,
              contentDescription = game.name,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            Icon(Icons.Rounded.TrendingUp, contentDescription = null, tint = Tertiary)
          }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(
            text = game.name,
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = game.sources.keys.joinToString(" • ") { TrendsRepository.SOURCE_LABELS[it] ?: it },
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // Metric Matrix
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        game.sources.entries.forEach { (src, metric) ->
          val color = when (src) {
            "twitch" -> Tertiary
            "youtube" -> Primary
            else -> OnSurface
          }
          Column(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(10.dp))
              .background(SurfaceContainerLow)
              .padding(horizontal = 8.dp, vertical = 6.dp),
          ) {
            Text(src.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
            Spacer(Modifier.height(2.dp))
            Text(
              text = formatCompactInt(metric.now),
              style = MaterialTheme.typography.titleMedium,
              color = OnSurface,
            )
            Text(
              text = if (metric.growth == null) "nouveau" else "%+.0f%%".format(metric.growth),
              style = MaterialTheme.typography.labelSmall,
              color = Secondary,
            )
          }
        }
      }

      // Sparkline Chart if history > 1 point
      val bestSeries = history.values.maxByOrNull { it.size }.orEmpty()
      if (bestSeries.size > 1) {
        SparklineChart(points = bestSeries, modifier = Modifier.fillMaxWidth().height(42.dp))
      }

      // Actions
      if (hebdoStatus != null) {
        Surface(
          color = SecondaryContainer.copy(alpha = 0.18f),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Secondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
              text = if (hebdoStatus == "major") "Dans Gros Sujets" else "Dans les Épluchures",
              style = MaterialTheme.typography.labelLarge,
              color = Secondary,
            )
          }
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            color = PrimaryContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f).height(42.dp).clickable(onClick = onMajor),
          ) {
            Row(
              modifier = Modifier.fillMaxSize(),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = OnPrimaryContainer, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Gros Sujet", style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), color = OnPrimaryContainer)
            }
          }
          Surface(
            color = SurfaceContainerHighest,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f).height(42.dp).clickable(onClick = onVrac),
          ) {
            Row(
              modifier = Modifier.fillMaxSize(),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Tertiary, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Épluchure", style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), color = Tertiary)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SparklineChart(points: List<TrendPoint>, modifier: Modifier = Modifier) {
  Canvas(
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .background(SurfaceContainerLow)
      .padding(horizontal = 8.dp, vertical = 6.dp)
  ) {
    if (points.size < 2) return@Canvas
    val minV = points.minOf { it.value }.toFloat()
    val maxV = points.maxOf { it.value }.toFloat()
    val range = (maxV - minV).coerceAtLeast(1f)
    val path = Path()
    points.forEachIndexed { idx, pt ->
      val x = idx.toFloat() / (points.size - 1) * size.width
      val y = size.height - ((pt.value - minV) / range) * size.height
      if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color = Tertiary, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    val lastX = size.width
    val lastY = size.height - ((points.last().value - minV) / range) * size.height
    drawCircle(color = Secondary, radius = 3.5.dp.toPx(), center = Offset(lastX, lastY))
  }
}

private fun formatCompactInt(n: Int): String = when {
  n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
  n >= 1_000 -> "%.1fk".format(n / 1_000.0)
  else -> n.toString()
}
