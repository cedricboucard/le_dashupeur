package com.dashupeur.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dashupeur.app.MainActivity
import com.dashupeur.app.R
import java.util.concurrent.TimeUnit

/**
 * Alertes des sorties Steam : toutes les 3 heures, l'app cherche les nouvelles sorties
 * (mêmes critères que le scan : nouveautés populaires et free-to-play à 50 joueurs ou plus),
 * les ajoute à la boîte de réception et envoie une notification pour chacune.
 */
object SteamAlerts {
  private const val PREFS = "dashupeur_settings"
  private const val KEY_ENABLED = "steam_alerts_enabled"
  private const val CHANNEL_ID = "steam_releases"
  private const val PERIODIC_WORK = "steam_release_alerts"
  private const val NOW_WORK = "steam_release_alerts_now"
  private const val INTERVAL_HOURS = 3L
  /** Au-delà, une seule notification récapitulative au lieu d'une par jeu. */
  private const val MAX_SINGLE_NOTIFICATIONS = 3

  fun isEnabled(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

  /** Active ou coupe les alertes. À l'activation, une vérification est lancée tout de suite. */
  fun setEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    val wm = WorkManager.getInstance(context)
    if (enabled) {
      schedule(context)
      wm.enqueueUniqueWork(
        NOW_WORK,
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<SteamReleaseWorker>().setConstraints(networkConstraint()).build(),
      )
    } else {
      wm.cancelUniqueWork(PERIODIC_WORK)
      wm.cancelUniqueWork(NOW_WORK)
    }
  }

  /** (Re)programme la vérification périodique si les alertes sont actives. Sans effet si déjà programmée. */
  fun schedule(context: Context) {
    if (!isEnabled(context)) return
    val request = PeriodicWorkRequestBuilder<SteamReleaseWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
      .setConstraints(networkConstraint())
      .build()
    WorkManager.getInstance(context)
      .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
  }

  private fun networkConstraint() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

  fun canNotify(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

  private fun ensureChannel(context: Context) {
    val channel = NotificationChannel(CHANNEL_ID, "Sorties Steam", NotificationManager.IMPORTANCE_DEFAULT).apply {
      description = "Une notification à chaque nouvelle sortie Steam repérée"
    }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  private fun line(item: RadarItem): String {
    val free = item.kind == "🆓 Nouveau Free-to-Play"
    val players = item.players?.let { " · ${RadarRepository.formatIntFr(it)} joueurs" }.orEmpty()
    return (if (free) "🆓 " else "🎮 ") + item.title + (if (free) " (free-to-play)" else "") + players
  }

  fun notify(context: Context, items: List<RadarItem>) {
    if (items.isEmpty() || !canNotify(context)) return
    ensureChannel(context)
    val nm = NotificationManagerCompat.from(context)
    try {
      if (items.size <= MAX_SINGLE_NOTIFICATIONS) {
        for (item in items) {
          val open = PendingIntent.getActivity(
            context,
            item.url.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse(item.url)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
          )
          val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dashupeur)
            .setContentTitle("Sortie Steam")
            .setContentText(line(item))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
          nm.notify(item.url.hashCode(), notification)
        }
      } else {
        val open = PendingIntent.getActivity(
          context,
          0,
          Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle()
        items.take(7).forEach { style.addLine(line(it)) }
        if (items.size > 7) style.setSummaryText("et ${items.size - 7} autre(s) dans la boîte de réception")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
          .setSmallIcon(R.drawable.ic_stat_dashupeur)
          .setContentTitle("${items.size} nouvelles sorties Steam")
          .setContentText(items.joinToString(", ") { it.title })
          .setStyle(style)
          .setContentIntent(open)
          .setAutoCancel(true)
          .build()
        nm.notify(PERIODIC_WORK.hashCode(), notification)
      }
    } catch (_: SecurityException) {
      // Permission retirée entre-temps : on n'insiste pas
    }
  }
}

/** Tâche de fond lancée par WorkManager : cherche les sorties Steam et notifie les nouvelles. */
class SteamReleaseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    if (!SteamAlerts.isEnabled(applicationContext)) return Result.success()
    return try {
      val added = RadarRepository.scanSteamReleases(DashupeurDb(applicationContext))
      SteamAlerts.notify(applicationContext, added)
      Result.success()
    } catch (e: Exception) {
      if (runAttemptCount < 2) Result.retry() else Result.failure()
    }
  }
}
