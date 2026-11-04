package com.peyton.toddlergame

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Feed the monster for an 18-month-old, through [SoundSink] with a seeded Random. */
class FeedTest {
  private val W = 2208f; private val H = 1840f
  private fun model(w: Float = W, h: Float = H): Pair<FeedModel, FakeSink> {
    val s = FakeSink(); val m = FeedModel(s, Random(1)); m.w = w; m.h = h; m.step(0f); return m to s
  }
  private fun FeedModel.advance(secs: Float, dt: Float = 1 / 60f, each: () -> Unit = {}) { var k = 0f; while (k < secs) { t += dt; step(dt); each(); k += dt } }
  private fun FakeSink.count(d: FloatArray) = plays.count { it.data === d }
  private fun FeedModel.tap(id: Long, p: Offset) { down(id, p); up(id) }
  private fun FeedModel.at(k: FoodKind) = foods.first { it.kind == k }.let { Offset(it.x, it.y) }

  @Test fun fiveBigFoodsInTheTopHalfAndAMonsterBelow() {
    for ((w, h) in listOf(W to H, 1233f to 2391f)) {
      val (m, _) = model(w, h)
      assertEquals(5, m.foods.size); assertEquals(FoodKind.entries.toSet(), m.foods.map { it.kind }.toSet())
      m.foods.forEach { assertTrue("on screen", it.x in 0f..w && it.y in 0f..h * 0.5f) }
      assertTrue("big targets", m.foodR * 2 >= kotlin.math.min(w, h) * 0.18f - 1f)
      assertTrue("monster on screen", m.monster.y + m.monsterR * 0.5f <= h && m.monster.y > h * 0.5f)
      val xs = m.foods.map { it.x }.sorted(); for (i in 1 until xs.size) assertTrue("foods don't overlap", xs[i] - xs[i - 1] > m.foodR * 1.5f)
    }
  }

  @Test fun tappingAFoodFliesItIntoTheMouthAndChomps() {
    val (m, s) = model(); val f = m.foods.first { it.kind == FoodKind.APPLE }
    m.tap(1, m.at(FoodKind.APPLE))
    assertTrue("instant feedback", s.plays.isNotEmpty()); assertEquals(FoodState.FLYING, f.state)
    m.advance(FeedModel.FLY + 0.05f)
    assertEquals(FoodState.EATEN, f.state); assertEquals(1, m.eaten); assertEquals(1, s.count(Sfx.chomp))
    m.advance(FeedModel.RESPAWN); assertEquals("comes back home", FoodState.IDLE, f.state)
    assertEquals(m.home(f.slot), Offset(f.x, f.y))
  }

  @Test fun tappingEmptySpaceFeedsTheNearestFood() {
    val (m, s) = model(); val target = m.at(FoodKind.MELON)
    m.tap(1, target + Offset(0f, m.foodR * 2.5f))
    assertEquals(FoodState.FLYING, m.foods.first { it.kind == FoodKind.MELON }.state)
    assertTrue(s.plays.isNotEmpty()); m.advance(1f); assertEquals(1, m.eaten)
  }

  @Test fun draggingCarriesTheFoodAndLettingGoAnywhereHopsItIn() {
    val (m, _) = model(); val f = m.foods.first { it.kind == FoodKind.COOKIE }
    m.down(3, m.at(FoodKind.COOKIE)); assertEquals(FoodState.HELD, f.state)
    m.advance(0.1f); assertTrue("mouth opens while a finger is down", m.mouthOpen > 0.5f)
    m.move(3, Offset(W * 0.1f, H * 0.6f)); assertEquals(W * 0.1f, f.x, 0.1f)
    m.up(3); assertEquals(FoodState.FLYING, f.state); m.advance(1f); assertEquals(1, m.eaten)
  }

  @Test fun droppingRightOnTheMouthEatsAtOnce() {
    val (m, s) = model(); val f = m.foods.first { it.kind == FoodKind.CARROT }
    m.down(1, m.at(FoodKind.CARROT)); m.move(1, m.mouth); m.up(1)
    assertEquals(FoodState.EATEN, f.state); assertEquals(1, s.count(Sfx.chomp))
  }

  @Test fun everyFifthBiteBurpsAndGiggles() {
    val (m, s) = model()
    repeat(10) { m.tap(it.toLong(), m.at(FoodKind.entries[it % 5])); m.advance(1.2f) }
    assertEquals(10, m.eaten); assertEquals(2, s.count(Sfx.burp))
    assertEquals("tummy back to normal after 10", 1f, m.tummy, 0.001f)
  }

  @Test fun tappingTheMonsterMakesItGiggle() {
    val (m, s) = model(); m.tap(1, m.monster)
    assertEquals(1, s.count(Sfx.giggle)); assertEquals(0, m.eaten); assertTrue(m.foods.all { it.state == FoodState.IDLE })
  }

  @Test fun palmSmackIsSafe() {
    val (m, s) = model()
    m.advance(5f) { if ((m.t * 60).toInt() % 2 == 0) for (f in 0L until 5L) { m.up(f); m.down(f, Offset(W * (0.1f + f * 0.2f), H * 0.25f)) }
      assertTrue(m.foods.count { it.state == FoodState.HELD } <= 5) }
    for (f in 0L until 5L) m.up(f); m.advance(2f)
    assertTrue(m.eaten > 5); assertTrue(m.foods.all { it.state == FoodState.IDLE })
    assertTrue("chomp throttled", s.count(Sfx.chomp) <= (7f / FeedModel.CHOMP_GAP).toInt() + 1)
    assertTrue(m.parts.list.size <= 300)
  }

  @Test fun everyFoodAlwaysComesBack() {
    val (m, _) = model(); m.foods.forEach { m.tap(it.slot.toLong(), Offset(it.x, it.y)) }
    m.tap(9, Offset(W * 0.5f, H * 0.1f))   // nothing idle left: still a sound, nothing breaks
    m.advance(3f); assertTrue(m.foods.all { it.state == FoodState.IDLE }); assertEquals(5, m.eaten)
  }

  @Test fun noMusicInFeed() {
    assertNull("Feed relies on its own sounds", MusicDirector.trackFor(Mode.FEED, MusicSlot.entries.toSet(), 5) { true })
  }

  @Test fun badInputNeverCrashes() {
    val (m, _) = model()
    m.down(1, Offset(Float.NaN, 0f)); m.move(1, Offset(Float.POSITIVE_INFINITY, 1f)); m.up(1); m.up(77); m.move(55, Offset(1f, 1f))
    m.down(2, Offset(-1e6f, 1e6f)); m.up(2); m.step(Float.NaN); m.step(-1f); m.step(5f); m.advance(2f)
    assertTrue(m.foods.all { it.x.isFinite() && it.y.isFinite() })
    val s = FakeSink(); val u = FeedModel(s, Random(1)); u.down(1, Offset(0f, 0f)); u.step(0.1f); assertTrue(s.plays.isEmpty())
  }
}
