package com.dashupeur.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dashupeur.app.data.RadarItem
import com.dashupeur.app.data.RadarRepository
import com.dashupeur.app.ui.DashupeurUiState
import com.dashupeur.app.ui.RadarSource
import com.dashupeur.app.ui.theme.ErrorColor
import com.dashupeur.app.ui.theme.ErrorContainer
import com.dashupeur.app.ui.theme.OnErrorContainer
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
fun RadarScreen(
  state: DashupeurUiState,
  onSelectSource: (RadarSource) -> Unit,
  onToggleGpKind: (String) -> Unit,
  onRefresh: () -> Unit,
  onSendToHebdo: (RadarItem, String) -> Unit,
) {
  val context = LocalContext.current
  val rawItems = when (state.radarSource) {
    RadarSource.EPIC -> state.epicItems
    RadarSource.GAMERPOWER -> state.gpItems.filter { it.kind in state.gpSelectedKinds }
    RadarSource.STEAM -> state.steamItems
  }
  val inHebdo = state.inHebdoMap

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "🎁 Radar Gratuits",
              style = MaterialTheme.typography.headlineMedium,
              color = OnSurface,
            )
            Text(
              text = "Offres 100% vérifiées • ${state.radarUpdatedAt?.let { "MàJ $it" } ?: "Temps réel"}",
              style = MaterialTheme.typography.bodySmall,
              color = Outline,
            )
          }
          Surface(
            color = SurfaceContainerHigh,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(40.dp).clickable(enabled = !state.isRadarLoading, onClick = onRefresh),
          ) {
            Box(contentAlignment = Alignment.Center) {
              if (state.isRadarLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PrimaryContainer)
              } else {
                Icon(Icons.Rounded.Sync, contentDescription = "Actualiser", tint = PrimaryContainer, modifier = Modifier.size(20.dp))
              }
            }
          }
        }

        // Segmented Source Switcher
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLowest)
            .padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          RadarSource.entries.forEach { src ->
            val selected = state.radarSource == src
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) PrimaryContainer else Color.Transparent)
                .clickable { onSelectSource(src) }
                .padding(vertical = 8.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = src.label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) OnPrimaryContainer else Outline,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
              )
            }
          }
        }

        if (state.radarSource == RadarSource.GAMERPOWER) {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("🎮 Jeu offert", "🧩 DLC offert", "🧪 Bêta / Playtest")) { kind ->
              val selected = kind in state.gpSelectedKinds
              FilterChip(
                selected = selected,
                onClick = { onToggleGpKind(kind) },
                label = { Text(kind, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = SurfaceContainerHighest,
                  selectedLabelColor = Secondary,
                  containerColor = SurfaceContainerLow,
                  labelColor = OnSurfaceVariant,
                ),
                border = null,
              )
            }
          }
        }
      }
    }

    if (state.radarError != null) {
      item {
        Surface(color = ErrorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Source indisponible : ${state.radarError}",
            style = MaterialTheme.typography.bodyMedium,
            color = OnErrorContainer,
            modifier = Modifier.padding(14.dp),
          )
        }
      }
    } else if (state.isRadarLoading && rawItems.isEmpty()) {
      item {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = PrimaryContainer)
        }
      }
    } else if (rawItems.isEmpty()) {
      item {
        Surface(color = SurfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Aucune offre active trouvée pour cette source.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(16.dp),
          )
        }
      }
    } else {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = when (state.radarSource) {
              RadarSource.EPIC -> "OFFRES EPIC GAMES"
              RadarSource.GAMERPOWER -> "GIVEAWAYS PC ACTIFS"
              RadarSource.STEAM -> "NOUVEAUX FREE-TO-PLAY"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = OnSurface,
          )
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(50))
              .background(SurfaceContainerHigh)
              .padding(horizontal = 10.dp, vertical = 4.dp)
          ) {
            Text(
              text = "${rawItems.size} résultat(s)",
              style = MaterialTheme.typography.labelSmall,
              color = Secondary,
            )
          }
        }
      }

      items(rawItems, key = { it.url + it.kind }) { item ->
        val newsId = RadarRepository.radarNewsId(item)
        RadarGameCard(
          item = item,
          hebdoStatus = inHebdo[newsId],
          onOpenUrl = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }
          },
          onMajor = { onSendToHebdo(item, "major") },
          onVrac = { onSendToHebdo(item, "vrac") },
        )
      }
    }
  }
}

@Composable
private fun RadarGameCard(
  item: RadarItem,
  hebdoStatus: String?,
  onOpenUrl: () -> Unit,
  onMajor: () -> Unit,
  onVrac: () -> Unit,
) {
  val deadline = RadarRepository.formatDeadline(item)
  Surface(
    color = SurfaceContainer,
    shape = RoundedCornerShape(16.dp),
    shadowElevation = 6.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column {
      if (item.image.isNotBlank()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clickable(onClick = onOpenUrl)
        ) {
          AsyncImage(
            model = item.image,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(
                Brush.verticalGradient(
                  colors = listOf(Color.Transparent, SurfaceContainer.copy(alpha = 0.25f), SurfaceContainer)
                )
              )
          )
          Row(
            modifier = Modifier
              .align(Alignment.TopStart)
              .padding(10.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(SurfaceContainerLowest.copy(alpha = 0.9f))
              .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(Icons.Rounded.Storefront, contentDescription = null, tint = Primary, modifier = Modifier.size(13.dp))
            Text(item.source.uppercase(), style = MaterialTheme.typography.labelSmall, color = OnSurface)
          }
          if (deadline.isNotBlank()) {
            Row(
              modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ErrorContainer.copy(alpha = 0.92f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(Icons.Rounded.Timer, contentDescription = null, tint = OnErrorContainer, modifier = Modifier.size(13.dp))
              Text(deadline, style = MaterialTheme.typography.labelSmall, color = OnErrorContainer)
            }
          }
        }
      }

      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = item.title,
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
            modifier = Modifier.clickable(onClick = onOpenUrl),
          )
          val metaParts = buildList {
            if (item.kind.isNotBlank()) add(item.kind)
            if (item.platforms.isNotBlank()) add(item.platforms)
            if (item.released.isNotBlank()) add("Sorti le ${item.released}")
          }
          Text(
            text = metaParts.joinToString(" • "),
            style = MaterialTheme.typography.bodySmall,
            color = Outline,
          )
        }

        // Value / Players strip
        if (item.worth.isNotBlank() || item.players != null || item.claimed != null) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(SurfaceContainerLow)
              .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (item.worth.isNotBlank()) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = item.worth,
                  style = MaterialTheme.typography.bodyMedium,
                  color = Outline,
                  textDecoration = TextDecoration.LineThrough,
                )
                Text("→", style = MaterialTheme.typography.labelMedium, color = Outline)
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(SecondaryContainer.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                  Text("GRATUIT (-100%)", style = MaterialTheme.typography.labelSmall, color = Secondary)
                }
              }
            } else if (item.players != null) {
              Text(
                text = "👥 ${RadarRepository.formatIntFr(item.players)} joueurs en ligne",
                style = MaterialTheme.typography.labelLarge,
                color = Secondary,
              )
            } else if (item.claimed != null) {
              Text(
                text = "🙋 ${RadarRepository.formatIntFr(item.claimed)} récupérés",
                style = MaterialTheme.typography.labelLarge,
                color = Tertiary,
              )
            }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(Icons.Rounded.Verified, contentDescription = null, tint = Secondary, modifier = Modifier.size(14.dp))
              Text("VÉRIFIÉ", style = MaterialTheme.typography.labelSmall, color = Secondary)
            }
          }
        }

        if (item.summary.isNotBlank()) {
          Text(
            text = item.summary,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
        }

        if (hebdoStatus != null) {
          Surface(
            color = SecondaryContainer.copy(alpha = 0.18f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(vertical = 10.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Secondary, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(
                text = if (hebdoStatus == "major") "Ajouté aux Gros Sujets" else "Ajouté en Vrac",
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
              modifier = Modifier.weight(1f).height(44.dp).clickable(onClick = onMajor),
            ) {
              Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = OnPrimaryContainer, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("Gros Sujet", style = MaterialTheme.typography.labelLarge, color = OnPrimaryContainer)
              }
            }
            Surface(
              color = SurfaceContainerHighest,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f).height(44.dp).clickable(onClick = onVrac),
            ) {
              Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Tertiary, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("En Vrac", style = MaterialTheme.typography.labelLarge, color = Tertiary)
              }
            }
          }
        }
      }
    }
  }
}
