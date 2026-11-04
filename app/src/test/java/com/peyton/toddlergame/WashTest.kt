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

  @Test fun cleanThenShowerShakeBlowDryFluffyThenTheNextMuddyFriend() {
    val (m, s) = model(); val first = m.animal
    m.washClean()
    assertEquals("tap-only wash works", WashPhase.RINSE, m.phase); assertEquals(1, m.washed); assertEquals(1, s.count(Sfx.shower))
    m.advance(WashModel.RINSE + 0.02f); assertEquals(WashPhase.SHAKE, m.phase); assertEquals("shake-off splash", 1, s.count(Sfx.splash))
    assertTrue("droplets fly", m.parts.list.size >= 30)
    m.advance(WashModel.SHAKE + 0.02f); assertEquals(WashPhase.DRY, m.phase); assertEquals(1, s.count(Sfx.blowDry))
    m.advance(WashModel.DRY + 0.05f); assertEquals("dries by itself", WashPhase.FLUFFY, m.phase)
    assertTrue("says hello when fluffy", s.animals.isNotEmpty() || s.count(Sfx.trumpet) + s.count(Sfx.baa) == 1)
    m.advance(WashModel.FLUFFY + 0.02f); assertEquals(WashPhase.ENTERING, m.phase)
    assertNotEquals(first, m.animal); assertEquals("muddy again", 1f, m.dirtLeft(), 0f)
    assertTrue("walks in from the right", m.center.x > W * 0.5f)
    m.advance(WashModel.ENTER + 0.05f); assertEquals(WashPhase.DIRTY, m.phase); assertEquals(W * 0.5f, m.center.x, 1f)
  }

  @Test fun rubbingWhileDryingDriesFaster() {
    val (a, _) = model(); val (b, _) = model()
    for (m in listOf(a, b)) { m.washClean(); m.advance(WashModel.RINSE + WashModel.SHAKE + 0.05f); assertEquals(WashPhase.DRY, m.phase) }
    var f = 0L; b.advance(0.5f) { b.down(f, b.center + Offset((f % 3 - 1) * b.radius * 0.4f, 0f)); b.up(f); f++ }; a.advance(0.5f)
    assertTrue("towel rubs: ${b.phase} ${b.dryness} vs ${a.dryness}", b.phase == WashPhase.FLUFFY || b.dryness > a.dryness + 0.3f)
  }

  @Test fun pigDogElephantAndFluffySheepAllGetAWashWithTheirOwnVoices() {
    assertTrue(WashModel.ORDER.containsAll(listOf(WashAnimal.PIG, WashAnimal.DOG, WashAnimal.ELEPHANT, WashAnimal.SHEEP)))
    val (m, s) = model(); val seen = LinkedHashSet<WashAnimal>()
    repeat(WashModel.ORDER.size) { seen += m.animal; m.washClean(); m.advance(WashModel.RINSE + WashModel.SHAKE + WashModel.DRY + WashModel.FLUFFY + WashModel.ENTER + 0.3f) }
    assertEquals(WashModel.ORDER.toSet(), seen)
    assertEquals("elephant trumpets", 1, s.count(Sfx.trumpet)); assertEquals("sheep baas", 1, s.count(Sfx.baa))
    assertEquals(WashModel.ORDER.count { it.critter != null }, s.animals.size)
  }

  @Test fun scrubbingTheBellyGiggles() {
    val (m, s) = model()
    m.down(1, m.belly); m.up(1); assertEquals(1, s.count(Sfx.giggle)); assertTrue(m.giggleAt >= 0f)
    m.t += 0.1f; m.down(2, m.belly); m.up(2); assertEquals("not a giggle storm", 1, s.count(Sfx.giggle))
    m.t += WashModel.GIGGLE_GAP; m.down(3, m.belly); m.up(3); assertEquals(2, s.count(Sfx.giggle))
    m.t += WashModel.GIGGLE_GAP; m.down(4, m.center - Offset(0f, m.radius * 0.6f)); m.up(4); assertEquals("forehead isn't the belly", 2, s.count(Sfx.giggle))
  }

  @Test fun bubblesFloatUpAndPopWhenTapped() {
    val (m, s) = model(); m.advance(10f) { assertTrue(m.bubbles.size <= WashModel.MAX_BUBBLES) }
    assertTrue(m.bubbles.isNotEmpty()); assertTrue("big targets", m.bubbleR * 2 >= kotlin.math.min(W, H) * 0.14f)
    val b = m.bubbles.last(); val y0 = b.y; m.advance(0.5f); assertTrue("floats up", b.y < y0)
    val dirt = m.dirtLeft(); val n = m.bubbles.size
    m.down(1, m.bubbleAt(b)); m.up(1)
    assertFalse(b in m.bubbles); assertEquals(n - 1, m.bubbles.size); assertEquals(1, s.count(Sfx.bubblePop)); assertEquals(1, m.popped)
    assertEquals("a pop isn't a scrub", dirt, m.dirtLeft(), 0f)
  }

  @Test fun touchesDuringTheCelebrationDontBreakAnything() {
    val (m, s) = model(); m.washClean(); assertEquals(WashPhase.RINSE, m.phase)
    for (ph in 0 until 4) { s.clear(); m.t += 0.1f; m.down(5, m.center + Offset(m.radius * 0.5f, 0f)); m.up(5)
      assertTrue("still a sound in ${m.phase}", s.plays.isNotEmpty()); m.advance(1.3f) }
    assertEquals(1, m.washed)
  }

  @Test fun aLongWashScrubsTwiceAsHard() {
    val (m, _) = model(); m.advance(WashModel.STALL + 1f); m.bubbles.clear()
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
