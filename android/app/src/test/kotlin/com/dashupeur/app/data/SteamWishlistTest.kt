package com.dashupeur.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamWishlistTest {
  @Test
  fun parseSteamId_acceptsRawIdAndProfileUrl() {
    assertEquals("76561198000000000", SteamWishlist.parseSteamId(" 76561198000000000 "))
    assertEquals(
      "76561198000000000",
      SteamWishlist.parseSteamId("https://steamcommunity.com/profiles/76561198000000000/"),
    )
    assertEquals("", SteamWishlist.parseSteamId("monpseudo"))
    assertEquals("", SteamWishlist.parseSteamId(""))
  }

  @Test
  fun titleWithGame_prefixesOnlyWhenMissing() {
    assertEquals("Arkheron : Patch notes", SteamWishlist.titleWithGame("Arkheron", "Patch notes"))
    assertEquals(
      "Join an Arkheron Partner Server",
      SteamWishlist.titleWithGame("Arkheron", "Join an Arkheron Partner Server"),
    )
  }
}
