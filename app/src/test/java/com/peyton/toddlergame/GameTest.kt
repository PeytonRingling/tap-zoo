package com.peyton.toddlergame
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test
class GameTest {
  @Test fun gridFitsFoldScreens() {
    for ((w, h) in listOf(1080f to 2092f, 2092f to 1080f, 2208f to 1840f, 1840f to 2208f)) for (n in 1..8) {
      val r = Layout.grid(n, w, h, 20f, square = true)
      assertEquals(n, r.size)
      r.forEach { assertTrue(it.left >= 0 && it.top >= 0 && it.right <= w + 0.5f && it.bottom <= h + 0.5f) }
      for (i in r.indices) for (j in i + 1 until r.size) assertFalse(r[i].overlaps(r[j].deflate(1f)))
    }
  }
  @Test fun unfoldedUsesWideGrid() { val r = Layout.grid(6, 2208f, 1840f, 20f); assertEquals(r[0].top, r[2].top, 0.1f) }
  @Test fun soundsAreFiniteAndBounded() {
    val all = Sfx.animal.values + Sfx.bells + listOf(Sfx.peekaboo, Sfx.giggle, Sfx.chime, Sfx.boom, Sfx.whoosh, Sfx.pop(1200.0), Sfx.balloonPop, Sfx.inflate, Sfx.bubblePop, Sfx.wheee, Sfx.blub, Sfx.snap, Sfx.zoom, Sfx.kick, Sfx.tom, Sfx.snare, Sfx.cymbal, Sfx.squeak) + Sfx.honks + Sfx.xylo + Sfx.comboNotes
    assertEquals(Critter.entries.size, Sfx.animal.size)
    all.forEach { s -> assertTrue(s.isNotEmpty()); assertTrue(s.all { it.isFinite() && kotlin.math.abs(it) <= 1.5f }) }
  }
  @Test fun prewarmCoversEverySoundAtTheNewRate() {
    val all = Sfx.prewarm()
    assertEquals(Critter.entries.size + 10 + 10 + 4 + 18 + 10 + 6, all.size)
    all.forEach { s -> assertTrue(s.isNotEmpty()); assertTrue(s.all { it.isFinite() && kotlin.math.abs(it) <= 1.5f }) }
    assertEquals(44100, Sfx.RATE); assertEquals((Sfx.RATE * 0.9).toInt(), Sfx.bells[0].size)   // durations in seconds unchanged
  }
  @Test fun pentatonicAscending() { for (i in 1 until Sfx.PENTA.size) assertTrue(Sfx.PENTA[i] > Sfx.PENTA[i - 1]) }
  @Test fun particlesCapped() { val p = Particles(50); repeat(10) { p.burst(0f, 0f, 30) }; assertEquals(50, p.list.size); repeat(200) { p.step(0.05f) }; assertTrue(p.list.isEmpty()) }
}
