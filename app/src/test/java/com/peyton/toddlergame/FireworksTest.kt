package com.peyton.toddlergame

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

/** Fireworks for an 18-month-old, through [SoundSink] with a seeded Random. */
class FireworksTest {
  private val W = 2208f; private val H = 1840f
  private fun model(seed: Int = 1, w: Float = W, h: Float = H): Pair<FireworksModel, FakeSink> {
    val s = FakeSink(); val m = FireworksModel(s, Random(seed)); m.w = w; m.h = h; return m to s
  }
  private fun FireworksModel.advance(secs: Float, dt: Float = 1 / 60f, each: () -> Unit = {}) { var k = 0f; while (k < secs) { t += dt; step(dt); each(); k += dt } }
  private fun FakeSink.count(d: FloatArray) = plays.count { it.data === d }

  @Test fun anyTouchLaunchesARocketFromTheBottomUnderTheFingerRightAway() {
    val (m, s) = model(); val p = Offset(W * 0.3f, H * 0.4f)
    m.down(1, p)
    assertEquals(1, m.rockets.size); val r = m.rockets[0]
    assertEquals(p.x, r.x, 0.01f); assertEquals(H, r.y0, 0.01f); assertEquals(p.y, r.ty, 0.01f)
    assertEquals("whoosh in the same frame", 1, s.count(Sfx.whoosh)); assertEquals(listOf(false), s.haptics)
    m.advance(0.2f); assertTrue("climbing", m.rocketY(r) in p.y..H)
  }

  @Test fun burstsBigRightWhereTheFingerWas() {
    val (m, s) = model(); val p = Offset(W * 0.5f, H * 0.35f)
    m.down(1, p); m.up(1); m.advance(FireworksModel.FLIGHT + 0.02f)
    assertTrue(m.rockets.isEmpty()); assertTrue(m.sparks.size >= 40)
    assertEquals("the first burst is a ring: a big boom", 1, s.count(Sfx.bigBoom))
    m.advance(1.2f)
    val far = m.sparks.maxOf { hypot(it.x - p.x, it.y - p.y) } / min(W, H)
    assertTrue("burst radius $far of the short side (about 40% across)", far in 0.15f..0.3f)
    val cx = m.sparks.map { it.x }.average().toFloat(); assertEquals("centred on the finger", p.x, cx, W * 0.03f)
  }

  @Test fun fourShapesAndEightColoursTakeTurnsWithAFaceEverySixth() {
    val (m, _) = model(); val shapes = ArrayList<BurstShape>(); val colors = HashSet<Any>()
    repeat(12) { m.down(it.toLong(), Offset(W * (0.1f + (it % 8) * 0.1f), H * 0.3f)); m.up(it.toLong())
      shapes += m.rockets.last().shape; colors += m.rockets.last().color; m.advance(0.5f) }
    val R = FireworksModel.ROTATION
    assertEquals(R + R[0] + BurstShape.SMILEY + R.drop(1) + R.take(2) + BurstShape.CAT, shapes)
    assertEquals(8, colors.size)
  }

  @Test fun eachShapeHasItsOwnSound() {
    val (m, s) = model(); val heard = LinkedHashMap<BurstShape, FakeSink.Play?>()
    repeat(12) { i -> s.clear(); m.down(i.toLong(), Offset(W * (if (i % 2 == 0) 0.2f else 0.8f), H * 0.3f)); m.up(i.toLong())
      val sh = m.rockets.last().shape; m.advance(1.2f)
      when (sh) {
        BurstShape.RING -> assertEquals(1, s.count(Sfx.bigBoom))
        BurstShape.PEONY -> { assertEquals(1, s.count(Sfx.boom)); assertEquals(1, s.count(Sfx.crackle)) }
        BurstShape.HEART -> { assertEquals("heart twinkles", 1, s.count(Sfx.twinkle)); assertEquals(0, s.count(Sfx.crackle)) }
        BurstShape.STAR -> { assertEquals("star crackles", 1, s.count(Sfx.crackle)); assertTrue(s.plays.first { it.data === Sfx.crackle }.gain > 0.5f) }
        BurstShape.SMILEY -> assertEquals("smiley giggles", 1, s.count(Sfx.giggle))
        BurstShape.CAT -> assertEquals(listOf(Critter.CAT to 150L), s.animals)
      }
      heard[sh] = null }
    assertEquals(BurstShape.entries.toSet(), heard.keys)
  }

  @Test fun rapidTapsOnTheSameSpotBuildBiggerBursts() {
    val (m, _) = model(); val p = Offset(W * 0.5f, H * 0.4f); val powers = ArrayList<Int>()
    repeat(5) { m.down(it.toLong(), p + Offset(it * 10f, 0f)); m.up(it.toLong()); powers += m.rockets.last().power; m.advance(0.2f) }
    assertEquals(listOf(0, 1, 2, 3, 3), powers)
    m.advance(2f); assertTrue(m.sparks.isEmpty())
    m.down(9, p); assertEquals("a pause resets", 0, m.rockets.last().power)
    m.t += 0.1f; m.down(10, Offset(W * 0.05f, H * 0.9f)); assertEquals("far away resets", 0, m.rockets.last().power)
    // a maxed-out burst has 4 layers of sparks and flies wider
    val (a, _) = model(); val (b, _) = model()
    a.down(1, p); a.up(1); a.advance(FireworksModel.FLIGHT + 0.02f); val small = a.sparks.size
    repeat(4) { b.down(it.toLong(), p); b.up(it.toLong()); if (it < 3) b.advance(0.05f) }
    b.sparks.clear(); b.advance(FireworksModel.FLIGHT + 0.02f)
    assertTrue("bigger burst: ${b.sparks.size} vs $small", b.sparks.size >= small * 3)
    assertTrue(b.sparks.size <= FireworksModel.MAX_SPARKS)
  }

  @Test fun firefliesDriftOnScreenSilentlyAndGlowBetweenTaps() {
    val (m, s) = model(); m.advance(0.1f)
    assertEquals(FireworksModel.FIREFLIES, m.fireflies.size)
    val start = m.fireflies.map { it.x to it.y }
    m.advance(30f) { m.fireflies.forEach { assertTrue(it.x in 0f..W && it.y in 0f..H) } }
    assertNotEquals("they drift", start, m.fireflies.map { it.x to it.y })
    assertEquals(1f, m.fireflyGlow(), 0f); assertTrue(s.plays.isEmpty())
    m.down(1, Offset(W / 2, H / 2)); m.up(1); assertTrue("dim right after a tap", m.fireflyGlow() < 0.3f)
  }

  @Test fun everyShapeIsUnitSizedAndRoundish() {
    val (m, _) = model()
    for (sh in BurstShape.entries) { val d = m.shapeDirs(sh)
      assertTrue("$sh has sparks", d.size >= 40)
      val r = d.maxOf { hypot(it.x, it.y) }; assertTrue("$sh radius $r", r in 0.8f..1.15f)
      assertTrue("$sh spreads in every direction", d.any { it.x > 0.5f } && d.any { it.x < -0.5f } && d.any { it.y > 0.5f } && d.any { it.y < -0.5f }) }
  }

  @Test fun holdingKeepsFiringAndFollowsTheFinger() {
    val (m, _) = model(); var launched = 1
    m.down(7, Offset(W * 0.2f, H * 0.3f)); var seen = m.rockets.last()
    m.advance(0.5f) { m.rockets.lastOrNull()?.let { if (it !== seen) { seen = it; launched++ } } }
    m.move(7, Offset(W * 0.8f, H * 0.5f))
    m.advance(0.5f) { m.rockets.lastOrNull()?.let { if (it !== seen) { seen = it; launched++ } } }
    assertEquals("one at the touch, then one every 0.3 s", 4, launched)
    assertEquals("follows the finger", W * 0.8f, seen.x, 1f)
    m.up(7); val n = m.rockets.size; m.advance(1f); assertTrue(m.rockets.isEmpty()); assertTrue(n <= FireworksModel.MAX_ROCKETS)
  }

  @Test fun aFingerStuckDownStopsFiringAfterAFew() {
    // e.g. a lost release or a palm/case edge resting on the glass: it used to fire every 0.3 s forever
    val (m, s) = model(); for (id in 1L..5L) m.down(id, Offset(W * 0.15f * id, H * 0.4f))
    m.advance(60f)
    assertEquals("5 taps + ${FireworksModel.HOLD_MAX} repeats each", 5 * (1 + FireworksModel.HOLD_MAX), s.haptics.count { !it })
    assertEquals(0, m.autoFiring()); assertTrue(m.rockets.isEmpty())
    val n = s.plays.size; m.advance(30f); assertEquals("silent sky", n, s.plays.size)
    m.up(1); m.down(1, Offset(W / 2, H / 2)); assertEquals("a new touch fires again", 1, m.rockets.size)
  }

  @Test fun releasedOrCancelledFingersNeverFireAgain() {
    val (m, s) = model(); val f = FingerTracker(onDown = m::down, onMove = m::move, onUp = m::up)
    f.event(listOf(FingerChange(1, Offset(W / 3, H / 3), true, false, false), FingerChange(2, Offset(W / 2, H / 3), true, false, false)))
    m.advance(0.4f); f.releaseAll()   // gesture cancelled (system swipe, app paused)
    assertEquals(0, m.holding()); val n = s.haptics.count { !it }; m.advance(10f); assertEquals(n, s.haptics.count { !it })
  }

  @Test fun rocketsInFlightAreCappedEvenWithEveryFingerHeld() {
    val (m, _) = model(); for (id in 0L until 5L) m.down(id, Offset(W * 0.2f * id, H * 0.4f))
    m.advance(5f, dt = 1 / 30f) { assertTrue(m.rockets.size <= FireworksModel.MAX_ROCKETS) }
  }

  @Test fun palmSmackIsSafe() {
    val (m, s) = model()
    m.advance(5f) { if ((m.t * 60).toInt() % 3 == 0) for (f in 0L until 5L) { m.up(f); m.down(f, Offset(W * (0.2f + f * 0.15f), H * 0.5f)) }
      assertTrue("rockets capped", m.rockets.size <= FireworksModel.MAX_ROCKETS)
      assertTrue("sparks capped", m.sparks.size <= FireworksModel.MAX_SPARKS) }
    val whooshes = s.count(Sfx.whoosh); val booms = s.count(Sfx.boom)
    assertTrue("whoosh throttled: $whooshes", whooshes <= (5f / FireworksModel.WHOOSH_GAP).toInt() + 1)
    assertTrue("boom throttled: $booms", booms <= (5f / FireworksModel.BOOM_GAP).toInt() + 1)
    assertTrue("still booming", booms > 10)
  }

  @Test fun anOverfullSkyBurstsTheOldestRocketEarly() {
    val (m, _) = model()
    repeat(FireworksModel.MAX_ROCKETS) { m.down(it.toLong(), Offset(W * 0.5f, H * 0.3f)); m.up(it.toLong()) }
    val oldest = m.rockets.first(); assertTrue(m.sparks.isEmpty())
    m.down(99, Offset(W * 0.1f, H * 0.2f))
    assertEquals(FireworksModel.MAX_ROCKETS, m.rockets.size); assertFalse(oldest in m.rockets); assertTrue("burst, not dropped", m.sparks.isNotEmpty())
  }

  @Test fun skyGoesQuietAndNothingFiresByItself() {
    val (m, s) = model(); m.down(1, Offset(W / 2, H / 2)); m.up(1); m.advance(3f)
    assertTrue(m.sparks.isEmpty()); assertTrue(m.rockets.isEmpty()); assertTrue(m.bursts.isEmpty())
    val n = s.plays.size; m.advance(60f); assertEquals(n, s.plays.size)
  }

  @Test fun noMusicInFireworks() {
    assertNull(MusicDirector.trackFor(Mode.FIREWORKS, MusicSlot.entries.toSet(), 5) { true })
  }

  @Test fun badInputNeverCrashes() {
    val (m, _) = model()
    m.down(1, Offset(Float.NaN, 5f)); m.move(1, Offset(Float.POSITIVE_INFINITY, 0f)); m.up(42); m.move(5, Offset(1f, 1f))
    assertTrue(m.rockets.isEmpty())
    m.down(2, Offset(-1e6f, 1e6f)); m.advance(1f); m.step(Float.NaN); m.step(-1f); m.step(5f)
    assertTrue(m.sparks.all { it.x.isFinite() && it.y.isFinite() })
    val (unsized, _) = model(w = 1f, h = 1f); unsized.down(1, Offset(0f, 0f)); unsized.advance(1f); assertTrue(unsized.rockets.isEmpty())
  }

  @Test fun sameSeedSameShow() {
    val (a, _) = model(5); val (b, _) = model(5)
    for (m in listOf(a, b)) { m.down(1, Offset(W * 0.4f, H * 0.3f)); m.advance(1f); m.up(1); m.advance(0.3f) }
    assertEquals(a.sparks.map { it.x to it.y }, b.sparks.map { it.x to it.y })
    assertTrue(a.sparks.isNotEmpty())
  }
}
