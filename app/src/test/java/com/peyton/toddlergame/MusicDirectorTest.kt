package com.peyton.toddlergame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicDirectorTest {
  private val all = MusicSlot.entries.toSet()
  private val shipped = setOf("music_menu", "music_balloons", "music_lullaby")
  private fun pick(screen: Mode?, on: Set<MusicSlot> = all, vol: Int = 4, files: Set<String> = shipped) =
    MusicDirector.trackFor(screen, on, vol) { it in files }

  @Test fun everyModeGetsTheRightTrackOrSilence() {
    assertEquals("music_menu", pick(null)?.name)
    assertEquals("music_balloons", pick(Mode.BALLOONS)?.name)
    assertEquals("music_lullaby", pick(Mode.BEDTIME)?.name)
    // Bubbles prefers its own track and falls back to the Balloons one while it isn't packaged.
    assertEquals("music_balloons", pick(Mode.BUBBLES)?.name)
    assertEquals("music_bubbles", pick(Mode.BUBBLES, files = shipped + "music_bubbles")?.name)
    val silent = Mode.entries - setOf(Mode.BALLOONS, Mode.BUBBLES, Mode.BEDTIME)
    silent.forEach { assertNull("$it", pick(it)) }
  }

  @Test fun switchesOffSilenceOnlyTheirSlot() {
    for (off in MusicSlot.entries) {
      val on = all - off
      val screen = when (off) { MusicSlot.MENU -> null; MusicSlot.BALLOONS -> Mode.BALLOONS; MusicSlot.BUBBLES -> Mode.BUBBLES }
      assertNull("$off", pick(screen, on))
      (MusicSlot.entries - off).forEach { other ->
        val s = when (other) { MusicSlot.MENU -> null; MusicSlot.BALLOONS -> Mode.BALLOONS; MusicSlot.BUBBLES -> Mode.BUBBLES }
        assertTrue("$other with $off off", pick(s, on) != null)
      }
    }
    // The lullaby is part of Bedtime, not a switchable slot.
    assertEquals("music_lullaby", pick(Mode.BEDTIME, emptySet())?.name)
  }

  @Test fun musicVolumeZeroSilencesEverything() {
    (Mode.entries + listOf<Mode?>(null)).forEach { assertNull("$it", pick(it, vol = 0)) }
  }

  @Test fun missingOrBrokenFilesMeanSilenceNotACrash() {
    assertNull(pick(Mode.BALLOONS, files = emptySet()))
    assertNull(MusicDirector.trackFor(Mode.BALLOONS, all, 4) { throw IllegalStateException("resources gone") })
  }

  @Test fun playModesDuckLightlyQuietOnesDeeper() {
    assertEquals(0.8f, pick(Mode.BALLOONS)!!.duckDepth, 0f)
    assertEquals(0.8f, pick(Mode.BUBBLES)!!.duckDepth, 0f)
    assertEquals(0.35f, pick(null)!!.duckDepth, 0f)
    assertEquals(0.35f, pick(Mode.BEDTIME)!!.duckDepth, 0f)
    MusicDirector.TABLE.values.forEach { assertTrue(it.level in 0f..1f && it.duckDepth in 0f..1f) }
  }

  @Test fun migrationKeepsOldSwitchesAndAddsBubbles() {
    assertEquals(all, MusicDirector.migrate(null, null, null))
    assertEquals(all, MusicDirector.migrate(null, true, true))
    assertEquals(all - MusicSlot.MENU, MusicDirector.migrate(null, false, true))
    assertEquals(all - MusicSlot.BALLOONS, MusicDirector.migrate(null, true, false))
    assertEquals(setOf(MusicSlot.BUBBLES), MusicDirector.migrate(null, false, false))
    // Once the new set is saved it wins, even over old keys; junk names are ignored; empty means all off.
    assertEquals(setOf(MusicSlot.MENU), MusicDirector.migrate(setOf("MENU", "NOPE"), false, false))
    assertEquals(emptySet<MusicSlot>(), MusicDirector.migrate(emptySet(), true, true))
  }

  @Test fun duckFallsFastHoldsThenRecoversSmoothly() {
    val d = Ducker(); d.depth = 0.8f
    assertTrue(!d.step(0, 16f)); assertEquals(1f, d.k, 0f) // idle: nothing to do
    d.duck(1000)
    var t = 1000L; var prev = 1f
    while (t < 1060) { t += 10; d.step(t, 10f); assertTrue(d.k <= prev && d.k >= 0.8f); prev = d.k }
    assertEquals(0.8f, d.k, 1e-4f) // reached depth within the 60 ms attack
    while (t < 1590) { t += 10; d.step(t, 10f); assertEquals(0.8f, d.k, 1e-4f) } // held until 1000 + 600 ms
    var steps = 0
    while (d.step(t, 10f)) { t += 10; assertTrue(d.k >= prev - 1e-6f && d.k <= 1f); prev = d.k; steps++ ; assertTrue(steps < 200) }
    assertEquals(1f, d.k, 0f)
    assertTrue("release should take ~500 ms, took ${steps * 10}", steps in 40..60)
  }

  @Test fun rapidTapsKeepMusicDownInsteadOfPumping() {
    val d = Ducker(); d.depth = 0.35f
    var t = 0L
    repeat(100) { i -> if (i % 15 == 0) d.duck(t); t += 16; d.step(t, 16f); if (t > 100) assertEquals(0.35f, d.k, 1e-4f) }
  }

  @Test fun duckerSurvivesBadInput() {
    val d = Ducker(); d.depth = 5f; assertEquals(1f, d.depth, 0f)
    d.depth = -1f; d.duck(0); d.step(1, 1e9f); assertTrue(d.k in 0f..1f)
    d.step(2, -50f); assertTrue(d.k in 0f..1f)
    d.reset(); assertEquals(1f, d.k, 0f)
  }
}
