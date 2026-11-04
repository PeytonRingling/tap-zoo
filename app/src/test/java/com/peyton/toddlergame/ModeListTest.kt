package com.peyton.toddlergame

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The menu after the round-2 cuts: 8 kept tiles plus the new ones, Bedtime only from Parent settings, and old saved settings never break it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModeListTest {
  private val c = ApplicationProvider.getApplicationContext<Context>()

  @Test fun tilesAndBedtimeIsNotOne() {
    assertEquals(listOf(Mode.BALLOONS, Mode.ANIMALS, Mode.AQUARIUM, Mode.BUBBLES, Mode.VEHICLES, Mode.DRUMS, Mode.PEEKABOO, Mode.PAINT, Mode.FIREWORKS, Mode.FEED, Mode.WASH, Mode.STICKERS), Mode.TILES)
    assertFalse(Mode.BEDTIME in Mode.TILES)
    assertEquals(Mode.TILES + Mode.BEDTIME, Mode.entries)
  }

  @Test fun savedTilesKeepTheOnesThatStillExist() {
    assertEquals(setOf(Mode.BUBBLES, Mode.PAINT), enabledFrom(setOf("BUBBLES", "GARDEN", "PAINT", "SKY", "MUSIC")))
  }

  @Test fun savedSetWithOnlyRemovedOrHiddenModesShowsEveryTile() {
    assertEquals(Mode.TILES.toSet(), enabledFrom(setOf("GARDEN", "MUSIC", "SKY")))
    assertEquals("bedtime alone can't empty the menu", Mode.TILES.toSet(), enabledFrom(setOf("BEDTIME")))
    assertEquals(Mode.TILES.toSet(), enabledFrom(emptySet()))
    assertEquals(Mode.TILES.toSet(), enabledFrom(null))
    assertEquals(Mode.TILES.toSet(), enabledFrom(setOf("", "balloons", "NOT_A_MODE")))
  }

  @Test fun prefsFromAnOlderVersionLoadWithoutCrashing() {
    val sp = c.getSharedPreferences("toddler", Context.MODE_PRIVATE)
    sp.edit().clear().putStringSet("modes", setOf("BALLOONS", "GARDEN", "MUSIC", "SKY", "BEDTIME")).putBoolean("bsym", false).commit()
    Prefs.init(c)
    assertEquals(setOf(Mode.BALLOONS), Prefs.enabled)
    Prefs.enabled = setOf(Mode.BALLOONS, Mode.PAINT)
    assertEquals(setOf("BALLOONS", "PAINT"), sp.getStringSet("modes", null))
    sp.edit().clear().commit()
  }
}
