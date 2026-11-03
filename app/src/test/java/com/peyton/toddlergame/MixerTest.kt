package com.peyton.toddlergame

import org.junit.Assert.*
import org.junit.Test

class MixerTest {
  private fun const(v: Float, n: Int) = FloatArray(n) { v }

  @Test fun mixesWithGainAndDropsFinishedVoices() {
    val m = Mixer(maxVoices = 4, fadeFrames = 8); val out = FloatArray(16)
    m.add(const(0.5f, 20), 0.5f); m.add(const(1f, 10), 0.2f)
    assertTrue(m.render(out))
    assertEquals(0.25f + 0.2f, out[0], 1e-6f); assertEquals(0.25f + 0.2f, out[9], 1e-6f); assertEquals(0.25f, out[10], 1e-6f)
    assertEquals(1, m.total())                       // the 10-sample voice finished
    m.render(out); assertEquals(0.25f, out[3], 1e-6f); assertEquals(0f, out[4], 1e-6f); assertEquals(0, m.total())
  }

  @Test fun stealingFadesTheOldestInsteadOfCuttingIt() {
    val fade = 32; val m = Mixer(maxVoices = 2, fadeFrames = fade)
    m.add(const(1f, 1000), 1f)                       // oldest: will be stolen mid-waveform
    m.add(FloatArray(1000), 1f); m.add(FloatArray(1000), 1f)   // silent voices, so out = the stolen voice only
    assertEquals(2, m.active()); assertEquals(3, m.total())
    val out = FloatArray(64); m.render(out)
    assertTrue("starts near full level, not a jump to 0", out[0] > 0.9f)
    for (i in 1 until fade) assertTrue("fade must be monotonic at $i", out[i] <= out[i - 1])
    for (i in fade until 64) assertEquals(0f, out[i], 0f)
    assertEquals(2, m.total())                       // stolen voice removed once faded
  }

  @Test fun rapidStealingKeepsVoiceCountBounded() {
    val m = Mixer(maxVoices = 6, fadeFrames = 220)
    repeat(200) { m.add(const(0.1f, 5000), 1f) }
    assertEquals(6, m.active()); assertTrue(m.total() <= 12)
    val out = FloatArray(256); m.render(out); out.forEach { assertTrue(it.isFinite() && it <= 1.2f + 1e-4f) }
  }

  @Test fun emptySoundIsIgnored() { val m = Mixer(4, 8); m.add(FloatArray(0), 1f); assertEquals(0, m.total()) }

  @Test fun staleRenderDoesNotConsumeVoices() {
    val m = Mixer(4, 8); m.add(FloatArray(10) { it.toFloat() }, 1f)
    val out = FloatArray(4) { 9f }
    assertFalse(m.render(out) { false }); assertArrayEquals(FloatArray(4) { 9f }, out, 0f)   // untouched
    assertTrue(m.render(out)); assertEquals(0f, out[0], 0f); assertEquals(3f, out[3], 0f)       // still from the start
  }

  @Test fun clearSilencesEverything() {
    val m = Mixer(4, 8); repeat(3) { m.add(const(1f, 100), 1f) }; m.clear()
    val out = FloatArray(8); m.render(out); assertEquals(0, m.total()); out.forEach { assertEquals(0f, it, 0f) }
  }
}
