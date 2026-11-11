package com.peyton.toddlergame

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.min

/** Tapping every soundboard animal (and its reaction frames) must never crash, on both Fold screens. v13.2 crashed on every
 *  tile past the 9th (sheep, rooster, owl, donkey): the tap glow picked `Pal.vivid[i]`, and there are only 9 colours. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w841dp-h701dp-land-xxhdpi")
class AnimalsTapTest {
  @get:Rule val rule = createComposeRule()

  private fun tapEveryTile() {
    rule.mainClock.autoAdvance = false
    rule.setContent { Box(Modifier.fillMaxSize()) { AnimalsScreen() } }
    rule.mainClock.advanceTimeBy(500)
    var tiles = 0
    rule.onRoot().performTouchInput {
      val r = Layout.grid(Critter.entries.size, width.toFloat(), height.toFloat(), min(width, height) * 0.04f, square = true)
      tiles = r.size
      r.forEach { down(it.center); up() }
    }
    assertEquals(Critter.entries.size, tiles)
    repeat(20) { rule.mainClock.advanceTimeBy(16) }   // draw the hop/glow reaction for all of them
    rule.onRoot().captureRoboImage("build/animals-tapped-${Critter.entries.size}.png")   // force a real draw pass (the glow draws while the reaction is on)
    repeat(40) { rule.mainClock.advanceTimeBy(16) }
    rule.onRoot().performTouchInput { down(Offset(width * 0.99f, height * 0.99f)); up() }   // and a miss
    rule.mainClock.advanceTimeBy(300)
  }

  @Test fun everyAnimalTileTapsWithoutCrashing() = tapEveryTile()
  @Config(qualifiers = "w411dp-h797dp-port-xxhdpi") @Test fun everyAnimalTileTapsWithoutCrashingFolded() = tapEveryTile()

  @Test fun everyAnimalSoundPathPlays() {
    val c = ApplicationProvider.getApplicationContext<Context>()
    AnimalSounds.init(c)
    try { for (k in Critter.entries) { GameSound.animal(k, 1f, 0); Thread.sleep(360) }   // past the 350 ms throttle, so each really plays
      for (k in Critter.entries) assert(Sfx.animal.getValue(k).isNotEmpty())               // synth fallbacks too
      for (a in WashAnimal.entries) { val s = FakeSink(); val m = WashModel(s, kotlin.random.Random(1), a); m.w = 1000f; m.h = 800f } }
    finally { AnimalSounds.release() }
  }
}
