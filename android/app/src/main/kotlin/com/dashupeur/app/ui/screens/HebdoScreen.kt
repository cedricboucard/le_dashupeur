package com.dashupeur.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dashupeur.app.data.HebdoExportResult
import com.dashupeur.app.data.NewsItem
import com.dashupeur.app.ui.DashupeurUiState
import com.dashupeur.app.ui.theme.OnPrimaryContainer
import com.dashupeur.app.ui.theme.OnSurface
import com.dashupeur.app.ui.theme.OnSurfaceVariant
import com.dashupeur.app.ui.theme.Outline
import com.dashupeur.app.ui.theme.PrimaryContainer
import com.dashupeur.app.ui.theme.Secondary
import com.dashupeur.app.ui.theme.SurfaceBg
import com.dashupeur.app.ui.theme.SurfaceContainer
import com.dashupeur.app.ui.theme.SurfaceContainerHigh
import com.dashupeur.app.ui.theme.SurfaceContainerHighest
import com.dashupeur.app.ui.theme.SurfaceContainerLow
import com.dashupeur.app.ui.theme.SurfaceContainerLowest
import com.dashupeur.app.ui.theme.Tertiary

@Composable
fun HebdoScreen(
  state: DashupeurUiState,
  onMoveMajor: (Int, Int) -> Unit,
  onRemoveItem: (NewsItem) -> Unit,
  onGeneratePack: () -> Unit,
  onDismissExport: () -> Unit,
) {
  val estimatedMinutes = state.majorItems.size * 3 + (state.vracItems.size + 1) / 2

  Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "🎬 Préparer l'Hebdo",
              style = MaterialTheme.typography.headlineMedium,
              color = OnSurface,
            )
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceContainerHighest)
                .padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Secondary))
              Text("CONDUCTEUR", style = MaterialTheme.typography.labelSmall, color = Secondary)
            }
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(SurfaceContainerLow)
              .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(SurfaceContainerHigh)
                  .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = PrimaryContainer, modifier = Modifier.size(14.dp))
                Text(
                  text = "${state.majorItems.size} Gros Sujet(s)",
                  style = MaterialTheme.typography.labelMedium,
                  color = PrimaryContainer,
                )
              }
              Text(
                text = "• ${state.vracItems.size} Vrac",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurfaceVariant,
              )
            }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(Icons.Rounded.Timer, contentDescription = null, tint = Tertiary, modifier = Modifier.size(15.dp))
              Text("~$estimatedMinutes min", style = MaterialTheme.typography.labelMedium, color = Tertiary)
            }
          }
        }
      }

      // Gros Sujets Section
      item {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = PrimaryContainer, modifier = Modifier.size(18.dp))
          Text(
            text = "GROS SUJETS — ORDRE DU SCRIPT",
            style = MaterialTheme.typography.labelMedium,
            color = OnSurface,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      if (state.majorItems.isEmpty()) {
        item {
          Surface(color = SurfaceContainerLow, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "Aucun gros sujet. Ajoute des news depuis Actus, Radar ou Tendances.",
              style = MaterialTheme.typography.bodySmall,
              color = Outline,
              modifier = Modifier.padding(14.dp),
            )
          }
        }
      } else {
        itemsIndexed(state.majorItems, key = { _, item -> "maj_${item.id}" }) { idx, item ->
          HebdoMajorCard(
            index = idx,
            total = state.majorItems.size,
            item = item,
            onMoveUp = { onMoveMajor(idx, -1) },
            onMoveDown = { onMoveMajor(idx, 1) },
            onRemove = { onRemoveItem(item) },
          )
        }
      }

      // News en Vrac Section
      item {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Tertiary, modifier = Modifier.size(18.dp))
          Text(
            text = "NEWS EN VRAC",
            style = MaterialTheme.typography.labelMedium,
            color = OnSurface,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      if (state.vracItems.isEmpty()) {
        item {
          Surface(color = SurfaceContainerLow, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "Aucune brève en vrac pour le moment.",
              style = MaterialTheme.typography.bodySmall,
              color = Outline,
              modifier = Modifier.padding(14.dp),
            )
          }
        }
      } else {
        itemsIndexed(state.vracItems, key = { _, item -> "vrac_${item.id}" }) { _, item ->
          Surface(
            color = SurfaceContainerLow,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = item.title,
                  style = MaterialTheme.typography.labelLarge,
                  color = OnSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = item.source,
                  style = MaterialTheme.typography.bodySmall,
                  color = Tertiary,
                )
              }
              IconButton(onClick = { onRemoveItem(item) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Retirer", tint = Outline, modifier = Modifier.size(18.dp))
              }
            }
          }
        }
      }
    }

    // Floating Export CTA
    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .background(SurfaceBg.copy(alpha = 0.92f))
        .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Button(
        onClick = onGeneratePack,
        enabled = state.majorItems.isNotEmpty() || state.vracItems.isNotEmpty(),
        colors = ButtonDefaults.buttonColors(
          containerColor = PrimaryContainer,
          contentColor = OnPrimaryContainer,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(50.dp),
      ) {
        Icon(Icons.Rounded.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Générer le pack de L'Hebdo", style = MaterialTheme.typography.titleMedium)
      }
    }

    state.hebdoExport?.let { exportResult ->
      HebdoExportDialog(export = exportResult, onDismiss = onDismissExport)
    }
  }
}

@Composable
private fun HebdoMajorCard(
  index: Int,
  total: Int,
  item: NewsItem,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onRemove: () -> Unit,
) {
  Surface(
    color = SurfaceContainer,
    shape = RoundedCornerShape(14.dp),
    shadowElevation = 3.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier = Modifier
          .size(30.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(if (index == 0) PrimaryContainer else SurfaceContainerHigh),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "%02d".format(index + 1),
          style = MaterialTheme.typography.labelLarge,
          color = if (index == 0) OnPrimaryContainer else OnSurface,
          fontWeight = FontWeight.Bold,
        )
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = item.title,
          style = MaterialTheme.typography.labelLarge,
          color = OnSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${item.source} • Script ~3 min",
          style = MaterialTheme.typography.bodySmall,
          color = Secondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        if (index > 0) {
          IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Monter", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
          }
        }
        if (index < total - 1) {
          IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Rounded.ArrowDownward, contentDescription = "Descendre", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
          }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(30.dp)) {
          Icon(Icons.Rounded.Close, contentDescription = "Retirer", tint = Outline, modifier = Modifier.size(18.dp))
        }
      }
    }
  }
}

@Composable
private fun HebdoExportDialog(export: HebdoExportResult, onDismiss: () -> Unit) {
  val context = LocalContext.current
  var selectedTab by remember { mutableIntStateOf(0) }
  val activeText = if (selectedTab == 0) export.scriptMarkdown else export.youtubeDescription

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      color = SurfaceContainer,
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Pack L'Hebdo généré ✅", style = MaterialTheme.typography.headlineSmall, color = Secondary)
          IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = Outline)
          }
        }

        TabRow(
          selectedTabIndex = selectedTab,
          containerColor = SurfaceContainerLowest,
          contentColor = PrimaryContainer,
        ) {
          Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("script_hebdo.md") })
          Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("description_youtube.txt") })
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceContainerLowest)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        ) {
          Text(
            text = activeText,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = OnSurface,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TextButton(
            onClick = {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clipboard.setPrimaryClip(ClipData.newPlainText("Le Dashupeur", activeText))
            },
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copier")
          }
          Button(
            onClick = {
              val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, activeText)
              }
              context.startActivity(Intent.createChooser(sendIntent, "Partager le script"))
            },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer, contentColor = OnPrimaryContainer),
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Partager")
          }
        }
      }
    }
  }
}
