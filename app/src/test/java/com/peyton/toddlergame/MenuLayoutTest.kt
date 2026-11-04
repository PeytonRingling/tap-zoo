package com.peyton.toddlergame

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

/** The 12-tile menu fits every screen Tap Zoo runs on: big tiles, inside the screen, apart, and clear of the parent gear. */
class MenuLayoutTest {
  private val screens = mapOf("phone" to (1179f to 2553f), "phone land" to (2553f to 1179f), "Fold folded" to (1233f to 2391f),
    "Fold unfolded" to (2523f to 2103f), "Fold unfolded portrait" to (2103f to 2523f))
  private val gearPx = 80f * 3   // 80 dp at xxhdpi

  @Test fun elevenTilesFitEveryScreen() {
    assertEquals(12, Mode.TILES.size)
    for ((name, wh) in screens) {
      val (w, h) = wh; val m = MenuModel().apply { this.w = w; this.h = h; this.gear = gearPx }
      val r = m.rects(Mode.TILES.size); assertEquals(name, 12, r.size)
      val g = Rect(w - gearPx, 0f, w, gearPx)
      r.forEachIndexed { i, a ->
        assertTrue("$name tile $i on screen", a.left >= 0f && a.top >= 0f && a.right <= w && a.bottom <= h)
        assertFalse("$name tile $i under the gear: $a vs $g", a.overlaps(g))
        assertTrue("$name tile $i ≥ 90 dp (about 14 mm), big enough to smack: ${a.width}", a.width >= 90f * 3)
        for (j in i + 1 until r.size) assertFalse("$name tiles $i/$j overlap", a.overlaps(r[j]))
      }
    }
  }
}
