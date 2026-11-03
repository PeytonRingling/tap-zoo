package com.peyton.toddlergame

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SamplesTest {
  private val c = ApplicationProvider.getApplicationContext<Context>()
  @After fun tearDown() = Samples.reset()

  private fun wav(samples: ShortArray, ch: Int = 1, rate: Int = 44100, bits: Int = 16, fmt: Int = 1, extra: ByteArray = ByteArray(0)): ByteArray {
    val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { samples.forEach { putShort(it) } }.array()
    val o = ByteArrayOutputStream()
    fun i32(v: Int) = o.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
    fun i16(v: Int) = o.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array())
    o.write("RIFF".toByteArray()); i32(36 + extra.size + data.size); o.write("WAVE".toByteArray())
    o.write(extra)
    o.write("fmt ".toByteArray()); i32(16); i16(fmt); i16(ch); i32(rate); i32(rate * ch * 2); i16(ch * 2); i16(bits)
    o.write("data".toByteArray()); i32(data.size); o.write(data)
    return o.toByteArray()
  }

  @Test fun shippedPopLoadsAsSevenPitchSteps() {
    assertSame("before loading: synth fallback", Sfx.balloonPop, Samples.balloonPop(0.5f))
    Samples.load(c)
    assertTrue(Samples.loaded())
    val big = Samples.balloonPop(1f); val small = Samples.balloonPop(0f); val mid = Samples.balloonPop(0.5f)
    assertNotSame(Sfx.balloonPop, mid)
    assertTrue("bigger balloon = lower = longer", big.size > mid.size && mid.size > small.size)
    // ~119 ms at 44.1 kHz, ±8%
    assertTrue(big.size in 5000..5800 && small.size in 4700..5300)
    for (x in listOf(big, small, mid)) { assertTrue(x.all { it.isFinite() && it in -1f..1f }); assertTrue(x.maxOf { kotlin.math.abs(it) } > 0.5f) }
    assertSame("NaN size doesn't crash", mid, Samples.balloonPop(Float.NaN))
    assertSame(big, Samples.balloonPop(7f)); assertSame(small, Samples.balloonPop(-3f))
  }

  @Test fun parsesMonoStereoAndOtherRates() {
    assertArrayEquals(floatArrayOf(0.5f, -0.5f), Samples.parseWav(wav(shortArrayOf(16384, -16384)))!!, 1e-6f)
    assertArrayEquals("stereo mixed down", floatArrayOf(0.25f), Samples.parseWav(wav(shortArrayOf(16384, 0), ch = 2))!!, 1e-6f)
    val half = Samples.parseWav(wav(ShortArray(100) { 1000 }, rate = 22050))!!
    assertTrue("22.05 kHz resampled to 44.1 kHz", half.size in 197..200)
    // an unknown chunk before fmt is skipped
    val list = "LIST".toByteArray() + byteArrayOf(4, 0, 0, 0) + "abcd".toByteArray()
    assertEquals(2, Samples.parseWav(wav(shortArrayOf(1, 2), extra = list))!!.size)
  }

  @Test fun rejectsAnythingElseInsteadOfCrashing() {
    assertNull(Samples.parseWav(ByteArray(0)))
    assertNull(Samples.parseWav("not a wav file at all".toByteArray()))
    assertNull("8-bit", Samples.parseWav(wav(shortArrayOf(1, 2), bits = 8)))
    assertNull("float", Samples.parseWav(wav(shortArrayOf(1, 2), fmt = 3)))
    assertNull("6 channels", Samples.parseWav(wav(ShortArray(12), ch = 6)))
    assertNull("empty data", Samples.parseWav(wav(ShortArray(0))))
    val ok = wav(shortArrayOf(1, 2, 3, 4))
    assertNull("truncated", Samples.parseWav(ok.copyOf(ok.size - 3)))
    val bad = ok.copyOf(); ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putInt(40, -5); assertNull("negative length", Samples.parseWav(bad))
    assertEquals(0, Samples.resample(FloatArray(0), 1f).size); assertEquals(0, Samples.resample(FloatArray(4), 0f).size)
  }

  @Test fun musicTracksArePackagedAndCredited() {
    listOf("music_balloons", "music_bubbles", "pop_balloon").forEach { assertTrue(it, BgMusic.has(c, it)) }
    assertEquals("music_bubbles", MusicDirector.trackFor(Mode.BUBBLES, MusicSlot.entries.toSet(), 4) { BgMusic.has(c, it) }?.name)
    for (t in listOf("Monkeys Spinning Monkeys", "Beachfront Celebration"))
      assertTrue(t, CREDITS.contains("\"$t\" Kevin MacLeod (incompetech.com)\nLicensed under Creative Commons: By Attribution 4.0 License\nhttp://creativecommons.org/licenses/by/4.0/"))
    assertTrue(CREDITS.contains("Edited:")); assertTrue(CREDITS.contains("LilMati") && CREDITS.contains("Breviceps"))
  }
}
