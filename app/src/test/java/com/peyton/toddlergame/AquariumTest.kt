package com.peyton.toddlergame

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Assert.fail as failNow
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/** Aquarium: smooth fish (no jumps, no snap turns), taps that scare, food they swim to, a treat every 5th sprinkle. */
class AquariumTest {
  private val W = 2208f; private val H = 1840f
  private fun model(seed: Int = 1, w: Float = W, h: Float = H): Pair<AquaModel, FakeSink> {
    val s = FakeSink(); val m = AquaModel(s, Random(seed)); m.w = w; m.h = h; m.step(0f); return m to s
  }
  private fun AquaModel.advance(secs: Float, dt: Float = 1 / 60f, each: () -> Unit = {}) { var k = 0f; while (k < secs) { t += dt; step(dt); each(); k += dt } }
  /** Open water: the first grid spot clear of fish and bubbles (bounded search, so it can't hang). */
  private fun AquaModel.water(): Offset {
    val spots = (0 until 5).flatMap { j -> (0 until 9).map { i -> Offset(W * (0.1f + i * 0.1f), H * (0.12f + j * 0.06f)) } }
    return spots.firstOrNull { q -> fish.none { hypot(it.x - q.x, it.y - q.y) < it.s * 2f } && bubs.none { hypot(it.x - q.x, it.y - q.y) < W * 0.05f } }
      ?: spots.maxBy { q -> fish.minOf { hypot(it.x - q.x, it.y - q.y) - it.s * 1.5f } }
  }

  @Test fun fishGlideWithoutJumpsEvenWhileBeingTappedAndFed() {
    val (m, _) = model(); var prev = m.fish.map { it.x to it.y }; var prevFace = m.fish.map { it.face }; var frame = 0
    val maxStep = W * AquaModel.DART * 1.2f / 60f
    m.advance(40f) {
      frame++
      if (frame % 45 == 0) m.fish[frame / 45 % m.fish.size].let { m.tap(Offset(it.x, it.y)) }   // tap a fish
      if (frame % 70 == 0) m.tap(m.water())                                                     // feed
      m.fish.forEachIndexed { i, f -> val (x, y) = prev[i]
        assertTrue("fish $i jumped ${hypot(f.x - x, f.y - y)} px in one frame (max $maxStep)", hypot(f.x - x, f.y - y) <= maxStep)
        assertTrue("fish $i turned ${abs(f.face - prevFace[i])} in one frame", abs(f.face - prevFace[i]) <= AquaModel.TURN * 2f / 60f + 1e-4f) }
      prev = m.fish.map { it.x to it.y }; prevFace = m.fish.map { it.face } }
  }

  @Test fun velocityEasesInsteadOfSnapping() {
    val (m, _) = model(); val f = m.fish[0]; m.advance(1f)
    val v0 = f.speed; m.tap(Offset(f.x, f.y)); m.advance(1 / 60f)
    assertTrue("doesn't jump to dart speed in a frame: ${f.speed} vs $v0", f.speed < v0 + W * AquaModel.DART * 0.2f)
    m.advance(0.5f); assertTrue("but does dart: ${f.speed}", f.speed > W * AquaModel.CRUISE * 2f)
    m.advance(5f); assertTrue("calms down: ${f.speed}", f.speed < W * AquaModel.CRUISE * 1.6f)
  }

  @Test fun sameSpeedAt30And120Fps() {
    fun dist(dt: Float): Float { val (m, _) = model(3); val f = m.fish[0]; m.advance(1f, dt); val x0 = f.x; val y0 = f.y; m.advance(1.5f, dt); return hypot(f.x - x0, f.y - y0) }
    val a = dist(1 / 30f); val b = dist(1 / 120f)
    assertEquals("frame-rate independent: $a vs $b", a, b, a * 0.08f + 2f)
  }

  @Test fun tappedFishBlubsInItsOwnVoiceDartsAwayAndBlowsBubbles() {
    val (m, s) = model(); m.advance(1f); val f = m.fish[0]; val tap = Offset(f.x - f.s * 0.5f, f.y); val bubs = m.bubs.size
    m.tap(tap)
    assertEquals(1, s.plays.count { it.data === Sfx.blubs[f.voice] }); assertEquals(1, s.haptics.size); assertTrue(m.bubs.size >= bubs + 3)
    val d0 = hypot(f.x - tap.x, f.y - tap.y); m.advance(0.8f)
    assertTrue("swims away from the finger", hypot(f.x - tap.x, f.y - tap.y) > d0 + f.s)
    assertTrue("fish don't all sound the same", m.fish.map { it.voice }.toSet().size >= 3)
    assertEquals(5, Sfx.blubs.size); assertEquals(5, Sfx.blubs.map { it.size }.size)
  }

  @Test fun foodSinksAndTheNearestFishSwimOverAndEatIt() {
    val (m, s) = model(); m.advance(1f); val p = m.water()
    m.tap(p); assertEquals(3, m.crumbs.size); assertEquals(1, s.plays.count { it.data === Sfx.plip })
    val y0 = m.crumbs.map { it.y }.average(); m.advance(0.5f); assertTrue("sinks", m.crumbs.isEmpty() || m.crumbs.map { it.y }.average() > y0)
    m.advance(8f); assertTrue("eaten: ${m.eaten}", m.eaten >= 2); assertTrue(m.crumbs.isEmpty())
  }

  @Test fun everyFifthSprinkleIsAGoldenTreatThatMakesAFishHappy() {
    val (m, s) = model(); m.advance(1f)
    repeat(4) { m.tap(m.water()); m.advance(0.3f) }
    assertTrue(m.crumbs.none { it.treat })
    m.tap(m.water()); assertEquals(1, m.crumbs.count { it.treat }); assertEquals(1, s.plays.count { it.data === Sfx.twinkle })
    m.advance(10f)
    assertTrue("someone ate the treat", m.fish.any { it.happyT > 0f }); assertEquals(1, s.plays.count { it.data === Sfx.chime })
  }

  @Test fun fishStayInTheTankAndAvoidSand() {
    val (m, _) = model(); var frame = 0
    m.advance(120f) { frame++; if (frame % 30 == 0) m.fish.random().let { m.tap(Offset(it.x + it.s * 0.3f, it.y + it.s * 0.3f)) }
      m.fish.forEach { assertTrue(it.x in -it.s..W + it.s); assertTrue(it.y in H * 0.08f..H * 0.82f) } }
  }

  @Test fun bubblesPopAndTheCrabHops() {
    val (m, s) = model(); m.advance(3f)
    val b = m.bubs.firstOrNull { it.r >= minOf(W, H) * 0.012f && m.fish.none { f -> hypot(f.x - it.x, f.y - it.y) < f.s * 2f } } ?: return failNow("no free bubble")
    m.tap(Offset(b.x, b.y)); assertFalse(b in m.bubs); assertEquals(1, s.plays.count { it.data === Sfx.bubblePop })
    m.tap(Offset(m.crabX * W, H * 0.9f)); assertEquals(1, s.plays.count { it.data === Sfx.snap }); assertEquals(m.t, m.crabHop, 0f)
  }

  @Test fun calmCapsUnderAPalmSmash() {
    val (m, _) = model()
    m.advance(10f) { repeat(5) { m.tap(Offset(Random.nextFloat() * W, Random.nextFloat() * H)) }
      assertTrue(m.crumbs.size <= AquaModel.MAX_CRUMBS); assertTrue(m.bubs.size <= AquaModel.MAX_BUBS + 3) }
  }

  @Test fun badInputNeverCrashes() {
    val (m, _) = model(); m.tap(Offset(Float.NaN, 1f)); m.tap(Offset(Float.POSITIVE_INFINITY, 0f)); m.step(Float.NaN); m.step(-1f); m.step(9f)
    assertTrue(m.fish.all { it.x.isFinite() && it.y.isFinite() })
    val (u, _) = model(w = 1f, h = 1f); u.tap(Offset(0f, 0f)); u.advance(1f); assertTrue(u.fish.isEmpty())
  }
}
