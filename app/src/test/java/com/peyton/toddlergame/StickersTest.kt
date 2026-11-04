package com.peyton.toddlergame

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Stickers for an 18-month-old, through [SoundSink] with a seeded Random. */
class StickersTest {
  private val W = 2208f; private val H = 1840f
  private fun model(w: Float = W, h: Float = H): Pair<StickersModel, FakeSink> {
    val s = FakeSink(); val m = StickersModel(s, Random(3)); m.w = w; m.h = h; return m to s
  }
  private fun StickersModel.advance(secs: Float, dt: Float = 1 / 60f, each: () -> Unit = {}) { var k = 0f; while (k < secs) { t += dt; step(dt); each(); k += dt } }
  private fun StickersModel.tap(id: Long, p: Offset) { t += 0.1f; down(id, p); up(id) }
  private fun FakeSink.count(d: FloatArray) = plays.count { it.data === d }
  /** Spots far enough apart that a tap never lands on an earlier sticker. */
  private fun StickersModel.spots() = listOf(0.15f to 0.15f, 0.5f to 0.15f, 0.85f to 0.15f, 0.3f to 0.5f, 0.7f to 0.5f).map { (x, y) -> Offset(w * x, h * y) }

  @Test fun trayHasFiveGiantStickersOnBothScreens() {
    for ((w, h) in listOf(W to H, 1233f to 2391f)) {
      val (m, _) = model(w, h); val mm = kotlin.math.min(w, h)
      assertTrue("tray sticker ≥ 16% of the short side", m.trayR * 2 >= mm * 0.16f)
      for (i in 0 until 5) { val c = m.slot(i); assertTrue(c.x - m.trayR >= 0f && c.x + m.trayR <= w && c.y + m.trayR <= h) }
      assertTrue("slots don't overlap", m.slot(1).x - m.slot(0).x > m.trayR * 2)
    }
  }

  @Test fun tappingTheSceneSlapsTheGlowingStickerThenTheNextGlows() {
    val (m, s) = model()
    m.tap(1, m.spots()[0])
    assertEquals(1, m.stickers.size); assertEquals(StickerKind.ANIMAL, m.stickers[0].kind)
    assertEquals(1, s.count(Sfx.thwack)); assertTrue(s.haptics.isNotEmpty()); assertEquals(1, m.current)
  }

  @Test fun tapsAloneGiveEveryKindAndANewAnimalEachTime() {
    val (m, _) = model(); val first = m.nextCritter
    m.spots().forEachIndexed { i, p -> m.tap(i.toLong(), p) }
    assertEquals(StickerKind.entries, m.stickers.map { it.kind })
    assertEquals(first, m.stickers[0].critter); assertNotEquals(first, m.nextCritter)
  }

  @Test fun stickersNeverHideBehindTheTray() {
    val (m, _) = model()
    val between = Offset((m.slot(0).x + m.slot(1).x) / 2, H - 2f)   // bottom edge, between two tray stickers
    m.tap(1, between); val st = m.stickers.single()
    assertTrue(st.y <= m.floorY); assertTrue(st.y + m.stickerR * 1.08f <= m.trayY - m.trayR * 1.25f + 0.5f)
  }

  @Test fun dragAStickerFromTheTrayAndLetGoToStickIt() {
    val (m, s) = model()
    m.down(1, m.slot(2)); assertEquals(1, s.count(Sfx.peel)); assertTrue(m.stickers.isEmpty()); assertEquals(2, m.current)
    m.move(1, Offset(W * 0.4f, H * 0.3f)); m.up(1)
    val st = m.stickers.single(); assertEquals(StickerKind.CAR, st.kind); assertEquals(W * 0.4f, st.x, 0.1f); assertEquals(H * 0.3f, st.y, 0.1f)
    assertEquals(1, s.count(Sfx.thwack)); assertTrue(m.peels.isEmpty())
  }

  @Test fun tappingAPlacedStickerMakesItDoItsThing() {
    val (m, s) = model(); val spots = m.spots()
    spots.forEachIndexed { i, p -> m.tap(i.toLong(), p) }; s.clear(); val n = m.stickers.size
    m.tap(9, spots[0]); assertEquals(listOf(m.stickers[0].critter to 0L), s.animals)
    m.tap(9, spots[2]); assertEquals(1, s.count(Sfx.honks[0]))
    m.tap(9, spots[1]); assertTrue(Sfx.bells.any { b -> s.plays.any { it.data === b } }); assertTrue(m.parts.list.isNotEmpty())
    assertEquals("poking doesn't add stickers", n, m.stickers.size); assertTrue(m.stickers[0].pokedAt > 0f)
  }

  @Test fun atMostTwentyStayAndTheOldestShrinksAway() {
    val (m, _) = model(); val p = Offset(W * 0.5f, H * 0.4f)
    repeat(25) { m.t += 0.1f; m.down(it.toLong(), m.slot(it % 5)); m.move(it.toLong(), p); m.up(it.toLong()) }
    assertEquals(StickersModel.MAX, m.stickers.count { it.live })
    m.advance(StickersModel.FADE + 0.1f); assertEquals(StickersModel.MAX, m.stickers.size)
  }

  @Test fun twoFingersHeldStillSweepThePageButPalmsAndWigglesDont() {
    val (m, s) = model(); val sp = m.spots()
    m.down(1, sp[0]); m.down(2, sp[1]); m.down(3, sp[2]); m.advance(StickersModel.CLEAR_HOLD + 0.5f)
    assertTrue("a palm doesn't clear", m.stickers.all { it.live }); m.up(1); m.up(2); m.up(3)
    m.down(4, sp[3]); m.down(5, sp[4]); m.advance(1f); m.move(5, sp[4] + Offset(m.trayR, 0f)); m.advance(1.5f)
    assertTrue("moving fingers restart the hold", m.stickers.all { it.live })
    m.advance(StickersModel.CLEAR_HOLD); assertTrue(m.stickers.none { it.live }); assertEquals(1, s.count(Sfx.whoosh))
    m.advance(StickersModel.FADE + 0.1f); assertTrue(m.stickers.isEmpty())
    m.advance(5f); assertEquals("one sweep per hold", 1, s.count(Sfx.whoosh))
  }

  @Test fun palmSmackIsSafe() {
    val (m, s) = model(); val rnd = Random(9)
    m.advance(20f) { if ((m.t * 60).toInt() % 3 == 0) for (f in 0L until 5L) { m.up(f); m.down(f, Offset(rnd.nextFloat() * W, rnd.nextFloat() * H)) }
      assertTrue(m.stickers.count { it.live } <= StickersModel.MAX); assertTrue("fading ones are dropped", m.stickers.size <= StickersModel.MAX * 4); assertTrue(m.parts.list.size <= 200) }
    assertTrue("thwacks throttled", s.count(Sfx.thwack) <= (20f / StickersModel.THWACK_GAP).toInt() + 1)
  }

  @Test fun badInputNeverCrashes() {
    val (m, _) = model()
    m.down(1, Offset(Float.NaN, 0f)); m.move(1, Offset(0f, Float.POSITIVE_INFINITY)); m.move(7, Offset(1f, 1f)); m.up(42)
    m.step(Float.NaN); m.step(-1f); m.step(5f); m.advance(1f)
    assertTrue(m.stickers.isEmpty())
    val s = FakeSink(); val u = StickersModel(s, Random(1)); u.down(1, Offset(0f, 0f)); u.up(1); u.step(0.1f); assertTrue(s.plays.isEmpty())
  }
}
