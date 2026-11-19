package com.dashupeur.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeparateTopicTest {
  @Test
  fun gtaTitles_goToGtaSection() {
    listOf(
      "You Bus Can Be CANCELED In GTA 6",
      "GTA6 review",
      "Grand Theft Auto VI est sorti",
      "Rockstar Games confirme une mise à jour",
    ).forEach { assertEquals(it, "🚗 GTA", Config.separateTopic(it)) }
  }

  @Test
  fun otherTitles_stayInMainList() {
    listOf("Aion 2 makes a sweeping change", "Agatha MMO", "Rockstar Energy sponsorise un tournoi MMO")
      .forEach { assertNull(it, Config.separateTopic(it)) }
  }
}
