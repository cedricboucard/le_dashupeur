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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dashupeur.app.data.Config
import com.dashupeur.app.data.NewsEngine
import com.dashupeur.app.data.NewsItem
import com.dashupeur.app.ui.DashupeurUiState
import com.dashupeur.app.ui.NewsSort
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
fun ActusScreen(
  state: DashupeurUiState,
  onSearchChange: (String) -> Unit,
  onSortChange: (NewsSort) -> Unit,
  onMinScoreChange: (Int) -> Unit,
  onScanFeeds: () -> Unit,
  onClearInbox: () -> Unit,
  onUpdateStatus: (String, String, String) -> Unit,
  onTogglePick: (NewsItem) -> Unit,
) {
  val context = LocalContext.current
  val filtered = remember(state.inboxItems, state.searchQuery, state.sortBy) {
    val q = state.searchQuery.trim().lowercase()
    val base = if (q.isEmpty()) {
      state.inboxItems
    } else {
      state.inboxItems.filter {
        (it.title + " " + it.summary + " " + it.tags).lowercase().contains(q)
      }
    }
    if (state.sortBy == NewsSort.PERTINENCE) {
      base
    } else {
      val dated = base.filter { NewsEngine.parsePublished(it.published) != null }
      val undated = base.filter { NewsEngine.parsePublished(it.published) == null }
      val sorted = if (state.sortBy == NewsSort.RECENT) {
        dated.sortedByDescending { NewsEngine.parsePublished(it.published) }
      } else {
        dated.sortedBy { NewsEngine.parsePublished(it.published) }
      }
      sorted + undated
    }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "📥 Boîte de réception",
              style = MaterialTheme.typography.headlineMedium,
              color = OnSurface,
            )
            Text(
              text = "${state.inboxItems.size} à trier · 🔥 ${state.majorItems.size} gros · ⚡ ${state.vracItems.size} vrac",
              style = MaterialTheme.typography.bodySmall,
              color = OnSurfaceVariant,
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
              color = SurfaceContainerHigh,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.clickable(onClick = onClearInbox),
            ) {
              Icon(
                imageVector = Icons.Rounded.DeleteSweep,
                contentDescription = "Vider",
                tint = Outline,
                modifier = Modifier.padding(10.dp).size(20.dp),
              )
            }
            Button(
              onClick = onScanFeeds,
              enabled = !state.isScanningFeeds,
              colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryContainer,
                contentColor = OnPrimaryContainer,
              ),
              shape = RoundedCornerShape(12.dp),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
              if (state.isScanningFeeds) {
                CircularProgressIndicator(
                  modifier = Modifier.size(16.dp),
                  strokeWidth = 2.dp,
                  color = OnPrimaryContainer,
                )
              } else {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              }
              Spacer(Modifier.width(6.dp))
              Text(
                text = if (state.isScanningFeeds) "Scan..." else "Scanner",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }

        OutlinedTextField(
          value = state.searchQuery,
          onValueChange = onSearchChange,
          placeholder = {
            Text("Filtrer par mot-clé (MMO, Steam, gratuit...)", style = MaterialTheme.typography.bodySmall)
          },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Outline) },
          trailingIcon = {
            if (state.searchQuery.isNotEmpty()) {
              IconButton(onClick = { onSearchChange("") }) {
                Icon(Icons.Rounded.Close, contentDescription = "Effacer", tint = Outline)
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SurfaceContainerLowest,
            unfocusedContainerColor = SurfaceContainerLowest,
            focusedBorderColor = PrimaryContainer,
            unfocusedBorderColor = SurfaceContainerHighest,
            focusedTextColor = OnSurface,
            unfocusedTextColor = OnSurface,
          ),
          modifier = Modifier.fillMaxWidth(),
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(NewsSort.entries) { sort ->
            val selected = state.sortBy == sort
            FilterChip(
              selected = selected,
              onClick = { onSortChange(sort) },
              label = { Text(sort.label, style = MaterialTheme.typography.labelMedium) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = PrimaryContainer,
                selectedLabelColor = OnPrimaryContainer,
                containerColor = SurfaceContainerLow,
                labelColor = OnSurfaceVariant,
              ),
              border = null,
            )
          }
          items(listOf(0, 10, 20)) { score ->
            val selected = state.minScore == score
            FilterChip(
              selected = selected,
              onClick = { onMinScoreChange(score) },
              label = {
                Text(
                  if (score == 0) "Score: Tous" else "Score ≥ $score",
                  style = MaterialTheme.typography.labelMedium,
                )
              },
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

        // Veille manuelle : sites qu'on ne peut pas scanner automatiquement
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "GlobeNewswire :",
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariant,
          )
          Config.MANUAL_WATCH_LINKS.forEach { (label, url) ->
            Surface(
              color = SurfaceContainerLow,
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
              },
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = OnSurface)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = Outline, modifier = Modifier.size(14.dp))
              }
            }
          }
        }
      }
    }

    if (filtered.isEmpty()) {
      item {
        Surface(
          color = SurfaceContainer,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Aucune news dans la boîte de réception. Appuie sur Scanner pour récupérer les flux RSS.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(20.dp),
          )
        }
      }
    } else {
      items(filtered, key = { it.id }) { item ->
        NewsTriageCard(
          item = item,
          onOpenUrl = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }
          },
          onMajor = { onUpdateStatus(item.id, "major", item.title) },
          onVrac = { onUpdateStatus(item.id, "vrac", item.title) },
          onIgnore = { onUpdateStatus(item.id, "ignored", item.title) },
          inX = item.id in state.xPickIds,
          onToggleX = { onTogglePick(item) },
        )
      }
    }
  }
}

@Composable
private fun NewsTriageCard(
  item: NewsItem,
  onOpenUrl: () -> Unit,
  onMajor: () -> Unit,
  onVrac: () -> Unit,
  onIgnore: () -> Unit,
  inX: Boolean,
  onToggleX: () -> Unit,
) {
  val isHot = item.score >= 15
  Surface(
    color = SurfaceContainer,
    shape = RoundedCornerShape(16.dp),
    shadowElevation = 4.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(if (isHot) PrimaryContainer.copy(alpha = 0.2f) else SurfaceContainerHigh)
              .padding(horizontal = 8.dp, vertical = 3.dp)
          ) {
            Text(
              text = if (isHot) "🔥 Score ${item.score}" else "Score ${item.score}",
              style = MaterialTheme.typography.labelSmall,
              color = if (isHot) Primary else OnSurfaceVariant,
            )
          }
          Text(
            text = item.source,
            style = MaterialTheme.typography.labelMedium,
            color = Tertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(onClick = onOpenUrl, modifier = Modifier.size(28.dp)) {
          Icon(
            imageVector = Icons.Rounded.OpenInNew,
            contentDescription = "Ouvrir l'article",
            tint = Outline,
            modifier = Modifier.size(16.dp),
          )
        }
      }

      Text(
        text = item.title,
        style = MaterialTheme.typography.titleMedium,
        color = OnSurface,
        modifier = Modifier.clickable(onClick = onOpenUrl),
      )

      Text(
        text = "🕒 ${NewsEngine.formatAge(item.published)}" +
          if (item.tags.isNotBlank()) " • ${item.tags}" else "",
        style = MaterialTheme.typography.bodySmall,
        color = Outline,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      if (item.summary.isNotBlank()) {
        Text(
          text = item.summary,
          style = MaterialTheme.typography.bodySmall,
          color = OnSurfaceVariant,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
      }

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
            Text("En Vrac", style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), color = Tertiary)
          }
        }
        Surface(
          color = SurfaceContainerHigh,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.width(46.dp).height(42.dp).clickable(onClick = onIgnore),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Ignorer", tint = Outline, modifier = Modifier.size(18.dp))
          }
        }
        Surface(
          color = if (inX) Secondary.copy(alpha = 0.2f) else SurfaceContainerHigh,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.width(46.dp).height(42.dp).clickable(onClick = onToggleX),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Rounded.Campaign,
              contentDescription = if (inX) "Retirer de l'Espace X" else "Ajouter à l'Espace X",
              tint = if (inX) Secondary else Outline,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }
  }
}
