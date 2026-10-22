package com.peyton.toddlergame

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.*

/** All sounds are synthesized in code: no bundled audio assets. */
object Sfx {
  const val RATE = 22050
  private val rnd = java.util.Random(7)

  fun tone(f0: Double, f1: Double, secs: Double, vib: Double = 0.0, vibRate: Double = 6.0, harm: Double = 0.3,
           decay: Double = 3.0, noise: Double = 0.0, gain: Double = 1.0): FloatArray {
    val n = (RATE * secs).toInt(); val out = FloatArray(n); var ph = 0.0
    for (i in 0 until n) {
      val t = i.toDouble() / RATE; val x = i.toDouble() / n
      val f = f0 + (f1 - f0) * x + vib * sin(2 * PI * vibRate * t)
      ph += 2 * PI * f / RATE
      val env = min(1.0, t / 0.008) * exp(-decay * x) * (if (x > 0.85) (1 - x) / 0.15 else 1.0)
      var s = (sin(ph) + harm * sin(2 * ph) + harm * 0.5 * sin(3 * ph)) / (1 + 1.5 * harm)
      if (noise > 0) s = s * (1 - noise) + (rnd.nextDouble() * 2 - 1) * noise
      out[i] = (s * env * gain).toFloat()
    }
    return out
  }
  fun silence(secs: Double) = FloatArray((RATE * secs).toInt())
  fun seq(vararg p: FloatArray): FloatArray { val r = FloatArray(p.sumOf { it.size }); var o = 0; for (a in p) { a.copyInto(r, o); o += a.size }; return r }
  fun mix(a: FloatArray, b: FloatArray): FloatArray { val r = FloatArray(max(a.size, b.size)); for (i in a.indices) r[i] += a[i]; for (i in b.indices) r[i] += b[i]; return r }

  fun bell(f: Double, secs: Double = 0.9) = mix(tone(f, f, secs, harm = 0.15, decay = 5.0), tone(f * 2, f * 2, secs, harm = 0.0, decay = 9.0, gain = 0.35))
  /** C-major pentatonic: every combination sounds pleasant. */
  val PENTA = doubleArrayOf(261.63, 293.66, 329.63, 392.0, 440.0, 523.25, 587.33, 659.25, 783.99, 880.0)
  val bells: List<FloatArray> by lazy { PENTA.map { bell(it) } }
  fun pop(freq: Double) = mix(tone(freq * 0.6, freq * 1.6, 0.08, harm = 0.1, decay = 4.0), tone(3000.0, 3000.0, 0.015, noise = 0.9, gain = 0.5))
  fun boing(f: Double) = tone(f * 0.5, f * 1.5, 0.3, vib = 30.0, vibRate = 12.0, decay = 2.5)
  fun plink(f: Double, gain: Double = 0.6) = tone(f, f * 0.98, 0.15, harm = 0.2, decay = 6.0, gain = gain)

  val animal: Map<Critter, FloatArray> by lazy {
    mapOf(
      Critter.DOG to seq(tone(330.0, 240.0, 0.13, harm = 0.9, noise = 0.25, decay = 2.0), silence(0.06), tone(350.0, 250.0, 0.15, harm = 0.9, noise = 0.25, decay = 2.0)),
      Critter.CAT to seq(tone(450.0, 760.0, 0.2, vib = 15.0, harm = 0.5, decay = 0.3), tone(760.0, 470.0, 0.35, vib = 20.0, harm = 0.5, decay = 2.0)),
      Critter.PIG to seq(tone(260.0, 300.0, 0.12, harm = 1.0, noise = 0.3, vib = 30.0, vibRate = 40.0), silence(0.05), tone(280.0, 320.0, 0.14, harm = 1.0, noise = 0.3, vib = 30.0, vibRate = 40.0)),
      Critter.COW to tone(190.0, 150.0, 0.9, vib = 8.0, vibRate = 4.0, harm = 1.0, decay = 1.2),
      Critter.FROG to seq(tone(180.0, 220.0, 0.09, harm = 1.0, vib = 60.0, vibRate = 45.0), silence(0.04), tone(200.0, 260.0, 0.14, harm = 1.0, vib = 60.0, vibRate = 45.0)),
      Critter.LION to tone(150.0, 115.0, 0.7, vib = 20.0, vibRate = 25.0, harm = 1.0, noise = 0.35, decay = 1.5),
      Critter.DUCK to seq(tone(520.0, 420.0, 0.16, harm = 1.0, noise = 0.2, vib = 40.0, vibRate = 50.0), silence(0.07), tone(500.0, 400.0, 0.18, harm = 1.0, noise = 0.2, vib = 40.0, vibRate = 50.0)),
      Critter.CHICK to seq(tone(1800.0, 2400.0, 0.07, decay = 1.0), silence(0.04), tone(1900.0, 2500.0, 0.07), silence(0.04), tone(2000.0, 2600.0, 0.08)),
    )
  }
  val peekaboo by lazy { seq(tone(1046.5, 1046.5, 0.14, decay = 1.5), tone(1318.5, 1318.5, 0.14, decay = 1.5), tone(784.0, 1568.0, 0.45, vib = 25.0, decay = 1.2)) }
  val giggle by lazy { seq(*(0 until 5).flatMap { listOf(tone(700.0 + it * 80, 900.0 + it * 80, 0.07, decay = 2.0), silence(0.03)) }.toTypedArray()) }
  val chime by lazy { seq(bell(783.99, 0.18), bell(1046.5, 0.5)) }
  val boom by lazy { mix(tone(90.0, 40.0, 0.8, noise = 0.85, decay = 4.0), tone(2000.0, 400.0, 0.25, noise = 0.5, decay = 6.0, gain = 0.4)) }
  val balloonPop by lazy { mix(tone(2600.0, 2600.0, 0.035, noise = 1.0, decay = 8.0), tone(170.0, 60.0, 0.14, harm = 0.2, decay = 5.0, gain = 0.8)) }
  val wheee by lazy { tone(500.0, 1400.0, 0.6, vib = 20.0, vibRate = 7.0, decay = 1.0, gain = 0.6) }
  val blub by lazy { seq(tone(300.0, 700.0, 0.06, harm = 0.0, decay = 2.0), tone(420.0, 950.0, 0.07, harm = 0.0, decay = 2.0)) }
  val inflate by lazy { tone(260.0, 820.0, 0.2, harm = 0.25, decay = 3.0, gain = 0.45) }
  val bubblePop by lazy { tone(900.0, 1600.0, 0.06, harm = 0.0, decay = 6.0, gain = 0.5) }
  val plip by lazy { tone(1200.0, 1800.0, 0.05, harm = 0.0, decay = 4.0, gain = 0.6) }
  val snap by lazy { seq(tone(2500.0, 2500.0, 0.025, noise = 0.9), silence(0.05), tone(2700.0, 2700.0, 0.025, noise = 0.9)) }
  val flutter by lazy { seq(*listOf(1046.5, 1318.5, 1568.0, 2093.0).map { tone(it, it * 1.02, 0.06, harm = 0.0, decay = 3.0, gain = 0.6) }.toTypedArray()) }
  val zoom by lazy { tone(180.0, 520.0, 0.55, noise = 0.25, harm = 0.8, decay = 1.5, gain = 0.45) }
  private fun horn(a: Double, b: Double, secs: Double) = mix(tone(a, a, secs, harm = 0.9, decay = 0.6, gain = 0.6), tone(b, b, secs, harm = 0.9, decay = 0.6, gain = 0.6))
  /** 0 car beep-beep, 1 bus, 2 truck, 3 train toot */
  val honks: List<FloatArray> by lazy { listOf(
    seq(horn(440.0, 554.0, 0.13), silence(0.06), horn(440.0, 554.0, 0.16)),
    horn(311.0, 392.0, 0.45),
    seq(horn(233.0, 294.0, 0.3), silence(0.05), horn(233.0, 294.0, 0.3)),
    seq(mix(horn(370.0, 466.0, 0.55), tone(554.0, 554.0, 0.55, vib = 6.0, harm = 0.8, decay = 0.6, gain = 0.5)), silence(0.05), mix(horn(370.0, 466.0, 0.25), tone(554.0, 554.0, 0.25, harm = 0.8, gain = 0.5)))) }
  val kick by lazy { tone(140.0, 45.0, 0.35, harm = 0.1, decay = 5.0, gain = 1.2) }
  val tom by lazy { tone(260.0, 170.0, 0.35, harm = 0.25, decay = 4.5) }
  val snare by lazy { mix(tone(220.0, 180.0, 0.18, decay = 6.0, gain = 0.6), tone(4000.0, 4000.0, 0.22, noise = 1.0, decay = 6.0, gain = 0.55)) }
  val cymbal by lazy { tone(6000.0, 6000.0, 1.0, noise = 1.0, decay = 4.0, gain = 0.35) }
  val xylo: List<FloatArray> by lazy { PENTA.map { f -> mix(tone(f * 2, f * 2, 0.5, harm = 0.05, decay = 9.0), tone(f * 8, f * 8, 0.3, harm = 0.0, decay = 16.0, gain = 0.2)) } }
  val march by lazy { seq(*listOf(67, 72, 76, 72, 79, 76, 72, 76, 79, 84).map { n -> seq(bell(440.0 * Math.pow(2.0, (n - 69) / 12.0), 0.22), silence(0.03)) }.toTypedArray()) }
  val whoosh by lazy { tone(400.0, 1600.0, 0.35, noise = 0.6, decay = 1.0, gain = 0.5) }
}

/** One streaming AudioTrack with a small software mixer; voices capped so chaos stays pleasant. */
object Audio {
  private class Voice(val data: FloatArray, val gain: Float) { var pos = 0 }
  private val voices = ArrayList<Voice>()
  const val MAX_VOICES = 6
  @Volatile var volume = 0.8f
  @Volatile private var running = false

  fun start() {
    if (running) return
    running = true
    Thread {
      val rate = Sfx.RATE
      val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
      val track = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(max(min, 2048)).setTransferMode(AudioTrack.MODE_STREAM)
        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build()
      track.play()
      val frames = 256; val buf = ShortArray(frames); val mixBuf = FloatArray(frames)
      while (running) {
        mixBuf.fill(0f)
        synchronized(voices) {
          val it = voices.iterator()
          while (it.hasNext()) {
            val v = it.next(); val n = min(frames, v.data.size - v.pos)
            for (i in 0 until n) mixBuf[i] += v.data[v.pos + i] * v.gain
            v.pos += n; if (v.pos >= v.data.size) it.remove()
          }
        }
        val vol = volume
        for (i in 0 until frames) buf[i] = (tanh(mixBuf[i] * vol) * 32000f).toInt().toShort()
        track.write(buf, 0, frames)
      }
      track.stop(); track.release()
    }.apply { priority = Thread.MAX_PRIORITY; name = "toddler-audio" }.start()
  }
  fun stop() { running = false; synchronized(voices) { voices.clear() } }
  fun play(data: FloatArray, gain: Float = 0.7f) {
    if (volume <= 0f) return
    BgMusic.duck()
    synchronized(voices) { if (voices.size >= MAX_VOICES) voices.removeAt(0); voices.add(Voice(data, gain)) }
  }
  fun activeVoices() = synchronized(voices) { voices.size }
}
