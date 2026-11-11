package com.peyton.toddlergame

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

/** Every finger reported down is reported up exactly once, even when its release never arrives. */
class FingerTrackerTest {
  private val log = ArrayList<String>()
  private val f = FingerTracker(onDown = { id, _ -> log += "down$id" }, onMove = { id, _ -> log += "move$id" }, onUp = { log += "up$it" })
  private fun ch(id: Long, pressed: Boolean, was: Boolean, moved: Boolean = false) = FingerChange(id, Offset(id * 10f, 5f), pressed, was, moved)

  @Test fun normalTapAndDrag() {
    f.event(listOf(ch(1, true, false))); f.event(listOf(ch(1, true, true, moved = true))); f.event(listOf(ch(1, false, true)))
    assertEquals(listOf("down1", "move1", "up1"), log); assertTrue(f.held().isEmpty())
  }

  @Test fun twoFingersIndependently() {
    f.event(listOf(ch(1, true, false))); f.event(listOf(ch(1, true, true), ch(2, true, false)))
    f.event(listOf(ch(1, false, true), ch(2, true, true, moved = true))); f.event(listOf(ch(2, false, true)))
    assertEquals(listOf("down1", "down2", "up1", "move2", "up2"), log)
  }

  @Test fun aFingerThatVanishesWithoutAReleaseIsReleased() {
    f.event(listOf(ch(1, true, false), ch(2, true, false)))
    f.event(listOf(ch(2, true, true, moved = true)))   // finger 1 is just gone (lost/cancelled release)
    assertEquals(listOf("down1", "down2", "move2", "up1"), log); assertEquals(setOf(2L), f.held())
  }

  @Test fun cancelReleasesEveryone() {
    f.event(listOf(ch(1, true, false), ch(2, true, false), ch(3, true, false)))
    f.releaseAll(); f.releaseAll()
    assertEquals(listOf("down1", "down2", "down3", "up1", "up2", "up3"), log); assertTrue(f.held().isEmpty())
  }

  @Test fun aReleaseAlreadyConsumedStillCounts() {
    // pressed=false is what matters, not whether something else consumed the change first
    f.event(listOf(ch(1, true, false))); f.event(listOf(ch(1, false, false)))
    assertEquals(listOf("down1", "up1"), log)
  }

  @Test fun sixthFingerIgnoredAndNeverGetsUp() {
    f.event((1L..6L).map { ch(it, true, false) })
    assertEquals("over 5 pressed: none taken", emptyList<String>(), log)
    f.event((1L..5L).map { ch(it, true, false) })
    assertEquals((1..5).map { "down$it" }, log); log.clear()
    f.event(listOf(ch(9, true, true, moved = true)))
    assertEquals("untracked finger moving does nothing; the 5 vanished get up", (1..5).map { "up$it" }, log)
  }

  @Test fun duplicateDownIsOneFinger() {
    f.event(listOf(ch(1, true, false))); f.event(listOf(ch(1, true, false))); f.event(listOf(ch(1, false, true))); f.event(listOf(ch(1, false, true)))
    assertEquals(listOf("down1", "up1"), log)
  }
}
