package com.peyton.toddlergame

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Splash pond for an 18-month-old, through [SoundSink] with a seeded Random. */
class PondTest {
  private val W = 2208f; private val H = 1840f
  private val SCREENS = listOf(W to H, 1233f to 2391f, 1179f to 2553f, 2553f to 1179f)
  private fun model(w: Float = W, h: Float = H): Pair<PondModel, FakeSink> {
    val s = FakeSink(); val m = PondModel(s, Random(4)); m.w = w; m.h = h; return m to s
  }
  private fun PondModel.advance(secs: Float, dt: Float = 1 / 60f) { var k = 0f; while (k < secs) { t += dt; step(dt); k += dt } }
  private fun FakeSink.count(d: FloatArray) = plays.count { it.data === d }
  private fun FakeSink.plopCount() = plays.count { p -> Sfx.plops.any { it === p.data } }
  /** Open water: the middle of the screen, away from the lily pads. */
  private fun PondModel.water(i: Int = 0) = Offset(w * (0.42f + (i % 3) * 0.08f), h * 0.5f)
  private fun PondModel.tapWater(i: Int = 0, id: Long = 1) { t += 0.1f; down(id, water(i)); up(id) }
  /** Splash-taps until [n] more taps have landed on open water (a tap on a paddling duck is a friend tap, not a splash). */
  private fun PondModel.splashes(n: Int) { val goal = taps + n; var i = 0; while (taps < goal && i < n * 3) tapWater(i++) }

  @Test fun bigLilyPadsOnEveryScreenAndNothingElseYet() {
    for ((w, h) in SCREENS) {
      val (m, _) = model(w, h)
      assertEquals(4, m.pads.size); assertTrue(m.friends.isEmpty() && m.ripples.isEmpty())
      assertTrue("pads are big targets (≥ 90 dp at 3x)", m.padR * 2 >= 270f)
      m.pads.indices.forEach { i -> val p = m.padAt(i)
        assertTrue("pad on screen", p.x - m.padR >= 0f && p.x + m.padR <= w && p.y - m.padR >= 0f && p.y + m.padR <= h)
        m.pads.indices.filter { it > i }.forEach { j -> assertTrue("pads apart", (m.padAt(j) - p).getDistance() > m.padR * 2) } }
      assertTrue("open water in the middle", m.pads.indices.none { (m.padAt(it) - m.water()).getDistance() < m.padR * 1.5f })
    }
  }

  @Test fun everyTapOnTheWaterRipplesSplashesAndPlops() {
    val (m, s) = model()
    m.tapWater()
    assertEquals(1, m.ripples.size); assertEquals(1f, m.ripples[0].big, 0f); assertEquals(1, s.plopCount())
    assertTrue("droplets", m.parts.list.isNotEmpty()); assertEquals(1, s.haptics.size); assertEquals(1, m.taps)
  }

  @Test fun friendsPopUpNearTapsDucksThenFrogsOnPads() {
    val (m, s) = model()
    m.tapWater(0); assertTrue("first tap is just a splash", m.friends.isEmpty())
    m.tapWater(1); val duck = m.friends.single()
    assertEquals(PondKind.DUCK, duck.kind); assertEquals(-1, duck.pad)
    assertTrue("near the tap", (m.friendAt(duck) - m.water(1)).getDistance() <= m.m * 0.25f)
    assertEquals(Critter.DUCK, s.animals.last().first)
    m.tapWater(2); m.tapWater(0); val frog = m.friends.last()
    assertEquals(PondKind.FROG, frog.kind); assertTrue("frog sits on a lily pad", frog.pad in m.pads.indices)
    assertEquals(Critter.FROG, s.animals.last().first)
  }

  @Test fun tappingAFriendMakesItCallAndHopButNotTooFast() {
    val (m, s) = model()
    m.tapWater(0); m.tapWater(1); val duck = m.friends.single(); m.advance(0.5f); s.clear()
    m.down(5, m.friendAt(duck)); m.up(5)
    assertEquals(listOf(Critter.DUCK), s.animals.map { it.first }); assertEquals(m.t, duck.reactAt, 0f)
    assertEquals("a friend isn't a splash tap", 2, m.taps)
    m.down(6, m.friendAt(duck)); m.up(6)
    assertEquals("palm-smack: one quack per ${PondModel.CALL_GAP}s", 1, s.animals.size)
    assertEquals("but every touch still buzzes", 2, s.haptics.size)
  }

  @Test fun lilyPadBobsAndAFrogHopsOnThenRibbits() {
    val (m, s) = model()
    m.t = 1f; m.down(1, m.padAt(2)); m.up(1)
    assertEquals(1f, m.pads[2].bobAt, 0f); assertEquals(1, s.count(Sfx.plip))
    val frog = m.friends.single(); assertEquals(2, frog.pad); assertEquals(Critter.FROG, s.animals.single().first)
    m.t = 2f; m.down(2, m.friendAt(frog)); m.up(2)
    assertEquals("tapping the frog ribbits again", 2, s.animals.count { it.first == Critter.FROG }); assertEquals(1, m.friends.size)
  }

  @Test fun draggingLeavesAWakeTrail() {
    val (m, s) = model(); val p = m.water()
    m.down(1, p); val after = m.ripples.size
    m.move(1, p + Offset(3f, 0f)); assertEquals("a wobble isn't a wake", after, m.ripples.size)
    for (i in 1..10) { m.t += 0.03f; m.move(1, p + Offset(i * m.m * PondModel.WAKE_STEP * 1.05f, 0f)) }
    assertEquals(after + 10, m.ripples.size); assertTrue(m.ripples.takeLast(10).all { it.big < 1f })
    val swishes = s.count(Sfx.swish); assertTrue("soft swish, throttled", swishes in 1..3)
    m.up(1); m.move(1, p); assertEquals("a move without a down just starts a new trail", after + 10, m.ripples.size)
  }

  @Test fun aFishJumpsEveryFifthSplashAndPlopsBackIn() {
    val (m, s) = model()
    m.splashes(PondModel.FISH_EVERY)
    val f = m.fish.single(); assertEquals(1, s.count(Sfx.blub)); val plops = s.plopCount()
    m.advance(PondModel.FISH_T * 0.5f); assertTrue("leaps up", m.fishAt(f).y < f.y0)
    m.advance(PondModel.FISH_T); assertTrue(m.fish.isEmpty()); assertEquals(plops + 1, s.plopCount())
  }

  @Test fun surprisesAlternateFrogLeapAndDuckFamily() {
    val (m, s) = model()
    m.splashes(PondModel.SURPRISE_EVERY)
    assertEquals(Surprise.FROG_JUMP, m.surprise); assertEquals(1, s.count(Sfx.wheee))
    val start = m.surprisePos()!!; m.advance(PondModel.FROG_JUMP_T / 2)
    assertTrue("frog leaps up and across", m.surprisePos()!!.y < start.y && m.surprisePos()!!.x > start.x)
    m.advance(PondModel.FROG_JUMP_T); assertNull(m.surprise); assertEquals("big splash landing", 1, s.count(Sfx.splash))
    m.splashes(PondModel.SURPRISE_EVERY)
    assertEquals(Surprise.DUCK_FAMILY, m.surprise); assertTrue(s.animals.any { it.first == Critter.CHICK })
    m.ripples.clear(); m.advance(PondModel.FAMILY_T / 2); assertTrue("ducks leave a wake", m.ripples.isNotEmpty())
    m.advance(PondModel.FAMILY_T / 2 + 0.1f); assertNull(m.surprise)
  }

  @Test fun aQuietPondPlaysASurpriseByItself() {
    val (m, _) = model()
    m.advance(PondModel.IDLE - 1f); assertNull(m.surprise)
    m.advance(2f); assertNotNull(m.surprise)
  }

  @Test fun friendsDiveAwayAndDucksStayOnScreen() {
    val (m, _) = model()
    repeat(4) { m.tapWater(it) }; assertEquals(2, m.friends.size)
    m.advance(PondModel.FRIEND_LIFE - 1f)
    m.friends.forEach { val p = m.friendAt(it); assertTrue(p.x in 0f..m.w && p.y in 0f..m.h) }
    m.advance(1.5f); assertTrue(m.friends.isEmpty())
  }

  @Test fun palmSmackIsCappedButEveryTouchAnswers() {
    val (m, s) = model()
    for (frame in 0 until 30) { m.t += 1 / 60f
      for (f in 0 until 10) { val id = frame * 10L + f; val p = Offset(m.w * (0.05f + f * 0.09f), m.h * (0.1f + (frame % 8) * 0.1f)); m.down(id, p); m.move(id, p + Offset(m.m * 0.2f, 0f)); m.up(id) }
      m.step(1 / 60f) }
    assertEquals(300, s.haptics.size)
    assertTrue(m.ripples.size <= PondModel.MAX_RIPPLES); assertTrue(m.friends.size <= PondModel.MAX_FRIENDS)
    assertTrue(m.fish.size <= PondModel.MAX_FISH); assertTrue(m.parts.list.size <= 300)
    assertTrue("plops throttled", s.plopCount() <= 30 / 3 + 1)
  }

  @Test fun zeroSizeDoesNotCrash() {
    val (m, _) = model(0f, 0f)
    m.down(1, Offset.Zero); m.move(1, Offset(5f, 5f)); m.up(1); m.advance(PondModel.IDLE + 1f)
    m.w = W; m.h = H; m.tapWater(); assertTrue(m.ripples.isNotEmpty())
  }
}
