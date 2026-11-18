package com.dashupeur.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dashupeur.app.data.ApiKeysConfig
import com.dashupeur.app.ui.screens.ActusScreen
import com.dashupeur.app.ui.screens.HebdoScreen
import com.dashupeur.app.ui.screens.RadarScreen
import com.dashupeur.app.ui.screens.TrendsScreen
import com.dashupeur.app.ui.screens.XScreen
import com.dashupeur.app.ui.theme.OnPrimary
import com.dashupeur.app.ui.theme.OnPrimaryContainer
import com.dashupeur.app.ui.theme.OnSurface
import com.dashupeur.app.ui.theme.OnSurfaceVariant
import com.dashupeur.app.ui.theme.Outline
import com.dashupeur.app.ui.theme.Primary
import com.dashupeur.app.ui.theme.PrimaryContainer
import com.dashupeur.app.ui.theme.Secondary
import com.dashupeur.app.ui.theme.SurfaceBg
import com.dashupeur.app.ui.theme.SurfaceContainer
import com.dashupeur.app.ui.theme.SurfaceContainerHigh
import com.dashupeur.app.ui.theme.SurfaceContainerHighest
import com.dashupeur.app.ui.theme.SurfaceContainerLow
import com.dashupeur.app.ui.theme.SurfaceContainerLowest

@Composable
fun LeDashupeurApp(viewModel: DashupeurViewModel = viewModel()) {
  val state by viewModel.state.collectAsState()
  // Android 13 et plus : il faut demander la permission avant d'activer les alertes
  val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    viewModel.setSteamAlerts(enabled = true, permissionGranted = granted)
  }
  val toggleSteamAlerts = {
    if (state.steamAlertsEnabled) {
      viewModel.setSteamAlerts(false)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      viewModel.setSteamAlerts(true)
    }
  }
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(state.flashMessage) {
    val msg = state.flashMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
    viewModel.clearFlash()
  }

  Scaffold(
    containerColor = SurfaceBg,
    snackbarHost = {
      SnackbarHost(snackbarHostState) { data ->
        Snackbar(
          snackbarData = data,
          containerColor = SurfaceContainerHigh,
          contentColor = Secondary,
        )
      }
    },
    topBar = {
      Surface(
        color = SurfaceBg.copy(alpha = 0.92f),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(PrimaryContainer)
            )
            Text(
              text = state.currentTab.headerTitle.uppercase(),
              style = MaterialTheme.typography.headlineSmall,
              color = OnSurface,
              fontWeight = FontWeight.Bold,
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            IconButton(onClick = toggleSteamAlerts) {
              Icon(
                imageVector = if (state.steamAlertsEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                contentDescription = if (state.steamAlertsEnabled) "Couper les alertes sorties Steam" else "Activer les alertes sorties Steam",
                tint = if (state.steamAlertsEnabled) Secondary else OnSurfaceVariant,
              )
            }
            IconButton(onClick = viewModel::openApiKeysDialog) {
              Icon(
                imageVector = Icons.Rounded.Key,
                contentDescription = "Clés API Twitch & YouTube",
                tint = if (state.apiKeys.hasTwitch || state.apiKeys.hasYoutube) Secondary else OnSurfaceVariant,
              )
            }
            Box(
              modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Primary),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = "Le Dashupeur",
                tint = OnPrimary,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
    },
    bottomBar = {
      NavigationBar(
        containerColor = SurfaceContainerLow,
        tonalElevation = 8.dp,
      ) {
        MainTab.entries.forEach { tab ->
          val selected = state.currentTab == tab
          val icon = when (tab) {
            MainTab.ACTUS -> Icons.Rounded.LocalFireDepartment
            MainTab.RADAR -> Icons.Rounded.Radar
            MainTab.TENDANCES -> Icons.Rounded.TrendingUp
            MainTab.HEBDO -> Icons.Rounded.CalendarMonth
            MainTab.X -> Icons.Rounded.Campaign
          }
          val badgeCount = when (tab) {
            MainTab.ACTUS -> state.inboxItems.size
            MainTab.HEBDO -> state.majorItems.size + state.vracItems.size
            MainTab.X -> state.xPicks.size
            else -> 0
          }
          NavigationBarItem(
            selected = selected,
            onClick = { viewModel.selectTab(tab) },
            icon = {
              if (badgeCount > 0) {
                BadgedBox(
                  badge = {
                    Badge(containerColor = PrimaryContainer, contentColor = Color.White) {
                      Text(badgeCount.toString())
                    }
                  }
                ) {
                  Icon(icon, contentDescription = tab.label)
                }
              } else {
                Icon(icon, contentDescription = tab.label)
              }
            },
            label = {
              Text(
                text = tab.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
              )
            },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = PrimaryContainer,
              selectedTextColor = Primary,
              unselectedIconColor = OnSurfaceVariant,
              unselectedTextColor = OnSurfaceVariant,
              indicatorColor = SurfaceContainerHigh,
            ),
          )
        }
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      when (state.currentTab) {
        MainTab.ACTUS -> ActusScreen(
          state = state,
          onSearchChange = viewModel::setSearchQuery,
          onSortChange = viewModel::setSortBy,
          onMinScoreChange = viewModel::setMinScore,
          onScanFeeds = viewModel::scanFeeds,
          onClearInbox = viewModel::clearInbox,
          onUpdateStatus = viewModel::updateNewsStatus,
          onTogglePick = viewModel::toggleXPick,
        )
        MainTab.RADAR -> RadarScreen(
          state = state,
          onSelectSource = viewModel::selectRadarSource,
          onToggleGpKind = viewModel::toggleGpKind,
          onRefresh = { viewModel.loadRadar(state.radarSource, forceRefresh = true) },
          onSendToHebdo = viewModel::sendRadarToHebdo,
        )
        MainTab.TENDANCES -> TrendsScreen(
          state = state,
          onSelectWindow = viewModel::setTrendWindow,
          onSelectPlatform = viewModel::setTrendPlatform,
          onMinGrowthChange = viewModel::setTrendMinGrowth,
          onToggleMultiPlatform = viewModel::toggleMultiPlatformOnly,
          onCollectNow = viewModel::collectSteamTrendsNow,
          onSendToHebdo = viewModel::sendTrendToHebdo,
        )
        MainTab.HEBDO -> HebdoScreen(
          state = state,
          onMoveMajor = viewModel::moveMajorItem,
          onRemoveItem = { item -> viewModel.updateNewsStatus(item.id, "inbox", item.title) },
          onGeneratePack = viewModel::generateHebdoPack,
          onDismissExport = viewModel::dismissHebdoExport,
        )
        MainTab.X -> XScreen(
          state = state,
          onSearchChange = viewModel::setXSearch,
          onTogglePick = viewModel::toggleXPick,
          onMove = viewModel::moveXPick,
          onClear = viewModel::clearXPicks,
          onModeChange = viewModel::setXMode,
          onLinkChange = viewModel::setXWithLink,
          onEdit = viewModel::editXTweet,
          onReset = viewModel::resetXTweet,
          onFlash = viewModel::flash,
        )
      }
    }

    if (state.showApiKeysDialog) {
      ApiKeysDialog(
        initial = state.apiKeys,
        onDismiss = viewModel::closeApiKeysDialog,
        onSave = viewModel::saveApiKeys,
      )
    }
  }
}

@Composable
private fun ApiKeysDialog(
  initial: ApiKeysConfig,
  onDismiss: () -> Unit,
  onSave: (ApiKeysConfig) -> Unit,
) {
  var twitchId by remember { mutableStateOf(initial.twitchClientId) }
  var twitchSecret by remember { mutableStateOf(initial.twitchClientSecret) }
  var youtubeKey by remember { mutableStateOf(initial.youtubeApiKey) }
  var steamId by remember { mutableStateOf(initial.steamId) }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      color = SurfaceContainer,
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("🔑 Clés API et Steam", style = MaterialTheme.typography.titleMedium, color = OnSurface)
          IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = Outline)
          }
        }
        Text(
          text = "Enregistrées localement sur l'appareil pour activer les relevés Twitch et YouTube en direct, et le suivi de ta liste de souhaits Steam.",
          style = MaterialTheme.typography.bodySmall,
          color = OnSurfaceVariant,
        )
        OutlinedTextField(
          value = twitchId,
          onValueChange = { twitchId = it },
          label = { Text("TWITCH_CLIENT_ID") },
          singleLine = true,
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
        OutlinedTextField(
          value = twitchSecret,
          onValueChange = { twitchSecret = it },
          label = { Text("TWITCH_CLIENT_SECRET") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
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
        OutlinedTextField(
          value = youtubeKey,
          onValueChange = { youtubeKey = it },
          label = { Text("YOUTUBE_API_KEY") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
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
        OutlinedTextField(
          value = steamId,
          onValueChange = { steamId = it },
          label = { Text("STEAM_ID (liste de souhaits)") },
          supportingText = { Text("SteamID64 à 17 chiffres, liste de souhaits publique.") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onClick = onDismiss) { Text("Annuler") }
          Spacer(Modifier.width(8.dp))
          Button(
            onClick = { onSave(ApiKeysConfig(twitchId, twitchSecret, youtubeKey, steamId)) },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryContainer, contentColor = OnPrimaryContainer),
          ) {
            Text("Enregistrer")
          }
        }
      }
    }
  }
}
