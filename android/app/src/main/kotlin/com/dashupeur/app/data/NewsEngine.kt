package com.dashupeur.app.data

import android.content.Context
import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object NewsEngine {
  private val URL_RE = Regex("""https?://\S+""")
  private val REDDIT_FOOTER_RE = Regex(
    """submitted by\s+/u/\S+(\s*\[link\])?(\s*\[comments\])?""",
    RegexOption.IGNORE_CASE,
  )
  private val WHITESPACE_RE = Regex("""\s+""")

  fun cleanHtml(rawHtml: String): String {
    if (rawHtml.isBlank()) return ""
    // Html.fromHtml remplace chaque <img> par le caractère U+FFFC (affiché « OBJ ») : on le retire
    val plain = Html.fromHtml(rawHtml, Html.FROM_HTML_MODE_LEGACY).toString().replace('\uFFFC', ' ')
    val noFooter = REDDIT_FOOTER_RE.replace(plain, " ")
    val noUrls = URL_RE.replace(noFooter, " ")
    return WHITESPACE_RE.replace(noUrls, " ").trim().take(600)
  }

  fun parsePublished(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    val trimmed = value.trim()
    return runCatching { Instant.parse(trimmed) }
      .recoverCatching { OffsetDateTime.parse(trimmed).toInstant() }
      .recoverCatching { ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }
      .recoverCatching {
        val fmt = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
        ZonedDateTime.parse(trimmed, fmt).toInstant()
      }
      .getOrNull()
  }

  fun formatAge(published: String): String {
    val instant = parsePublished(published) ?: return "date inconnue"
    val local = instant.atZone(ZoneId.systemDefault())
    val days = ChronoUnit.DAYS.between(local.toLocalDate(), LocalDate.now())
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val dayMonthFmt = DateTimeFormatter.ofPattern("dd/MM")
    val fullFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    return when {
      days <= 0 -> "🆕 aujourd'hui ${local.format(timeFmt)}"
      days == 1L -> "hier ${local.format(timeFmt)}"
      days < 7L -> "il y a $days jours (${local.format(dayMonthFmt)})"
      else -> local.format(fullFmt)
    }
  }

  fun scoreArticle(title: String, summary: String): Pair<Int, List<String>> {
    val content = "$title $summary".lowercase()
    var score = 0
    val matched = mutableListOf<String>()
    for ((kw, weight) in Config.KEYWORDS_WEIGHTS) {
      if (content.contains(kw)) {
        score += weight
        matched.add(kw)
      }
    }
    return score to matched
  }

  suspend fun fetchAllFeeds(db: DashupeurDb): Pair<Int, List<String>> = withContext(Dispatchers.IO) {
    var added = 0
    val errors = mutableListOf<String>()

    for ((sourceName, feedUrl) in Config.RSS_FEEDS) {
      runCatching {
        val xml = HttpUtils.getText(feedUrl, timeoutMs = 10000)
        val entries = parseRssOrAtom(xml).take(20)
        for (entry in entries) {
          if (entry.url.isBlank()) continue
          val summary = cleanHtml(entry.rawSummary)
          val (score, tags) = scoreArticle(entry.title, summary)
          val id = HttpUtils.md5Hex(entry.url)
          val publishedIso = parsePublished(entry.published)?.toString().orEmpty()
          val inserted = db.upsertNews(
            NewsItem(
              id = id,
              title = entry.title.ifBlank { "Sans titre" },
              url = entry.url,
              source = sourceName,
              published = publishedIso,
              summary = summary,
              score = score,
              tags = tags.joinToString(", "),
              status = "inbox",
            )
          )
          if (inserted) added++
        }
      }.onFailure { e ->
        errors.add("$sourceName: ${e.message ?: "erreur"}")
      }
    }
    // Nouvelles sorties Steam (pas de flux RSS : recherche de la boutique)
    runCatching { added += RadarRepository.scanSteamReleases(db) }
      .onFailure { e -> errors.add("Steam · Nouveautés: ${e.message ?: "erreur"}") }
    added to errors
  }

  private data class FeedEntry(
    val title: String = "",
    val url: String = "",
    val published: String = "",
    val rawSummary: String = "",
  )

  private fun parseRssOrAtom(xml: String): List<FeedEntry> {
    val parser = Xml.newPullParser().apply {
      setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
      setInput(StringReader(xml))
    }
    val entries = mutableListOf<FeedEntry>()
    var inItem = false
    var isAtom = false
    var title = ""
    var url = ""
    var published = ""
    var rawSummary = ""
    var currentTag = ""

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
      when (eventType) {
        XmlPullParser.START_TAG -> {
          val tag = parser.name.lowercase()
          currentTag = tag
          if (tag == "feed") isAtom = true
          if (tag == "item" || (isAtom && tag == "entry")) {
            inItem = true
            title = ""
            url = ""
            published = ""
            rawSummary = ""
          } else if (inItem && tag == "link") {
            val href = parser.getAttributeValue(null, "href")
            val rel = parser.getAttributeValue(null, "rel")
            if (!href.isNullOrBlank() && (rel.isNullOrBlank() || rel == "alternate")) {
              url = href
            }
          }
        }
        XmlPullParser.TEXT -> {
          if (inItem) {
            val text = parser.text.orEmpty()
            when (currentTag) {
              "title" -> title += text
              "link" -> if (url.isBlank()) url += text.trim()
              "pubdate", "published", "updated", "dc:date" -> if (published.isBlank()) published += text.trim()
              "description", "summary", "content", "content:encoded", "media:description" -> rawSummary += text
            }
          }
        }
        XmlPullParser.END_TAG -> {
          val tag = parser.name.lowercase()
          if (tag == "item" || (isAtom && tag == "entry")) {
            if (url.isNotBlank()) {
              entries.add(FeedEntry(title.trim(), url.trim(), published.trim(), rawSummary))
            }
            inItem = false
          }
          currentTag = ""
        }
      }
      eventType = parser.next()
    }
    return entries
  }

  fun exportHebdo(
    context: Context,
    majors: List<NewsItem>,
    vracs: List<NewsItem>,
  ): HebdoExportResult {
    val now = ZonedDateTime.now()
    val folderName = "OnEpluche_${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))}"
    val exportDir = File(context.filesDir, "exports/$folderName").apply { mkdirs() }

    val scriptLines = mutableListOf("# 🧅 SCRIPT ${Config.SHOW_NAME.uppercase()} DU ${now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}\n")
    val descLines = mutableListOf(
      "🧅 Bienvenue dans ce nouvel épisode de « ${Config.SHOW_NAME} » !\n",
      "⏱️ CHAPITRES :",
      "00:00 - Introduction",
    )
    val linksLines = mutableListOf("\n🔗 SOURCES & LIENS :")

    scriptLines.add("## 🔥 GROS SUJETS\n")
    majors.forEachIndexed { index, item ->
      val idx = index + 1
      scriptLines.add("### $idx. ${item.title}")
      scriptLines.add("*Source : ${item.source} | Tags : ${item.tags}*\n")
      scriptLines.add("${item.summary}\n")
      scriptLines.add("Lien : ${item.url}\n---\n")

      descLines.add("%02d:00 - ${item.title}".format(idx * 3))
      linksLines.add("- ${item.title} : ${item.url}")
    }

    scriptLines.add("\n## ⚡ NEWS EN VRAC\n")
    descLines.add("XX:XX - News en vrac")
    for (item in vracs) {
      scriptLines.add("- **${item.title}** (${item.source}) -> ${item.url}")
      scriptLines.add("  > ${item.summary.take(200)}...\n")
      linksLines.add("- [Vrac] ${item.title} : ${item.url}")
    }

    val scriptMd = scriptLines.joinToString("\n")
    val youtubeDesc = (descLines + linksLines).joinToString("\n")

    File(exportDir, "script_episode.md").writeText(scriptMd, Charsets.UTF_8)
    File(exportDir, "description_youtube.txt").writeText(youtubeDesc, Charsets.UTF_8)

    return HebdoExportResult(
      folderPath = exportDir.absolutePath,
      scriptMarkdown = scriptMd,
      youtubeDescription = youtubeDesc,
    )
  }
}
