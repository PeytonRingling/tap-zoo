package com.peyton.toddlergame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val FOLDED = "w411dp-h797dp-port-xxhdpi"

/** Renders screens at Pixel Fold inner (841x701dp) and outer (411x797dp) sizes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w841dp-h701dp-land-xxhdpi")
class ScreensTest {
  @get:Rule val rule = createComposeRule()
  private val out = System.getProperty("screens.dir") ?: "../screens"

  private fun shot(name: String, start: String?, taps: List<Pair<Float, Float>> = emptyList(), drag: Boolean = false, after: Long = 250, settle: Long = 2500) {
    rule.mainClock.autoAdvance = false
    rule.setContent { Box(Modifier.fillMaxSize()) { App(start, onExit = {}) } }
    rule.mainClock.advanceTimeBy(settle)
    if (drag) rule.onRoot().performTouchInput {
      down(Offset(width * 0.15f, height * 0.5f))
      for (i in 1..40) { moveTo(Offset(width * (0.15f + i * 0.018f), height * (0.5f + 0.25f * kotlin.math.sin(i * 0.3f)))); rule.mainClock.advanceTimeBy(16) }
      up()
    }
    taps.forEach { (x, y) -> rule.onRoot().performTouchInput { down(Offset(width * x, height * y)); up() }; rule.mainClock.advanceTimeBy(40) }
    rule.mainClock.advanceTimeBy(after)
    rule.onRoot().captureRoboImage("$out/$name.png")
  }
  private val grid = listOf(0.2f to 0.3f, 0.5f to 0.45f, 0.8f to 0.35f, 0.35f to 0.65f, 0.65f to 0.6f)

  @Test fun menu() = shot("01_menu_unfolded", null)
  @Test fun settings() = shot("02_parent_settings_unfolded", "SETTINGS")
  @Test fun balloons() = shot("03_balloons_unfolded", "BALLOONS", settle = 4000)
  @Test fun balloonsPop() = shot("04_balloons_pop_unfolded", "BALLOONS", grid, after = 140, settle = 4000)
  @Test fun aquarium() = shot("05_aquarium_unfolded", "AQUARIUM", listOf(0.5f to 0.4f))
  @Test fun vehicles() = shot("06_vehicles_unfolded", "VEHICLES", settle = 3000)
  @Test fun garden() = shot("07_garden_unfolded", "GARDEN")
  @Test fun drums() = shot("08_drums_unfolded", "DRUMS", listOf(0.3f to 0.3f, 0.6f to 0.8f))
  @Test fun animals() = shot("09_animals_unfolded", "ANIMALS", listOf(0.5f to 0.3f))
  @Test fun bubbles() = shot("10_bubbles_unfolded", "BUBBLES", listOf(0.3f to 0.5f, 0.7f to 0.4f))
  @Test fun peekaboo() = shot("11_peekaboo_unfolded", "PEEKABOO", listOf(0.25f to 0.7f))
  @Test fun paint() = shot("12_paint_unfolded", "PAINT", drag = true)
  @Test fun music() = shot("13_music_unfolded", "MUSIC", listOf(0.2f to 0.3f))
  @Test fun sky() = shot("14_sky_unfolded", "SKY", listOf(0.5f to 0.3f))
  @Test fun bedtime() = shot("21_bedtime_unfolded", "BEDTIME", listOf(0.3f to 0.3f))
  @Config(qualifiers = FOLDED) @Test fun menuFolded() = shot("15_menu_folded", null)
  @Config(qualifiers = FOLDED) @Test fun balloonsFolded() = shot("16_balloons_pop_folded", "BALLOONS", grid, after = 140, settle = 4000)
  @Config(qualifiers = FOLDED) @Test fun vehiclesFolded() = shot("17_vehicles_folded", "VEHICLES", settle = 3000)
  @Config(qualifiers = FOLDED) @Test fun aquariumFolded() = shot("18_aquarium_folded", "AQUARIUM")
  @Config(qualifiers = FOLDED) @Test fun gardenFolded() = shot("19_garden_folded", "GARDEN")
  @Config(qualifiers = FOLDED) @Test fun drumsFolded() = shot("20_drums_folded", "DRUMS")

  // ---- App updates row in Parent settings (behind the 3 s gear hold); one render per state
  private val rel9 = GhRelease("v9", "9", 2, "tap-zoo-v9-vc2.apk", "https://example.invalid/x.apk", 1)
  private fun upd(name: String, ui: UpdUi) { Updater.ui = ui; try { shot("updates/$name", "SETTINGS") } finally { Updater.ui = UpdUi() } }
  @Test fun updChecking() = upd("U1_checking", UpdUi(UpdPhase.CHECKING, "Checking for updates…"))
  @Test fun updUpToDate() = upd("U2_up_to_date", UpdUi(UpdPhase.UP_TO_DATE, "Tap Zoo is up to date (version 9).", rel9))
  @Test fun updAvailable() = upd("U3_update_available", UpdUi(UpdPhase.AVAILABLE, "Version 9 is available.", rel9))
  @Test fun updError() = upd("U4_error_no_internet", UpdUi(UpdPhase.ERROR, "No internet connection. Check the network and try again."))
  @Test fun updNeedsPermission() = upd("U5_needs_permission", UpdUi(UpdPhase.NEEDS_PERMISSION, "Version 9 is ready. Allow Tap Zoo to install updates, then come back.", rel9))
  @Config(qualifiers = FOLDED) @Test fun updAvailableFolded() = upd("U6_update_available_folded", UpdUi(UpdPhase.AVAILABLE, "Version 9 is available.", rel9))
}
