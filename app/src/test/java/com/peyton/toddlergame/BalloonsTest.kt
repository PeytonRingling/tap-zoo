package com.peyton.toddlergame

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Balloons for an 18-month-old (stream mode, plain round balloons, a rare animal, soft combo, gentle idle), through [SoundSink]
 *  with a seeded Random. */
class BalloonsTest {
  private val W = 2208f; private val H = 1840f          // Fold inner, px
  private val FW = 1233f; private val FH = 2391f        // Fold outer (folded), px

  private fun model(seed: Int = 1, stream: Boolean = true, w: Float = W, h: Float = H): Pair<BalloonModel, FakeSink> {
    val s = FakeSink(); val m = BalloonModel(s, Random(seed), stream = stream); m.w = w; m.h = h; return m to s
  }
  private fun BalloonModel.advance(secs: Float, dt: Float = 1 / 60f, each: () -> Unit = {}) { var k = 0f; while (k < secs) { t += dt; step(dt); each(); k += dt } }
  /** Keeps the model "touched" so idle behaviour stays out of the way. */
  private fun BalloonModel.busy(secs: Float, each: () -> Unit = {}) = advance(secs) { touched(); each() }
  private fun BalloonModel.plain(x: Float = w * 0.5f, y: Float = h * 0.25f) = make(x, y, kind = BKind.ROUND)

  @Test fun startsWithAFewBigPlainBalloonsSpreadOut() {
    for ((w, h) in listOf(W to H, FW to FH)) {
      val (m, s) = model(w = w, h = h); m.advance(0.05f)
      assertEquals(m.targetMax, m.liveCount()); assertTrue(s.plays.isEmpty())
      val xs = m.balloons.map { it.x }.sorted()
      assertTrue("spread across the width", xs.first() < w * 0.25f && xs.last() > w * 0.75f)
      assertTrue("all plain round at the start", m.balloons.all { it.kind == BKind.ROUND })
    }
  }

  @Test fun fiveToSevenOnScreen() {
    val (a, _) = model(); val (b, _) = model(w = FW, h = FH)
    assertEquals("Fold inner", 6, a.targetMax); assertEquals("folded", 7, b.targetMax)
    assertTrue(BalloonModel.MIN_LIVE == 5 && BalloonModel.MAX_LIVE == 7)
  }

  @Test fun streamKeepsFiveToSevenThroughAPoppingSpree() {
    for ((w, h) in listOf(W to H, FW to FH)) {
      val (m, _) = model(w = w, h = h); m.busy(3f)
      var minSeen = 99; var maxSeen = 0; var k = 0
      m.busy(120f) {
        if (++k % 30 == 0) m.balloons.firstOrNull { it.live && it.y < h }?.let { m.pop(it) }   // a pop every 0.5 s
        val n = m.liveCount(); maxSeen = max(maxSeen, n); if (k > 180) minSeen = min(minSeen, n)
      }
      assertTrue("min $minSeen", minSeen >= BalloonModel.MIN_LIVE - 2)   // a pop spree briefly dips, the stream refills
      assertTrue("max $maxSeen vs ${m.targetMax}", maxSeen <= m.targetMax + 1)
      m.balloons.clear(); m.busy(5f); assertTrue("refills an empty sky", m.liveCount() >= BalloonModel.MIN_LIVE)
    }
  }

  @Test fun balloonsAreBigAndOnlyRoundOrAnimal() {
    val (m, _) = model(seed = 3); m.busy(1f)
    repeat(300) { m.make(W * ((it * 37) % 100) / 100f, H * 0.4f); if (it % 2 == 0) m.balloons.firstOrNull { b -> b.live }?.let { b -> m.pop(b) }; m.t += 0.1f; m.step(0.1f) }
    m.balloons.forEach { assertTrue("r ${it.r / H}", it.r / H in BalloonModel.R_MIN..(BalloonModel.R_MIN + BalloonModel.R_SPAN)) }
    assertTrue(m.balloons.all { it.kind == BKind.ROUND || it.kind == BKind.ANIMAL })
  }

  @Test fun tappingAPalmFullOfFingersNeverOverfills() {
    val (m, _) = model(); m.busy(1f)
    repeat(200) { m.t += 0.13f; m.tap(Offset(W * ((it * 53) % 100) / 100f, H * ((it * 29) % 100) / 100f)); m.step(0.016f)
      assertTrue("never over the hard cap", m.liveCount() <= BalloonModel.HARD_CAP) }
  }

  @Test fun classicKeepsOnlyWhatYouMake() {
    val (m, s) = model(stream = false); m.busy(30f)
    assertEquals(3, m.liveCount()); assertTrue(s.plays.isEmpty())
  }

  @Test fun quickPopsClimbTheScaleSoftlyAndResetAfterAPause() {
    val (m, s) = model(); m.busy(1f)
    val bs = (0 until 4).map { m.plain(W * (0.2f + it * 0.2f)) }; s.clear()
    bs.forEach { m.t += 0.3f; m.pop(it) }
    val notes = s.plays.filter { it.data in Sfx.comboNotes }
    assertEquals((0 until 4).map { Sfx.comboNotes[it] }, notes.map { it.data })
    assertTrue("subtle: well under the pop", notes.all { it.gain <= BalloonModel.COMBO_GAIN })
    m.t += 1.3f; s.clear(); m.pop(m.plain())
    assertSame("streak reset", Sfx.comboNotes[0], s.plays[1].data)
    assertEquals(1, m.streak)
  }

  @Test fun aLongStreakPlaysOnlyPopsAndNotes() {
    val (m, s) = model(); m.busy(1f)
    s.clear()
    repeat(12) { m.t += 0.2f; m.pop(m.plain(W * (0.05f + it * 0.08f), H * 0.2f)) }
    assertEquals("one pop + one note each, no jingle", 24, s.plays.size)
    assertTrue(s.plays.all { it.data in Sfx.comboNotes || it.data === Sfx.balloonPop })
  }

  @Test fun everyNoteIsInGMajorPentatonic() {
    val g = setOf(7, 9, 11, 2, 4)
    assertTrue(Sfx.BALLOON_KEY.all { it % 12 in g }); for (i in 1 until Sfx.BALLOON_KEY.size) assertTrue(Sfx.BALLOON_KEY[i] > Sfx.BALLOON_KEY[i - 1])
  }

  @Test fun squashThenBurstWithConfetti() {
    val (m, _) = model(); m.busy(1f); val b = m.plain(); val before = m.parts.list.size
    m.pop(b); m.pop(b)
    assertEquals("double pop counts once", 1, m.pops); assertTrue(b in m.balloons); assertFalse(b.live)
    m.t += 0.03f; m.step(0.03f); assertTrue("still squashing", b in m.balloons)
    m.t += 0.05f; m.step(0.05f); assertFalse(b in m.balloons)
    assertTrue(m.parts.list.size > before + 20); assertTrue(m.flashes.isNotEmpty())
  }

  @Test fun animalBalloonPlaysItsAnimalAndParachutesDown() {
    val (m, s) = model(); m.busy(1f)
    val b = m.make(W / 2, H * 0.3f, kind = BKind.ANIMAL); s.clear(); m.parachutes.clear()
    m.pop(b); m.busy(0.1f)
    assertEquals(listOf(b.critter to 0L), s.animals)
    assertEquals(1, m.parachutes.size); assertEquals(b.critter, m.parachutes[0].critter)
    val y0 = m.parachutes[0].y; m.busy(2f); assertTrue("floats down", m.parachutes[0].y > y0)
    m.busy(25f); assertTrue("gone eventually", m.parachutes.none { it.critter == b.critter && it.t0 < 2f })
  }

  @Test fun animalIsARareTreatOneAtATime() {
    val (m, _) = model(seed = 5); m.busy(2f)
    var animals = 0; val total = 3000
    repeat(total) { val b = m.make(W * ((it * 37) % 100) / 100f, H * 0.4f)
      if (b.kind == BKind.ANIMAL) { animals++; m.pop(b) }
      assertTrue(m.balloons.count { it.live && it.kind == BKind.ANIMAL } <= 1)
      m.balloons.firstOrNull { it.live }?.let { b2 -> m.pop(b2) }; m.t += 0.1f; m.step(0.1f) }
    val expected = total / BalloonModel.ANIMAL_ODDS.toFloat()
    assertTrue("animals $animals vs ~$expected", animals in (expected * 0.6f).toInt()..(expected * 1.4f).toInt())
  }

  @Test fun idleWigglesGentlyButNeverPopsByItself() {
    val (m, s) = model(); m.touched(); m.advance(4.9f); assertNull(m.wiggle); assertTrue(s.plays.isEmpty())
    m.advance(0.3f); assertNotNull(m.wiggle); assertEquals(1, s.plays.count { it.data === Sfx.squeak })
    m.advance(120f)
    assertEquals("nothing pops while nobody touches", 0, m.pops)
    assertEquals("squeaks capped at 3", 3, s.plays.count { it.data === Sfx.squeak })
    val later = s.plays.size; m.advance(60f)
    assertEquals("after 90 s idle: silent", later, s.plays.size)
    m.tap(Offset(-50f, -50f)); assertNull("touch resets", m.wiggle)
  }

  @Test fun risersAreSlowAndHover() {
    val (m, _) = model(); m.busy(1f)
    val b = m.make(W / 2, H / 2, rising = true, kind = BKind.ROUND)
    assertTrue("starts below the screen", b.y > H)
    m.busy(1f); assertTrue("still on its way up after 1 s", b.y > H * 0.6f)
    m.busy(8f); assertTrue("settled in the sky", b.live && b.y in 0f..H)
  }

  @Test fun particlesStayCappedInAStorm() {
    val (m, _) = model(); m.busy(1f)
    m.busy(10f) { m.balloons.filter { it.live }.take(3).forEach { m.pop(it) }; repeat(2) { m.make(W / 2, H / 2) }; assertTrue(m.parts.list.size <= 600) }
  }

  @Test fun badInputNeverCrashes() {
    val (m, _) = model(); m.tap(Offset(10f, 10f)); m.step(0.016f)
    m.tap(Offset(Float.NaN, 5f)); m.drag(Offset(Float.POSITIVE_INFINITY, 0f)); m.tap(Offset(-1e6f, 1e6f))
    m.t += 5f; m.step(5f); m.step(Float.NaN); m.step(-1f)
    assertTrue(m.balloons.all { it.x.isFinite() && it.y.isFinite() })
    val (tiny, _) = model(w = 3f, h = 3f); tiny.advance(1f); tiny.tap(Offset(1f, 1f))
    val (unsized, _) = model(w = 1f, h = 1f); unsized.advance(1f); unsized.tap(Offset(0f, 0f)); assertTrue(unsized.balloons.isEmpty())
  }

  @Test fun sameSeedSameGame() {
    val (a, _) = model(7); val (b, _) = model(7); a.advance(30f); b.advance(30f)
    assertEquals(a.balloons.map { Triple(it.kind, it.color, it.r) }, b.balloons.map { Triple(it.kind, it.color, it.r) })
  }
}
