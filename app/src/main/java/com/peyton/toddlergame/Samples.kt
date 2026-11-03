package com.peyton.toddlergame

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Recorded one-shot sounds from res/raw, played through the app's own mixer ([Audio]) so they duck the music, respect the voice
 *  cap and can be pitched per balloon. They're 16-bit PCM WAV (tiny, no decoder needed). Loaded by name: when a file is missing or
 *  unreadable, the synthesized sound is used instead, so a build without it still pops. */
object Samples {
  /** Pitch steps for the balloon pop, lowest first (index 0 = biggest balloon). */
  @Volatile private var pops: List<FloatArray>? = null
  const val POP_STEPS = 7
  const val POP_LOW = 0.92f
  const val POP_HIGH = 1.08f

  fun load(c: Context) {
    if (pops != null) return
    pops = runCatching {
      val id = c.resources.getIdentifier("pop_balloon", "raw", c.packageName)
      if (id == 0) null else c.resources.openRawResource(id).use { parseWav(it.readBytes()) }?.let(::pitchSteps)
    }.getOrNull()
  }
  fun loaded() = pops != null
  internal fun reset() { pops = null }

  /** The cartoon balloon pop. [size01] 0 = smallest balloon (highest pitch) .. 1 = biggest (lowest), so repeats never sound identical. */
  fun balloonPop(size01: Float): FloatArray {
    val v = pops ?: return Sfx.balloonPop
    val s = if (size01.isNaN()) 0.5f else size01.coerceIn(0f, 1f)
    return v[((1f - s) * (v.size - 1)).roundToInt()]
  }

  internal fun pitchSteps(x: FloatArray): List<FloatArray> =
    (0 until POP_STEPS).map { i -> resample(x, POP_LOW + (POP_HIGH - POP_LOW) * i / (POP_STEPS - 1)) }

  /** Plays [x] [ratio] times faster (ratio > 1 = higher and shorter), linear interpolation. */
  internal fun resample(x: FloatArray, ratio: Float): FloatArray {
    if (x.isEmpty() || ratio <= 0f) return FloatArray(0)
    val n = ((x.size - 1) / ratio).toInt() + 1
    return FloatArray(n) { i -> val p = i * ratio; val j = p.toInt(); val f = p - j
      if (j + 1 < x.size) x[j] * (1 - f) + x[j + 1] * f else x[x.size - 1] }
  }

  /** Minimal RIFF/WAVE reader: PCM 16-bit, mono or stereo (mixed down), any rate (resampled to [Sfx.RATE]). Null if it isn't one. */
  internal fun parseWav(b: ByteArray): FloatArray? {
    if (b.size < 12 || String(b, 0, 4, Charsets.US_ASCII) != "RIFF" || String(b, 8, 4, Charsets.US_ASCII) != "WAVE") return null
    val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
    var pos = 12; var ch = 0; var rate = 0; var bits = 0; var fmt = 0
    while (pos + 8 <= b.size) {
      val id = String(b, pos, 4, Charsets.US_ASCII); val len = bb.getInt(pos + 4)
      if (len < 0 || pos + 8 + len > b.size) return null
      when (id) {
        "fmt " -> { if (len < 16) return null; fmt = bb.getShort(pos + 8).toInt(); ch = bb.getShort(pos + 10).toInt(); rate = bb.getInt(pos + 12); bits = bb.getShort(pos + 22).toInt() }
        "data" -> {
          if (fmt != 1 || bits != 16 || ch !in 1..2 || rate !in 8000..192000) return null
          val frames = len / (2 * ch)
          if (frames == 0) return null
          val out = FloatArray(frames) { i -> var s = 0f; for (k in 0 until ch) s += bb.getShort(pos + 8 + (i * ch + k) * 2) / 32768f; s / ch }
          return if (rate == Sfx.RATE) out else resample(out, rate.toFloat() / Sfx.RATE)
        }
      }
      pos += 8 + len + (len and 1)
    }
    return null
  }
}
