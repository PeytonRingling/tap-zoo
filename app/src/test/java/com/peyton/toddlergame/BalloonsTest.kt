package com.peyton.toddlergame

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** The Balloons overhaul (stream mode, shapes, surprises, combos, idle show), through [SoundSink] with a seeded Random. */
class BalloonsTest {
  private val W = 2208f; private val H = 1840f          // Fold inner, px
  private val FW = 1233f; private val FH = 2391f        // Fold outer (folded), px

  private fun model(seed: Int = 1, stream: Boolean = true, symbols: Boolean = true, w: Float = W, h: Float = H): Pair<BalloonModel, FakeSink> {
    val s = FakeSink(); val m = BalloonModel(s, Random(seed), stream = stream, symbols = symbols); m.w = w; m.h = h; return m to s
  }
  private fun BalloonModel.advance(secs: Float, dt: Float = 1 / 60f, each: () -> Unit = {}) { var k = 0f; while (k < secs) { t += dt; step(dt); each(); k += dt } }
  /** Keeps the model "touched" so idle behaviour stays out of the way. */
  private fun BalloonModel.busy(secs: Float, each: () -> Unit = {}) = advance(secs) { touched(); each() }
  private fun BalloonModel.plain(x: Float = w * 0.5f, y: Float = h * 0.25f) = make(x, y, kind = BKind.ROUND)

  @Test fun startsFullAndSpreadOut() {
    val (m, s) = model(); m.advance(0.05f)
    assertEquals(7, m.liveCount()); assertTrue(s.plays.isEmpty())
    val xs = m.balloons.map { it.x }.sorted()
    assertTrue("spread across the width", xs.first() < W * 0.25f && xs.last() > W * 0.75f)
    assertEquals("one see-through animal balloon to aim for", 1, m.balloons.count { it.kind == BKind.ANIMAL })
    assertTrue(m.balloons.none { it.kind == BKind.GIANT || it.kind == BKind.GOLDEN })
  }

  @Test fun streamKeepsSixToTwelveThroughAPoppingSpree() {
    for ((w, h) in listOf(W to H, FW to FH)) {
      val (m, _) = model(w = w, h = h); m.busy(3f)
      val target = m.targetMax; assertTrue(target in BalloonModel.MIN_LIVE..BalloonModel.MAX_LIVE)
      var minSeen = 99; var maxSeen = 0; var k = 0
      m.busy(120f) {
        if (++k % 30 == 0) m.balloons.firstOrNull { it.live && it.y < h }?.let { m.pop(it) }   // a pop every 0.5 s
        val n = m.liveCount(); maxSeen = max(maxSeen, n); if (k > 120) minSeen = min(minSeen, n)
        assertTrue("never over the hard cap", n <= BalloonModel.HARD_CAP)
      }
      assertTrue("min $minSeen", minSeen >= BalloonModel.MIN_LIVE - 2)   // a burst can briefly dip below 6, the stream refills fast
      assertTrue("max $maxSeen vs $target", maxSeen <= target + BalloonModel.MINIS)  // only a giant's minis go over target
      m.balloons.clear(); m.busy(4f); assertTrue("refills an empty sky", m.liveCount() >= BalloonModel.MIN_LIVE)
    }
  }

  @Test fun foldedScreenGetsMoreSmallerOnesInnerGetsFewerBigger() {
    val (a, _) = model(); val (b, _) = model(w = FW, h = FH)
    assertEquals(10, a.targetMax); assertEquals(12, b.targetMax)
    a.advance(0.05f); a.balloons.filter { it.kind != BKind.GOLDEN }.forEach { assertTrue(it.r / H in 0.094f..0.146f) }
  }

  @Test fun classicKeepsOnlyWhatYouMake() {
    val (m, s) = model(stream = false); m.busy(30f)
    assertEquals(3, m.liveCount()); assertTrue(s.plays.isEmpty())
    assertTrue(m.balloons.none { it.kind == BKind.GIANT })
  }

  @Test fun comboClimbsTheScaleAndResetsAfterAPause() {
    val (m, s) = model(); m.busy(1f)
    val bs = (0 until 4).map { m.plain(W * (0.2f + it * 0.2f)) }; s.clear()
    bs.forEach { m.t += 0.3f; m.pop(it) }
    val notes = s.plays.map { it.data }.filter { it in Sfx.comboNotes }
    assertEquals((0 until 4).map { Sfx.comboNotes[it] }, notes)
    m.t += 1.3f; s.clear(); m.pop(m.plain())
    assertSame("streak reset", Sfx.comboNotes[0], s.plays[1].data)
    assertEquals(1, m.streak)
  }

  @Test fun fiveInARowGetsOneTaDaNotMore() {
    val (m, s) = model(); m.busy(1f)
    val bs = (0 until 9).map { m.plain(W * (0.1f + it * 0.09f), H * 0.2f) }; s.clear()
    bs.forEachIndexed { i, b -> m.t += 0.2f; m.pop(b); if (i == 3) assertTrue(s.plays.none { it.data === Sfx.tada }) }
    assertEquals(1, s.plays.count { it.data === Sfx.tada }); assertEquals(1, m.ribbons.size)
    assertTrue("no score anywhere", s.plays.all { it.data in Sfx.comboNotes || it.data === Sfx.tada || it.data === Sfx.balloonPop })
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

  @Test fun giantBurstsIntoSevenMinisWithAFanfare() {
    val (m, s) = model(); m.busy(1f)
    val g = m.make(W / 2, H / 2, kind = BKind.GIANT); s.clear()
    val minisBefore = m.balloons.count { it.kind == BKind.MINI }
    m.pop(g); m.busy(0.1f)
    assertEquals(BalloonModel.MINIS, m.balloons.count { it.kind == BKind.MINI } - minisBefore)
    assertTrue(s.plays.any { it.data === Sfx.fanfare }); assertTrue(m.shakeUntil > 0f)
    m.busy(3f); m.balloons.filter { it.kind == BKind.MINI }.forEach { assertTrue("minis settle on screen", it.x in 0f..W && it.y in 0f..H * 1.1f) }
  }

  @Test fun giantArrivesEvery25To40sOneAtATime() {
    val (m, _) = model(); val seen = HashSet<Balloon>(); val arrived = ArrayList<Float>()
    m.busy(130f) {
      m.balloons.filter { it.kind == BKind.GIANT }.forEach { if (seen.add(it)) arrived += m.t }
      assertTrue(m.balloons.count { it.kind == BKind.GIANT && !it.leaving } <= 1)
      m.balloons.firstOrNull { it.kind == BKind.GIANT && it.live && m.t - it.born > 3f }?.let { m.pop(it) }   // pop it once it's up
    }
    assertTrue("first after 25-40 s: $arrived", arrived.first() in 25f..40.1f)
    arrived.zipWithNext { a, b -> assertTrue("gap ${b - a}", b - a in 25f..40.1f) }
    assertTrue(arrived.size in 3..5)
    // an unpopped giant isn't joined by a second one
    val (n, _) = model(seed = 2); n.busy(130f); assertEquals(1, n.balloons.count { it.kind == BKind.GIANT })
  }

  @Test fun goldenStarSparkles() {
    val (m, s) = model(); m.busy(1f); val b = m.make(W / 2, H * 0.3f, kind = BKind.GOLDEN); s.clear()
    m.pop(b); assertTrue(s.plays.any { it.data === Sfx.sparkle })
  }

  @Test fun surprisesStayRare() {
    val (m, _) = model(seed = 5); m.busy(2f)
    var animals = 0; var golden = 0; var total = 0
    repeat(400) { val b = m.make(W * ((it * 37) % 100) / 100f, H * 0.4f); total++
      if (b.kind == BKind.ANIMAL) animals++; if (b.kind == BKind.GOLDEN) golden++
      assertTrue(m.balloons.count { it.live && it.kind == BKind.ANIMAL } <= BalloonModel.MAX_ANIMAL_BALLOONS)
      assertTrue(m.balloons.count { it.live && it.kind == BKind.GOLDEN } <= 1)
      if (it % 3 == 0) m.balloons.firstOrNull { it.live }?.let { b2 -> m.pop(b2); m.t += 0.1f; m.step(0.1f) } }
    assertTrue("animals $animals/$total", animals in 10..80); assertTrue("golden $golden/$total", golden in 2..30)
  }

  @Test fun symbolsOffMeansNoSymbols() {
    val (m, _) = model(symbols = false); m.busy(1f); repeat(50) { m.make(W / 2, H / 2) }
    assertTrue(m.balloons.all { it.symbol == 0 })
    val (n, _) = model(symbols = true); n.busy(1f); repeat(80) { n.make(W / 2, H / 2) }
    assertTrue(n.balloons.any { it.symbol != 0 }); assertTrue("symbols only on round ones", n.balloons.all { it.symbol == 0 || it.kind == BKind.ROUND })
  }

  @Test fun idleWigglesThenPutsOnAShowThenGoesQuiet() {
    val (m, s) = model(); m.touched(); m.advance(4.9f); assertNull(m.wiggle); assertTrue(s.plays.isEmpty())
    m.advance(0.3f); assertNotNull(m.wiggle); assertEquals(1, s.plays.count { it.data === Sfx.squeak })
    m.advance(14.5f); val popsBefore = m.pops
    m.advance(1f); assertEquals("auto-show pops two", popsBefore + 2, m.pops)
    m.advance(80f)
    assertEquals("squeaks capped at 3", 3, s.plays.count { it.data === Sfx.squeak })
    val later = s.plays.size; val p = m.pops; m.advance(60f)
    assertEquals("after 90 s idle: silent", later, s.plays.size); assertEquals(p, m.pops)
    m.tap(Offset(-50f, -50f)); assertNull("touch resets", m.wiggle)
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
    assertEquals(a.balloons.map { Triple(it.kind, it.color, it.label) }, b.balloons.map { Triple(it.kind, it.color, it.label) })
  }
}
