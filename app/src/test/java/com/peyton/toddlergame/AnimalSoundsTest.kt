package com.peyton.toddlergame

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The animal clips come from the optional private `sounds/` submodule. With it every clip but the synth-only pig must be used; without it
 *  (public clone) none are, and init/play must still work (synth fallback). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnimalSoundsTest {
  @Test fun clipsMatchSubmoduleAndPlaybackNeverCrashes() {
    val c = ApplicationProvider.getApplicationContext<Context>()
    val skipped = AnimalSounds.SYNTH_ONLY.map { "animal_${it.name.lowercase()}.ogg" }
    val inTree = listOf("../sounds/res/raw", "src/main/res/raw").sumOf { d -> File(d).listFiles { f -> f.name.startsWith("animal_") && f.name.endsWith(".ogg") && f.name !in skipped }?.size ?: 0 }
    assertTrue("the 5 public recordings are always there", AnimalSounds.available(c) >= 5)
    assertEquals(inTree, AnimalSounds.available(c))
    AnimalSounds.init(c)
    Critter.entries.forEach { AnimalSounds.play(it) }
    AnimalSounds.release()
  }
}
