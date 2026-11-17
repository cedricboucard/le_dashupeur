package com.dashupeur.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

class DashupeurDb(context: Context) : SQLiteOpenHelper(
  seedDatabaseIfNeeded(context),
  DB_NAME,
  null,
  DB_VERSION,
) {
  companion object {
    private const val DB_NAME = "dashupeur.db"
    private const val DB_VERSION = 2  // v2 : table x_picks (Espace X)

    private fun seedDatabaseIfNeeded(context: Context): Context {
      val dbFile: File = context.getDatabasePath(DB_NAME)
      if (!dbFile.exists()) {
        dbFile.parentFile?.mkdirs()
        runCatching {
          context.assets.open(DB_NAME).use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
          }
        }
      }
      return context
    }
  }

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS news (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        url TEXT NOT NULL,
        source TEXT NOT NULL,
        published TEXT,
        summary TEXT,
        score INTEGER DEFAULT 0,
        tags TEXT,
        status TEXT DEFAULT 'inbox',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS trend_snapshots (
        taken_at TEXT NOT NULL,
        source TEXT NOT NULL,
        game_key TEXT NOT NULL,
        game_name TEXT NOT NULL,
        value INTEGER NOT NULL,
        url TEXT,
        image TEXT,
        PRIMARY KEY (taken_at, source, game_key)
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      CREATE INDEX IF NOT EXISTS idx_trend_game
      ON trend_snapshots (source, game_key, taken_at)
      """.trimIndent()
    )
    createXPicksTable(db)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 2) createXPicksTable(db)
  }

  // Espace X : news piochées pour des tweets, indépendamment de leur statut dans l'Hebdo
  private fun createXPicksTable(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE IF NOT EXISTS x_picks (
        news_id TEXT PRIMARY KEY,
        position REAL NOT NULL
      )
      """.trimIndent()
    )
  }

  fun upsertNews(item: NewsItem): Boolean {
    val values = ContentValues().apply {
      put("id", item.id)
      put("title", item.title)
      put("url", item.url)
      put("source", item.source)
      put("published", item.published)
      put("summary", item.summary)
      put("score", item.score)
      put("tags", item.tags)
    }
    val rowId = writableDatabase.insertWithOnConflict(
      "news",
      null,
      values,
      SQLiteDatabase.CONFLICT_IGNORE,
    )
    return rowId != -1L
  }

  fun getNews(status: String? = null, minScore: Int = 0): List<NewsItem> {
    val args = mutableListOf(minScore.toString())
    val where = StringBuilder("score >= ?")
    if (status != null) {
      where.append(" AND status = ?")
      args.add(status)
    }
    val sql = "SELECT * FROM news WHERE $where ORDER BY score DESC, created_at DESC"
    return queryNews(sql, args.toTypedArray())
  }

  private fun queryNews(sql: String, args: Array<String>): List<NewsItem> {
    val items = mutableListOf<NewsItem>()
    readableDatabase.rawQuery(sql, args).use { cursor ->
      val idxId = cursor.getColumnIndexOrThrow("id")
      val idxTitle = cursor.getColumnIndexOrThrow("title")
      val idxUrl = cursor.getColumnIndexOrThrow("url")
      val idxSource = cursor.getColumnIndexOrThrow("source")
      val idxPublished = cursor.getColumnIndexOrThrow("published")
      val idxSummary = cursor.getColumnIndexOrThrow("summary")
      val idxScore = cursor.getColumnIndexOrThrow("score")
      val idxTags = cursor.getColumnIndexOrThrow("tags")
      val idxStatus = cursor.getColumnIndexOrThrow("status")
      val idxCreatedAt = cursor.getColumnIndexOrThrow("created_at")
      while (cursor.moveToNext()) {
        items.add(
          NewsItem(
            id = cursor.getString(idxId),
            title = cursor.getString(idxTitle),
            url = cursor.getString(idxUrl),
            source = cursor.getString(idxSource),
            published = cursor.getString(idxPublished).orEmpty(),
            summary = cursor.getString(idxSummary).orEmpty(),
            score = cursor.getInt(idxScore),
            tags = cursor.getString(idxTags).orEmpty(),
            status = cursor.getString(idxStatus).orEmpty(),
            createdAt = cursor.getString(idxCreatedAt).orEmpty(),
          )
        )
      }
    }
    return items
  }

  fun updateStatus(newsId: String, newStatus: String) {
    val values = ContentValues().apply { put("status", newStatus) }
    writableDatabase.update("news", values, "id = ?", arrayOf(newsId))
  }

  fun clearInbox() {
    // Les news piochées pour X restent, même si elles étaient dans la boîte de réception
    writableDatabase.delete(
      "news",
      "status IN ('inbox', 'ignored') AND id NOT IN (SELECT news_id FROM x_picks)",
      null,
    )
  }

  // --- Espace X ---

  /** Toutes les news (quel que soit leur statut), les plus récentes d'abord. */
  fun getAllNews(search: String = "", limit: Int = 60): List<NewsItem> {
    val args = mutableListOf<String>()
    var sql = "SELECT * FROM news"
    if (search.isNotBlank()) {
      sql += " WHERE lower(title || ' ' || ifnull(summary, '') || ' ' || source) LIKE ?"
      args.add("%${search.lowercase()}%")
    }
    sql += " ORDER BY published DESC, created_at DESC LIMIT $limit"
    return queryNews(sql, args.toTypedArray())
  }

  fun getXPicks(): List<NewsItem> = queryNews(
    "SELECT news.* FROM x_picks JOIN news ON news.id = x_picks.news_id ORDER BY x_picks.position",
    emptyArray(),
  )

  fun addXPick(newsId: String) {
    writableDatabase.execSQL(
      "INSERT OR IGNORE INTO x_picks (news_id, position) VALUES (?, (SELECT ifnull(max(position), 0) + 1 FROM x_picks))",
      arrayOf(newsId),
    )
  }

  fun removeXPick(newsId: String) {
    writableDatabase.delete("x_picks", "news_id = ?", arrayOf(newsId))
  }

  fun clearXPicks() {
    writableDatabase.delete("x_picks", null, null)
  }

  /** Échange la news avec sa voisine (offset -1 = monter, +1 = descendre). */
  fun moveXPick(newsId: String, offset: Int) {
    val ids = getXPicks().map { it.id }.toMutableList()
    val i = ids.indexOf(newsId)
    val j = i + offset
    if (i < 0 || j !in ids.indices) return
    ids[i] = ids[j].also { ids[j] = ids[i] }
    val db = writableDatabase
    db.beginTransaction()
    try {
      ids.forEachIndexed { pos, id ->
        db.execSQL("UPDATE x_picks SET position = ? WHERE news_id = ?", arrayOf<Any>(pos + 1, id))
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun saveSnapshot(source: String, takenAt: String, rows: List<SnapshotRow>) {
    val db = writableDatabase
    db.beginTransaction()
    try {
      for (row in rows) {
        val values = ContentValues().apply {
          put("taken_at", takenAt)
          put("source", source)
          put("game_key", row.gameKey)
          put("game_name", row.gameName)
          put("value", row.value)
          put("url", row.url)
          put("image", row.image)
        }
        db.insertWithOnConflict("trend_snapshots", null, values, SQLiteDatabase.CONFLICT_REPLACE)
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  data class SnapshotTime(val raw: String, val instant: Instant)

  fun snapshotTimes(source: String): List<SnapshotTime> {
    val result = mutableListOf<SnapshotTime>()
    readableDatabase.rawQuery(
      "SELECT DISTINCT taken_at FROM trend_snapshots WHERE source = ? ORDER BY taken_at",
      arrayOf(source),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val raw = cursor.getString(0)
        parseInstantSafe(raw)?.let { result.add(SnapshotTime(raw, it)) }
      }
    }
    return result
  }

  fun valuesAt(source: String, takenAtIso: String): Map<String, SnapshotRow> {
    val result = linkedMapOf<String, SnapshotRow>()
    readableDatabase.rawQuery(
      "SELECT taken_at, source, game_key, game_name, value, url, image FROM trend_snapshots WHERE source = ? AND taken_at = ?",
      arrayOf(source, takenAtIso),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val row = SnapshotRow(
          takenAt = cursor.getString(0),
          source = cursor.getString(1),
          gameKey = cursor.getString(2),
          gameName = cursor.getString(3),
          value = cursor.getInt(4),
          url = cursor.getString(5).orEmpty(),
          image = cursor.getString(6).orEmpty(),
        )
        result[row.gameKey] = row
      }
    }
    return result
  }

  fun history(gameKey: String, days: Int = 7): Map<String, List<TrendPoint>> {
    val since = Instant.now().minus(days.toLong(), ChronoUnit.DAYS).toString()
    val result = linkedMapOf<String, MutableList<TrendPoint>>()
    readableDatabase.rawQuery(
      "SELECT source, taken_at, value FROM trend_snapshots WHERE game_key = ? AND taken_at >= ? ORDER BY taken_at",
      arrayOf(gameKey, since),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val source = cursor.getString(0)
        val instant = parseInstantSafe(cursor.getString(1)) ?: continue
        val value = cursor.getInt(2)
        result.getOrPut(source) { mutableListOf() }.add(TrendPoint(instant, value))
      }
    }
    return result
  }

  private fun parseInstantSafe(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value) }
      .recoverCatching { java.time.OffsetDateTime.parse(value).toInstant() }
      .getOrNull()
  }
}
