package com.peyton.toddlergame

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** The pig's oink-oink: two short, low, buzzy grunts that glide down, as loud as the other animals. */
class PigSoundTest {
  private val R = Sfx.RATE
  private val o = Sfx.oinkOink

  /** Autocorrelation pitch in [lo]..[hi] Hz of [a] from [from] s, over 50 ms. */
  private fun pitch(a: FloatArray, from: Double, lo: Double = 70.0, hi: Double = 600.0): Double {
    val s = (from * R).toInt(); val n = (0.05 * R).toInt()
    var best = 0.0; var lag = 0
    for (l in (R / hi).toInt()..(R / lo).toInt()) { var c = 0.0; for (i in 0 until n) c += a[s + i] * a[s + i + l]; if (c > best) { best = c; lag = l } }
    return R.toDouble() / lag
  }
  /** Start/end (s) of each loud stretch, from 10 ms RMS windows. */
  private fun syllables(a: FloatArray): List<Pair<Double, Double>> {
    val win = R / 100; val loud = (0 until a.size / win).map { w -> Sfx.rms(a.copyOfRange(w * win, (w + 1) * win)) > 0.03 }
    val out = ArrayList<Pair<Double, Double>>(); var start = -1
    loud.forEachIndexed { i, on -> if (on && start < 0) start = i; if (!on && start >= 0) { out += start / 100.0 to i / 100.0; start = -1 } }
    if (start >= 0) out += start / 100.0 to loud.size / 100.0
    return out
  }

  @Test fun pigPlaysTheSynthOinkNotTheSquealyClip() {
    assertSame(o, Sfx.animal.getValue(Critter.PIG))
    assertTrue(Critter.PIG in AnimalSounds.SYNTH_ONLY)
    assertEquals("only the pig skips its clip", setOf(Critter.PIG), AnimalSounds.SYNTH_ONLY)
  }

  @Test fun twoShortOinks() {
    val secs = o.size / R.toDouble(); assertTrue("length $secs", secs in 0.4..0.6)
    val syl = syllables(o); assertEquals("two syllables: $syl", 2, syl.size)
    syl.forEach { (a, b) -> assertTrue("each oink 0.1-0.25 s: $a..$b", b - a in 0.1..0.25) }
    assertTrue("a clear gap", syl[1].first - syl[0].second >= 0.05)
  }

  @Test fun lowAndGlidingDownLikeAGruntNotASqueal() {
    for ((a, b) in syllables(o)) {
      val early = pitch(o, a + 0.01); val late = pitch(o, b - 0.065)
      assertTrue("low: $early Hz", early in 120.0..200.0)
      assertTrue("glides down: $early -> $late", late < early * 0.92)
    }
  }

  @Test fun asLoudAsTheOtherAnimalsAndNeverClips() {
    val loud = syllables(o).flatMap { (a, b) -> o.copyOfRange((a * R).toInt(), (b * R).toInt()).toList() }.toFloatArray()
    assertEquals("RMS while oinking", Sfx.ANIMAL_RMS, Sfx.rms(loud), 0.06)
    assertTrue("peak ${o.maxOf { abs(it) }}", o.maxOf { abs(it) } <= 0.98f)
    assertTrue(o.all { it.isFinite() })
  }

  @Test fun sameOinkEveryTime() {
    assertArrayEquals(Sfx.oink(155.0, 112.0, 0.17, 3), Sfx.oink(155.0, 112.0, 0.17, 3), 0f)
  }

  /** Not a check: leaves build/oink.wav to listen to. */
  @Test fun writesAWavToListenTo() {
    val pcm = ByteBuffer.allocate(o.size * 2).order(ByteOrder.LITTLE_ENDIAN); o.forEach { pcm.putShort((it * 32000).toInt().toShort()) }
    val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    h.put("RIFF".toByteArray()).putInt(36 + o.size * 2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(R).putInt(R * 2)
      .putShort(2).putShort(16).put("data".toByteArray()).putInt(o.size * 2)
    File("build").mkdirs(); File("build/oink.wav").writeBytes(h.array() + pcm.array())
  }
}
