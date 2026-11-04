package com.peyton.toddlergame

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Wash the animal for an 18-month-old, through [SoundSink] with a seeded Random. */
class WashTest {
  private val W = 2208f; private val H = 1840f
  private fun model(w: Float = W, h: Float = H): Pair<WashModel, FakeSink> {
    val s = FakeSink(); val m = WashModel(s, Random(2)); m.w = w; m.h = h; return m to s
  }
  private fun WashModel.advance(secs: Float, dt: Float = 1 / 60f, each: () -> Unit = {}) { var k = 0f; while (k < secs) { t += dt; step(dt); each(); k += dt } }
  private fun FakeSink.count(d: FloatArray) = plays.count { it.data === d }
  /** Taps mud until the animal is clean (one quick tap per splat per pass). */
  private fun WashModel.washClean() { repeat(WashModel.RUBS) { splats.forEachIndexed { i, s -> if (phase == WashPhase.DIRTY && s.dirt > 0) { t += 0.1f; down(i.toLong(), splatAt(s)); up(i.toLong()) } } } }

  @Test fun startsWithOneBigMuddyAnimal() {
    for ((w, h) in listOf(W to H, 1233f to 2391f)) {
      val (m, _) = model(w, h)
      assertEquals(WashModel.SPLATS, m.splats.size); assertEquals(1f, m.dirtLeft(), 0f)
      assertTrue("animal about 60% of the short side", m.radius * 2 >= kotlin.math.min(w, h) * 0.58f)
      m.splats.forEach { val p = m.splatAt(it); assertTrue("mud on screen", p.x in 0f..w && p.y in 0f..h); assertTrue("mud above the tub", it.dy + it.r <= 0.55f) }
    }
  }

  @Test fun tappingMudScrubsItWithASqueakAndFoam() {
    val (m, s) = model(); val sp = m.splats[0]
    m.down(1, m.splatAt(sp)); m.up(1)
    assertEquals(WashModel.RUBS - 1, sp.dirt); assertEquals(1, s.count(Sfx.scrub)); assertTrue(m.foam.isNotEmpty())
  }

  @Test fun rubbingCountsByDistanceNotByEveryPixel() {
    val (m, _) = model(); val sp = m.splats[3]; val p = m.splatAt(sp)
    m.down(1, p); val after1 = sp.dirt
    m.move(1, p + Offset(2f, 0f)); assertEquals("a wobble isn't a rub", after1, sp.dirt)
    m.t += 0.1f; m.move(1, p + Offset(m.radius * 0.13f, 0f)); assertEquals(after1 - 1, sp.dirt)
  }

  @Test fun touchingAnywhereElseStillFoamsAndPlays() {
    val (m, s) = model(); m.down(1, Offset(10f, 10f))
    assertTrue(m.foam.isNotEmpty()); assertEquals(1, s.count(Sfx.plip)); assertEquals(1f, m.dirtLeft(), 0f)
  }

  @Test fun mostlyCleanGetsAShowerThenTheNextMuddyFriendWalksIn() {
    val (m, s) = model(); val first = m.critter
    m.washClean()
    assertEquals("tap-only wash works", WashPhase.CLEAN, m.phase); assertEquals(1, m.washed)
    assertEquals(1, s.count(Sfx.shower)); assertEquals(listOf(first to 300L), s.animals)
    m.advance(WashModel.CELEBRATE + 0.05f); assertEquals(WashPhase.ENTERING, m.phase)
    assertNotEquals(first, m.critter); assertEquals("muddy again", 1f, m.dirtLeft(), 0f)
    assertTrue("walks in from the right", m.center.x > W * 0.5f)
    m.advance(WashModel.ENTER + 0.05f); assertEquals(WashPhase.DIRTY, m.phase); assertEquals(W * 0.5f, m.center.x, 1f)
  }

  @Test fun touchesDuringTheCelebrationDontBreakAnything() {
    val (m, s) = model(); m.washClean(); assertEquals(WashPhase.CLEAN, m.phase)
    s.clear(); m.t += 0.2f; m.down(5, m.center); m.up(5)
    assertTrue("still a sound", s.plays.isNotEmpty()); assertEquals(1, m.washed)
  }

  @Test fun aLongWashScrubsTwiceAsHard() {
    val (m, _) = model(); m.advance(WashModel.STALL + 1f)
    val sp = m.splats[5]; m.down(1, m.splatAt(sp)); assertEquals(WashModel.RUBS - 2, sp.dirt)
  }

  @Test fun palmSmackIsSafe() {
    val (m, s) = model()
    m.advance(20f) { if ((m.t * 60).toInt() % 2 == 0) for (f in 0L until 5L) { m.up(f); val a = m.t * 2f + f * 1.26f; m.down(f, m.center + Offset(kotlin.math.cos(a) * m.radius * 0.5f, kotlin.math.sin(a) * m.radius * 0.45f)) }
      assertTrue(m.foam.size <= WashModel.MAX_FOAM); assertTrue(m.parts.list.size <= 300) }
    assertTrue("washed animals one after another", m.washed >= 2)
    assertTrue("scrub throttled", s.count(Sfx.scrub) + s.count(Sfx.plip) <= (20f / WashModel.SCRUB_GAP).toInt() + 1)
  }

  @Test fun badInputNeverCrashes() {
    val (m, _) = model()
    m.down(1, Offset(Float.NaN, 0f)); m.move(1, Offset(0f, Float.POSITIVE_INFINITY)); m.move(9, Offset(1f, 1f)); m.up(42)
    m.step(Float.NaN); m.step(-1f); m.step(5f); m.advance(1f)
    assertTrue(m.foam.isEmpty()); assertEquals(1f, m.dirtLeft(), 0f)
    val s = FakeSink(); val u = WashModel(s, Random(1)); u.down(1, Offset(0f, 0f)); u.step(0.1f); assertTrue(s.plays.isEmpty())
  }
}
