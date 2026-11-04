package com.peyton.toddlergame

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Behaviour of the game models through [SoundSink] with a seeded Random: what plays, what spawns, what's capped. These pin
 *  today's behaviour so later polish PRs can't silently change what the kid already likes. */
class GameModelsTest {
  private val W = 2208f; private val H = 1840f   // Pixel Fold inner screen, px

  // ---------------------------------------------------------------- Balloons
  private fun balloons(seed: Int = 1): Pair<BalloonModel, FakeSink> {
    val s = FakeSink(); val m = BalloonModel(s, Random(seed), stream = false); m.w = W; m.h = H; return m to s
  }
  private fun BalloonModel.advance(secs: Float, dt: Float = 1 / 60f) { var k = 0f; while (k < secs) { t += dt; step(dt); k += dt } }
  private fun BalloonModel.emptySpot(): Offset {
    for (yy in 6..13) for (xx in 4..16) { val p = Offset(w * xx / 20f, h * yy / 20f)   // away from the edges, where big balloons are clamped
      if (balloons.none { kotlin.math.hypot(it.x - p.x, it.y - p.y) < it.r * 1.6f } && bubbles.none { kotlin.math.hypot(it.x - p.x, it.y - p.y) < it.r * 3f }) return p }
    error("no empty spot")
  }

  @Test fun balloonsWaitForTheScreenSize() {
    val m = BalloonModel(FakeSink(), Random(1), stream = false); m.step(0.016f); assertTrue(m.balloons.isEmpty())
  }

  @Test fun balloonsStartWithThree() { val (m, s) = balloons(); m.advance(0.1f); assertEquals(3, m.balloons.size); assertTrue(s.plays.isEmpty()) }

  @Test fun tapOnEmptySkyInflatesABalloonUnderTheFinger() {
    val (m, s) = balloons(); m.advance(0.5f); val p = m.emptySpot()
    m.tap(p)
    assertEquals(4, m.balloons.size)
    val b = m.balloons.last(); assertEquals(p.x, b.x, 1f); assertEquals(p.y, b.y, 1f)
    assertSame(Sfx.inflate, s.plays.first().data); assertEquals(listOf(false), s.haptics)
  }

  @Test fun rapidEmptyTapsAreThrottled() {
    val (m, _) = balloons(); m.advance(0.5f); val p = m.emptySpot()
    m.tap(p); val n = m.balloons.size; m.t += 0.05f; m.tap(m.emptySpot())
    assertEquals(n, m.balloons.size)
  }

  @Test fun tappingABalloonPopsItWithPopAndOneInKeyNote() {
    val (m, s) = balloons(); m.advance(0.5f); val b = m.make(W * 0.5f, H * 0.2f, kind = BKind.ROUND); s.clear(); m.t += 0.3f
    m.tap(Offset(b.x, b.y))
    assertEquals(1, m.pops); assertFalse("squashing: no longer tappable", b.live)
    assertSame(Sfx.balloonPop, s.plays[0].data)
    assertSame("first pop of a run = first note of the G pentatonic ladder", Sfx.comboNotes[0], s.plays[1].data)
    assertEquals(2, s.plays.size); assertEquals(listOf(true), s.haptics)
    m.advance(0.1f); assertFalse("burst after the squash", b in m.balloons)
  }

  @Test fun popSoundIsPitchedByBalloonSize() {
    val sizes = ArrayList<Float>(); val pop = FloatArray(10)
    val s = FakeSink(); val m = BalloonModel(s, Random(3), { sizes += it; pop }, stream = false); m.w = W; m.h = H; m.advance(0.5f)
    repeat(3) { val b = m.balloons.first { it.live }; m.pop(b) }
    assertEquals(3, sizes.size); sizes.forEach { assertTrue("size $it in 0..1", it in 0f..1f) }
    assertEquals(3, s.plays.count { it.data === pop })
  }

  @Test fun balloonCountNeverExceedsTheCap() {
    val (m, _) = balloons(); m.advance(0.2f)
    repeat(60) { m.t += 0.2f; m.make(W * ((it * 37) % 100) / 100f, H * 0.5f); m.step(0.016f) }
    assertTrue(m.balloons.count { !it.leaving } <= m.cap)
    m.advance(15f); assertTrue("leavers float away", m.balloons.none { it.leaving })
  }

  @Test fun emptySkyRefillsAfterTwoAndAHalfSeconds() {
    val (m, _) = balloons(); m.advance(0.2f)
    m.balloons.toList().forEach { m.pop(it) }
    m.advance(2.3f); assertTrue(m.balloons.isEmpty())
    m.advance(0.5f); assertEquals(1, m.balloons.size)
  }

  @Test fun sameSeedSameGame() {
    val (a, _) = balloons(7); val (b, _) = balloons(7); a.advance(1f); b.advance(1f)
    assertEquals(a.balloons.map { it.color to it.r }, b.balloons.map { it.color to it.r })
  }

  // ---------------------------------------------------------------- Bubbles
  private fun bubbles(seed: Int = 1): Pair<BubbleModel, FakeSink> { val s = FakeSink(); val m = BubbleModel(s, Random(seed)); m.w = W; m.h = H; return m to s }

  @Test fun bubblesFillTheScreenAndStayFull() {
    val (m, _) = bubbles(); m.step(0.016f); val n = m.bubs.size
    assertTrue(n in 9..24)
    repeat(600) { m.t += 0.05f; m.step(0.05f) }
    assertEquals(n, m.bubs.size)
  }

  @Test fun tapOnOpenWaterIsSilent() {
    val (m, s) = bubbles(); m.step(0.016f); m.bubs.clear(); m.tap(Offset(10f, 10f))
    assertTrue(s.plays.isEmpty()); assertTrue(s.haptics.isEmpty())
  }

  @Test fun chainReactionPopsNeighboursAndAlwaysEnds() {
    val (m, s) = bubbles(); m.w = W; m.h = H; m.step(0.016f); m.bubs.clear()
    val r = 60f
    for (i in 0 until 12) m.bubs += Bub(300f + i * r * 1.9f, 900f, r, RAINBOW[i % 8], 0f, false, 0f)   // touching row of 12
    m.tap(Offset(300f, 900f))
    assertEquals(1, s.plays.size); assertEquals(1, s.haptics.size)
    repeat(100) { m.t += 0.05f; m.step(0.0f) }
    assertTrue(m.pending.isEmpty())
    assertEquals("level cap: the tapped one + 6 levels", 7, s.plays.size)
  }

  @Test fun goldenBubbleBurstsIntoSevenWithAChime() {
    val (m, s) = bubbles(); m.step(0.016f); m.bubs.clear()
    m.bubs += Bub(1000f, 900f, 200f, RAINBOW[0], 0f, true, 0f)
    m.tap(Offset(1000f, 900f))
    assertTrue(s.plays.any { it.data === Sfx.chime })
    assertEquals("the golden one is replaced by 7 small ones", 7, m.bubs.size)
  }

  // ---------------------------------------------------------------- Paint (finger trail)
  private fun paint(): Pair<PaintModel, FakeSink> { val s = FakeSink(); val m = PaintModel(s, Random(1)); m.w = W; m.h = H; return m to s }

  @Test fun paintNotePitchFollowsHeight() {
    val (m, s) = paint()
    m.down(1, Offset(500f, 1f)); m.up(1); m.t += 1f
    m.down(2, Offset(500f, H - 1f)); m.up(2)
    assertSame("top of screen = highest bell", Sfx.bells[Sfx.PENTA.size - 1], s.plays[0].data)
    assertSame("bottom = lowest bell", Sfx.bells[0], s.plays[1].data)
    assertEquals(listOf(false, false), s.haptics)
  }

  @Test fun paintTinyMovesAreIgnoredAndNotesAreSpaced() {
    val (m, s) = paint(); m.down(1, Offset(500f, 900f)); val tr = m.active.getValue(1L)
    m.move(1, Offset(503f, 900f)); assertEquals(1, tr.pts.size)                 // < 5 px
    m.t += 0.05f; m.move(1, Offset(800f, 900f)); assertEquals(1, s.plays.size)  // far but < 0.11 s since last note
    m.t += 0.2f; m.move(1, Offset(1100f, 900f)); assertEquals(2, s.plays.size)
  }

  @Test fun paintTrailsFadeAfterFourSecondsAndPointsAreCapped() {
    val (m, _) = paint()
    m.down(1, Offset(0f, 900f)); for (i in 1..2500) m.move(1, Offset(i * 6f % W, 900f + (i % 2) * 6f))
    m.step(0.016f); assertTrue(m.trails.sumOf { it.pts.size } <= 1800)
    m.up(1); m.t += 4.1f; m.step(0.016f); assertTrue(m.trails.isEmpty())
    m.up(99); m.move(99, Offset(1f, 1f))      // unknown finger: no crash
  }
}
