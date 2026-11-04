package com.peyton.toddlergame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Parent settings › "Music in": every switch (all 5, Pond included) is fully on screen without sideways scrolling, on every
 *  screen Tap Zoo runs on. Before v13's fix the row scrolled sideways and Pond sat off the right edge. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SettingsLayoutTest {
  @get:Rule val rule = createComposeRule()

  private fun allChipsOnScreen() {
    rule.mainClock.autoAdvance = false
    rule.setContent { Box(Modifier.fillMaxSize()) { App("SETTINGS", onExit = {}) } }
    rule.mainClock.advanceTimeBy(1500)
    val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
    val panel = rule.onNodeWithTag("settings_panel").fetchSemanticsNode().boundsInRoot
    assertEquals(5, MusicSlot.entries.size)
    // Sideways first, before anything scrolls: the chip's unclipped position must sit inside the white panel and the screen.
    for (sl in MusicSlot.entries) {
      val node = rule.onNodeWithTag("music_chip_${sl.name}").fetchSemanticsNode(); val x = node.positionInRoot.x; val w = node.size.width
      assertTrue("$sl starts inside the panel: x=$x panel=$panel", x >= panel.left && x >= root.left)
      assertTrue("$sl ends inside the panel: ${x + w} panel=$panel", x + w <= panel.right + 0.5f && x + w <= root.right)
    }
    // Then down: the panel scrolls vertically (it's long on a landscape phone); each chip must be fully visible once scrolled to.
    for (sl in MusicSlot.entries) {
      val n = rule.onNodeWithTag("music_chip_${sl.name}"); n.performScrollTo(); rule.mainClock.advanceTimeBy(300)
      val node = n.fetchSemanticsNode(); val b = node.boundsInRoot
      assertEquals("$sl fully visible after scrolling down: $b", node.size.width.toFloat(), b.width, 1f)
      assertEquals("$sl fully visible after scrolling down: $b", node.size.height.toFloat(), b.height, 1f)
    }
  }

  @Config(qualifiers = "w393dp-h851dp-port-xxhdpi") @Test fun phone() = allChipsOnScreen()
  @Config(qualifiers = "w851dp-h393dp-land-xxhdpi") @Test fun phoneLandscape() = allChipsOnScreen()
  @Config(qualifiers = "w411dp-h797dp-port-xxhdpi") @Test fun foldFolded() = allChipsOnScreen()
  @Config(qualifiers = "w841dp-h701dp-land-xxhdpi") @Test fun foldUnfolded() = allChipsOnScreen()
  @Config(qualifiers = "w701dp-h841dp-port-xxhdpi") @Test fun foldUnfoldedPortrait() = allChipsOnScreen()
}
