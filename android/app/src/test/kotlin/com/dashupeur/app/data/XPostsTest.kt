package com.dashupeur.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vérifie que l'Espace X Android rédige les mêmes tweets que la version web (xposts.py). */
class XPostsTest {
  private val url = "https://store.steampowered.com/news/app/1667040/view/681892101614469655"
  private val item = NewsItem(
    id = "a",
    title = "Dev Blog #6 - Taming and Companions Playtest starts now!",
    url = url,
    source = "Steam · Under a Rock",
    published = "",
    summary = "Greetings, explorers! We are delighted to announce that we are moving forward with a new phase " +
      "of testing and have once again opened applications for new testers for our Closed Playtest! More soon.",
    score = 10,
    tags = "playtest",
    status = "inbox",
  )
  private val body = "Dev Blog #6 - Taming and Companions Playtest starts now!\n\nWe are delighted to announce that " +
    "we are moving forward with a new phase of testing and have once again opened applications for new testers " +
    "for our Closed Playtest!"

  @Test fun tweetSkipsShortGreetingAndAddsLink() {
    val tweet = XPosts.buildTweet(item)
    assertEquals("$body\n\n🔗 $url", tweet)
    assertEquals(250, XPosts.tweetLength(tweet))
  }

  @Test fun tweetWithoutLink() = assertEquals(body, XPosts.buildTweet(item, withLink = false))

  @Test fun lengthCountsLinksAs23AndEmojisAs2() =
    assertEquals(45, XPosts.tweetLength("Mon tweet retouché 🎮 https://example.com"))

  @Test fun longTitleIsCutToFit() {
    val long = item.copy(title = "mot ".repeat(100), summary = "")
    val tweet = XPosts.buildTweet(long, prefix = "3/3 ")
    assertEquals("3/3 " + List(61) { "mot" }.joinToString(" ") + "…\n\n🔗 $url", tweet)
    assertTrue(XPosts.tweetLength(tweet) <= XPosts.TWEET_MAX)
  }

  @Test fun threadIsNumbered() {
    val thread = XPosts.buildThread(listOf(item, item.copy(id = "b")))
    assertEquals(3, thread.size)
    assertTrue(thread[0].startsWith("1/3 🧅 On épluche l'actu jeux vidéo du "))
    assertTrue(thread[1].startsWith("2/3 Dev Blog #6"))
    assertTrue(thread[2].startsWith("3/3 Dev Blog #6"))
  }
}
