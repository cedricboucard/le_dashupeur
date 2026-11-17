package com.dashupeur.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dashupeur.app.data.NewsEngine
import com.dashupeur.app.data.NewsItem
import com.dashupeur.app.data.XPosts
import com.dashupeur.app.ui.DashupeurUiState
import com.dashupeur.app.ui.XMode
import com.dashupeur.app.ui.theme.ErrorColor
import com.dashupeur.app.ui.theme.OnPrimaryContainer
import com.dashupeur.app.ui.theme.OnSurface
import com.dashupeur.app.ui.theme.OnSurfaceVariant
import com.dashupeur.app.ui.theme.Outline
import com.dashupeur.app.ui.theme.PrimaryContainer
import com.dashupeur.app.ui.theme.Secondary
import com.dashupeur.app.ui.theme.SurfaceContainer
import com.dashupeur.app.ui.theme.SurfaceContainerHigh
import com.dashupeur.app.ui.theme.SurfaceContainerHighest
import com.dashupeur.app.ui.theme.SurfaceContainerLow
import com.dashupeur.app.ui.theme.SurfaceContainerLowest
import com.dashupeur.app.ui.theme.Tertiary

private const val X_PACKAGE = "com.twitter.android"

@Composable
fun XScreen(
  state: DashupeurUiState,
  onSearchChange: (String) -> Unit,
  onTogglePick: (NewsItem) -> Unit,
  onMove: (NewsItem, Int) -> Unit,
  onClear: () -> Unit,
  onModeChange: (XMode) -> Unit,
  onLinkChange: (Boolean) -> Unit,
  onEdit: (String, String) -> Unit,
  onReset: (String) -> Unit,
  onFlash: (String) -> Unit,
) {
  // 0 = Piocher, 1 = Ma sélection (on ouvre la sélection si elle contient déjà des news)
  var subTab by rememberSaveable { mutableIntStateOf(if (state.xPicks.isEmpty()) 0 else 1) }

  Column(modifier = Modifier.fillMaxSize()) {
    TabRow(
      selectedTabIndex = subTab,
      containerColor = SurfaceContainerLow,
      contentColor = PrimaryContainer,
    ) {
      Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("🧺 Piocher") })
      Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("✍️ Ma sélection (${state.xPicks.size})") })
    }
    if (subTab == 0) {
      PickList(state, onSearchChange, onTogglePick)
    } else {
      Selection(state, onTogglePick, onMove, onClear, onModeChange, onLinkChange, onEdit, onReset, onFlash)
    }
  }
}

@Composable
private fun PickList(
  state: DashupeurUiState,
  onSearchChange: (String) -> Unit,
  onTogglePick: (NewsItem) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      Text(
        text = "Pioche librement des news, sans toucher à L'Hebdo.",
        style = MaterialTheme.typography.bodySmall,
        color = OnSurfaceVariant,
      )
      Spacer(Modifier.height(8.dp))
      OutlinedTextField(
        value = state.xSearch,
        onValueChange = onSearchChange,
        placeholder = { Text("Chercher (titre, résumé, source)") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Outline) },
        singleLine = true,
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
      )
    }
    if (state.xResults.isEmpty()) {
      item {
        Text("Aucune news trouvée. Lance un scan depuis Actus.", style = MaterialTheme.typography.bodySmall, color = Outline)
      }
    }
    items(state.xResults, key = { "pick_${it.id}" }) { item ->
      val picked = item.id in state.xPickIds
      Surface(color = SurfaceContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = OnSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
              "🕒 ${NewsEngine.formatAge(item.published)} • ${item.source}",
              style = MaterialTheme.typography.labelSmall,
              color = Outline,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Surface(
            color = if (picked) Secondary.copy(alpha = 0.2f) else SurfaceContainerHighest,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(42.dp).clickable { onTogglePick(item) },
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = if (picked) Icons.Rounded.Check else Icons.Rounded.Add,
                contentDescription = if (picked) "Retirer de la sélection" else "Ajouter à la sélection",
                tint = if (picked) Secondary else OnSurface,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun Selection(
  state: DashupeurUiState,
  onTogglePick: (NewsItem) -> Unit,
  onMove: (NewsItem, Int) -> Unit,
  onClear: () -> Unit,
  onModeChange: (XMode) -> Unit,
  onLinkChange: (Boolean) -> Unit,
  onEdit: (String, String) -> Unit,
  onReset: (String) -> Unit,
  onFlash: (String) -> Unit,
) {
  val picks = state.xPicks
  val thread = state.xMode == XMode.THREAD
  val defaults: List<String> = if (thread) {
    XPosts.buildThread(picks, state.xWithLink)
  } else {
    picks.map { XPosts.buildTweet(it, withLink = state.xWithLink) }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          XMode.entries.forEach { mode ->
            FilterChip(
              selected = state.xMode == mode,
              onClick = { onModeChange(mode) },
              label = { Text(mode.label) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = PrimaryContainer.copy(alpha = 0.25f),
                selectedLabelColor = OnSurface,
                labelColor = OnSurfaceVariant,
              ),
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(
              checked = state.xWithLink,
              onCheckedChange = onLinkChange,
              colors = SwitchDefaults.colors(checkedTrackColor = PrimaryContainer),
            )
            Text("🔗 Lien de la source", style = MaterialTheme.typography.bodySmall, color = OnSurface)
          }
          if (picks.isNotEmpty()) {
            TextButton(onClick = onClear) {
              Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Vider")
            }
          }
        }
        if (thread && picks.isNotEmpty()) {
          Text(
            "Poste le 1er tweet, puis ajoute les suivants dans l'ordre avec « + » dans X.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariant,
          )
        }
      }
    }

    if (picks.isEmpty()) {
      item {
        Surface(color = SurfaceContainerLow, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
          Text(
            "Ta sélection est vide : ajoute des news depuis « Piocher » ou avec le bouton X dans Actus.",
            style = MaterialTheme.typography.bodySmall,
            color = Outline,
            modifier = Modifier.padding(14.dp),
          )
        }
      }
    } else {
      if (thread) {
        item(key = "intro") {
          TweetCard(
            label = "1/${defaults.size} · Intro",
            editKey = "thread_intro",
            default = defaults[0],
            state = state,
            onEdit = onEdit,
            onReset = onReset,
            onFlash = onFlash,
          )
        }
      }
      itemsIndexed(picks, key = { _, it -> "tweet_${it.id}" }) { i, item ->
        val pos = if (thread) "${i + 2}/${defaults.size} · " else ""
        TweetCard(
          label = pos + item.source,
          editKey = "${state.xMode.name}_${item.id}",
          default = defaults[if (thread) i + 1 else i],
          state = state,
          onEdit = onEdit,
          onReset = onReset,
          onFlash = onFlash,
          onMoveUp = if (i > 0) ({ onMove(item, -1) }) else null,
          onMoveDown = if (i < picks.lastIndex) ({ onMove(item, 1) }) else null,
          onRemove = { onTogglePick(item) },
        )
      }
    }
  }
}

@Composable
private fun TweetCard(
  label: String,
  editKey: String,
  default: String,
  state: DashupeurUiState,
  onEdit: (String, String) -> Unit,
  onReset: (String) -> Unit,
  onFlash: (String) -> Unit,
  onMoveUp: (() -> Unit)? = null,
  onMoveDown: (() -> Unit)? = null,
  onRemove: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  val text = state.xEdits[editKey] ?: default
  val length = XPosts.tweetLength(text)
  val tooLong = length > XPosts.TWEET_MAX

  Surface(color = SurfaceContainer, shape = RoundedCornerShape(16.dp), shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          label,
          style = MaterialTheme.typography.labelMedium,
          color = Tertiary,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Text(
          "${if (tooLong) "🔴" else "🟢"} $length/${XPosts.TWEET_MAX}",
          style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
          color = if (tooLong) ErrorColor else Secondary,
        )
        onMoveUp?.let { SmallIcon(Icons.Rounded.ArrowUpward, "Monter", it) }
        onMoveDown?.let { SmallIcon(Icons.Rounded.ArrowDownward, "Descendre", it) }
        onRemove?.let { SmallIcon(Icons.Rounded.Close, "Retirer de la sélection", it) }
      }

      // Le texte se retouche directement dans le champ
      OutlinedTextField(
        value = text,
        onValueChange = { onEdit(editKey, it) },
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (editKey in state.xEdits) {
          IconButton(onClick = { onReset(editKey) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Replay, contentDescription = "Revenir au texte de base", tint = Outline)
          }
        }
        TextButton(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Tweet", text))
            onFlash("Tweet copié 📋")
          },
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Copier")
        }
        Button(
          onClick = { openInX(context, text) },
          colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer, contentColor = OnPrimaryContainer),
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Ouvrir dans X")
        }
      }
    }
  }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
  IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
    Icon(icon, contentDescription = description, tint = Outline, modifier = Modifier.size(18.dp))
  }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
  focusedContainerColor = SurfaceContainerLowest,
  unfocusedContainerColor = SurfaceContainerLowest,
  focusedBorderColor = PrimaryContainer,
  unfocusedBorderColor = SurfaceContainerHigh,
  focusedTextColor = OnSurface,
  unfocusedTextColor = OnSurface,
)

/** Ouvre l'app X avec le tweet pré-rempli, ou x.com dans le navigateur si l'app n'est pas installée. */
private fun openInX(context: Context, text: String) {
  val appIntent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, text)
    setPackage(X_PACKAGE)
  }
  try {
    context.startActivity(appIntent)
  } catch (_: ActivityNotFoundException) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(XPosts.intentUrl(text)))) }
  }
}
