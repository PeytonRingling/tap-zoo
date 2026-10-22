package com.peyton.toddlergame

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The animal clips come from the optional private `sounds/` submodule. With it all 8 must be packaged; without it
 *  (public clone) none are, and init/play must still work (synth fallback). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnimalSoundsTest {
  @Test fun clipsMatchSubmoduleAndPlaybackNeverCrashes() {
    val c = ApplicationProvider.getApplicationContext<Context>()
    val inTree = File("../sounds/res/raw").listFiles { f -> f.name.startsWith("animal_") && f.name.endsWith(".ogg") }?.size ?: 0
    assertEquals(inTree, AnimalSounds.available(c))
    AnimalSounds.init(c)
    Critter.entries.forEach { AnimalSounds.play(it) }
    AnimalSounds.release()
  }
}
