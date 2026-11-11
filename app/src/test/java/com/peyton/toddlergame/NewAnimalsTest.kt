package com.peyton.toddlergame

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** v13.3 animals: horse, sheep, rooster, owl and donkey, each with a real recording in the public repo, credited. */
class NewAnimalsTest {
  private val added = listOf(Critter.HORSE, Critter.SHEEP, Critter.ROOSTER, Critter.OWL, Critter.DONKEY)

  @Test fun theyreOnTheSoundboard() {
    assertTrue(Critter.entries.containsAll(added)); assertEquals(13, Critter.entries.size)
    assertEquals("no animal twice", Critter.entries.size, Critter.entries.map { it.name }.toSet().size)
  }

  @Test fun eachHasAShortRealRecordingThatsCredited() {
    val credits = File("../SOUND_CREDITS.md").readText()
    for (k in added) { val f = File("src/main/res/raw/animal_${k.name.lowercase()}.ogg")
      assertTrue("$f exists", f.exists()); assertTrue("$f small: ${f.length()}", f.length() in 3_000L..40_000L)
      assertTrue("animal_${k.name.lowercase()}.ogg credited", credits.contains("animal_${k.name.lowercase()}.ogg")) }
    assertTrue("CC BY credit in the app", CREDITS.contains("Lemasson") && CREDITS.contains("CC BY 4.0"))
    assertFalse("only free licences", Regex("animal_(horse|sheep|rooster|owl|donkey).*(NC|ND|BY-SA)").containsMatchIn(credits))
  }

  @Test fun eachStillHasASynthFallback() {
    for (k in added) { val s = Sfx.animal.getValue(k); assertTrue(s.size > Sfx.RATE / 5); assertTrue(s.all { it.isFinite() && kotlin.math.abs(it) <= 1.5f }) }
  }
}
