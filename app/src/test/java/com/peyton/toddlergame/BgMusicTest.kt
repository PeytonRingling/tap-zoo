package com.peyton.toddlergame

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BgMusicTest {
  private val c = ApplicationProvider.getApplicationContext<Context>()
  @After fun tearDown() { BgMusic.stop(); BgMusic.musicGain = 0.8f }

  @Test fun findsShippedTracksByName() {
    listOf("music_menu", "music_balloons", "music_lullaby").forEach { assertTrue(it, BgMusic.has(c, it)) }
    assertFalse(BgMusic.has(c, "music_does_not_exist")); assertFalse(BgMusic.has(c, ""))
  }

  @Test fun missingOrUnplayableTrackNeverCrashes() {
    BgMusic.play(c, MusicPick("music_does_not_exist", 0.5f, 0.8f))
    BgMusic.pause(); BgMusic.resume(); BgMusic.duck(); BgMusic.setFade(0.5f); BgMusic.refresh()
    BgMusic.play(c, MusicPick("music_balloons", 0.45f, 0.8f)) // Robolectric's MediaPlayer can't decode: create may fail
    assertEquals("music_balloons", BgMusic.currentName)
    BgMusic.stop(); assertEquals("", BgMusic.currentName)
  }

  @Test fun musicVolumeIsSeparateFromSfxVolume() {
    BgMusic.play(c, MusicPick("music_menu", 0.5f, 0.35f))
    val sfx = Audio.volume
    BgMusic.musicGain = 1f; assertEquals(0.5f, BgMusic.level(), 1e-6f)
    Audio.volume = 0f; assertEquals(0.5f, BgMusic.level(), 1e-6f)
    BgMusic.musicGain = 0.4f; assertEquals(0.2f, BgMusic.level(), 1e-6f)
    Audio.volume = sfx
  }

  @Test fun prefsMigrateOldSwitchesAndPinMusicVolume() {
    val sp = c.getSharedPreferences("toddler", Context.MODE_PRIVATE)
    sp.edit().clear().putBoolean("mmus", false).putInt("vol", 2).commit()
    Prefs.init(c)
    assertEquals(MusicSlot.entries.toSet() - MusicSlot.MENU, Prefs.musicSlots)
    assertEquals(2, Prefs.musicVolume)
    Prefs.volume = 5; assertEquals("changing SFX volume must not move music", 2, Prefs.musicVolume)
    Prefs.musicSlots = setOf(MusicSlot.BUBBLES); assertEquals(setOf(MusicSlot.BUBBLES), Prefs.musicSlots)
    sp.edit().clear().putStringSet("musslots", setOf("BUBBLES")).commit()   // saved by v11, before Wash existed
    assertEquals("a new mode's music starts on after the update", setOf(MusicSlot.BUBBLES, MusicSlot.WASH, MusicSlot.POND), Prefs.musicSlots)
    sp.edit().clear().putStringSet("musslots", setOf("WASH")).putStringSet("musknown", setOf("MENU", "BALLOONS", "BUBBLES", "WASH")).commit()   // saved by v12
    assertEquals("Pond's music starts on after the v13 update", setOf(MusicSlot.WASH, MusicSlot.POND), Prefs.musicSlots)
    sp.edit().clear().commit()
  }
}
