package com.dashupeur.app.data

import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Espace X : transforme des news en tweets séparés ou en thread, prêts à copier-coller sur X. */
object XPosts {
  const val TWEET_MAX = 280
  private const val URL_LENGTH = 23 // X compte chaque lien comme 23 caractères
  private val URL_RE = Regex("""https?://\S+""")
  private val SENTENCE_RE = Regex("""[^.!?]+[.!?]+""")

  // Plages de caractères comptés 1 par X (le reste, dont les emojis, compte 2) — d'après twitter-text
  private val LIGHT_RANGES = listOf(0..4351, 8192..8205, 8208..8223, 8242..8247)

  /** Longueur d'un tweet telle que X la calcule. */
  fun tweetLength(text: String): Int {
    val links = URL_RE.findAll(text).count()
    val rest = URL_RE.replace(text, "")
    var weight = 0
    rest.codePoints().forEach { cp -> weight += if (LIGHT_RANGES.any { cp in it }) 1 else 2 }
    return weight + URL_LENGTH * links
  }

  /** Coupe le texte sur un mot pour qu'il tienne dans le budget (en longueur X). */
  private fun shorten(text: String, budget: Int): String {
    val clean = text.trim().lines().joinToString("\n") { line -> line.split(Regex("""\s+""")).filter { it.isNotEmpty() }.joinToString(" ") }
    if (tweetLength(clean) <= budget) return clean
    val words = clean.split(" ").toMutableList()
    while (words.isNotEmpty() && tweetLength(words.joinToString(" ") + "…") > budget) words.removeAt(words.lastIndex)
    return if (words.isEmpty()) "" else words.joinToString(" ").trimEnd(',', ';', ':', '.', '-', '–') + "…"
  }

  /** 1re vraie phrase du résumé (les formules comme « Greetings, explorers! » sont sautées). */
  private fun firstSentence(summary: String): String {
    val sentences = SENTENCE_RE.findAll(summary).map { it.value.trim() }.toList().ifEmpty { listOf(summary.trim()) }
    return sentences.firstOrNull { it.length >= 40 }.orEmpty()
  }

  /** Tweet prêt à poster : titre + 1re phrase du résumé + lien, coupé pour tenir dans 280 caractères. */
  fun buildTweet(item: NewsItem, prefix: String = "", withLink: Boolean = true): String {
    val link = if (withLink && item.url.isNotBlank()) "\n\n🔗 ${item.url}" else ""
    val budget = TWEET_MAX - tweetLength(prefix) - tweetLength(link)
    val title = item.title.trim()
    val sentence = firstSentence(item.summary)
    var body = title
    if (sentence.isNotEmpty() && !title.lowercase().contains(sentence.lowercase())) {
      val candidate = "$title\n\n$sentence"
      if (tweetLength(candidate) <= budget) body = candidate
    }
    return prefix + shorten(body, budget) + link
  }

  fun threadIntro(count: Int): String =
    "🎮 L'actu jeux vidéo du ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM"))} en $count news 🧵👇"

  /** Thread numéroté : un tweet d'intro puis un tweet par news (« 2/5 », « 3/5 »…). */
  fun buildThread(items: List<NewsItem>, withLink: Boolean = true): List<String> {
    val total = items.size + 1
    return listOf("1/$total ${threadIntro(items.size)}") +
      items.mapIndexed { i, item -> buildTweet(item, prefix = "${i + 2}/$total ", withLink = withLink) }
  }

  /** Lien qui ouvre X avec le tweet déjà rempli. */
  fun intentUrl(text: String): String =
    "https://x.com/intent/post?text=" + URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}
