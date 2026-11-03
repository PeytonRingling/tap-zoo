package com.peyton.toddlergame

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.*

/** Sound effects synthesized in code (animal clips and music are separate, see AnimalSounds/BgMusic). */
object Sfx {
  /** 44.1 kHz so short transients (pops, clicks) keep their high end; every sound is defined in Hz, so pitches are unchanged. */
  const val RATE = 44100
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

  /** Synthesizes every lazy sound up front (call off the main thread at startup) so the first tap in a mode doesn't stall
   *  the UI while its sounds are generated. `by lazy` is synchronized, so racing a tap is safe. Returns them for tests. */
  fun prewarm(): List<FloatArray> = animal.values + bells + xylo + honks +
    listOf(peekaboo, giggle, chime, boom, balloonPop, wheee, blub, inflate, bubblePop, plip, snap, flutter, zoom, kick, tom, snare, cymbal, march, whoosh)
}

/** Pure software mixer (no Android types, unit-tested). Voices are capped so chaos stays pleasant; when a new sound would
 *  exceed the cap, the oldest voice is faded out over [fadeFrames] instead of being cut mid-waveform (which clicks). */
internal class Mixer(val maxVoices: Int, val fadeFrames: Int) {
  private class Voice(val data: FloatArray, val gain: Float) { var pos = 0; var fadeLeft = -1 }
  private val voices = ArrayList<Voice>()

  fun add(data: FloatArray, gain: Float) = synchronized(voices) {
    if (data.isEmpty()) return@synchronized
    val live = voices.filter { it.fadeLeft < 0 }
    if (live.size >= maxVoices) live.first().fadeLeft = fadeFrames
    // Rapid stealing could pile up fading voices: keep at most maxVoices of them, dropping the most-faded first.
    while (voices.count { it.fadeLeft >= 0 } > maxVoices) voices.remove(voices.filter { it.fadeLeft >= 0 }.minBy { it.fadeLeft })
    voices.add(Voice(data, gain))
  }
  fun clear() = synchronized(voices) { voices.clear() }
  /** Voices that are playing and not being faded out. */
  fun active(): Int = synchronized(voices) { voices.count { it.fadeLeft < 0 } }
  /** All voices still producing sound, including ones fading out. */
  fun total(): Int = synchronized(voices) { voices.size }

  /** Mixes the next [out].size frames into [out] (overwritten) only while [keepGoing] holds, checked under the voice lock so a
   *  stale output thread can never consume voices. Returns false (and leaves voices untouched) when it doesn't. */
  fun render(out: FloatArray, keepGoing: () -> Boolean = { true }): Boolean = synchronized(voices) {
    if (!keepGoing()) return@synchronized false
    out.fill(0f)
    val frames = out.size
    val it = voices.iterator()
    while (it.hasNext()) {
      val v = it.next()
      var n = min(frames, v.data.size - v.pos)
      if (v.fadeLeft >= 0) n = min(n, v.fadeLeft)
      for (i in 0 until n) {
        val g = if (v.fadeLeft >= 0) v.gain * (v.fadeLeft - i).toFloat() / fadeFrames else v.gain
        out[i] += v.data[v.pos + i] * g
      }
      v.pos += n
      if (v.fadeLeft >= 0) v.fadeLeft -= n
      if (v.pos >= v.data.size || v.fadeLeft == 0) it.remove()
    }
    true
  }
}

/** Where mixed PCM goes. The real one is an AudioTrack; tests use a fake. */
internal interface PcmOut { fun write(buf: ShortArray, frames: Int); fun close() }

/** One streaming output thread fed by [Mixer]. A generation counter guarantees that at most one output thread mixes at a time,
 *  even when stop()/start() race (fast pause/resume): a stale thread sees a newer generation and exits without touching voices. */
object Audio {
  const val MAX_VOICES = 6
  const val FRAMES = 256
  internal val mixer = Mixer(MAX_VOICES, fadeFrames = Sfx.RATE / 200) // 5 ms fade on voice steal
  @Volatile var volume = 0.8f
  @Volatile private var running = false
  @Volatile internal var generation = 0; private set
  /** Builds the output for a sample rate. Swapped in tests; a failure here must never crash the app. */
  internal var openOutput: (rate: Int) -> PcmOut = ::audioTrackOut
  internal var thread: Thread? = null; private set

  fun start() = synchronized(this) {
    if (running) return@synchronized
    running = true
    val my = ++generation
    thread = Thread { runOutput(my) }.apply { priority = Thread.MAX_PRIORITY; name = "toddler-audio"; isDaemon = true; start() }
  }
  fun stop() { synchronized(this) { running = false; generation++ }; mixer.clear() }
  fun isRunning() = running

  private fun runOutput(my: Int) {
    val out = try { openOutput(Sfx.RATE) } catch (_: Throwable) { null }
    if (out == null) { synchronized(this) { if (generation == my) running = false }; return }
    try {
      val buf = ShortArray(FRAMES); val mixBuf = FloatArray(FRAMES)
      while (pump(my, mixBuf, buf)) out.write(buf, FRAMES)
    } catch (_: Throwable) {
      synchronized(this) { if (generation == my) running = false }
    } finally { try { out.close() } catch (_: Throwable) {} }
  }
  /** One block: mix (only if generation [my] is still current) and convert with soft clipping. False = this thread should exit. */
  internal fun pump(my: Int, mixBuf: FloatArray, buf: ShortArray): Boolean {
    if (!mixer.render(mixBuf) { generation == my && running }) return false
    val vol = volume
    for (i in mixBuf.indices) buf[i] = (tanh(mixBuf[i] * vol) * 32000f).toInt().toShort()
    return true
  }
  fun play(data: FloatArray, gain: Float = 0.7f) {
    if (volume <= 0f) return
    BgMusic.duck()
    mixer.add(data, gain)
  }
  fun activeVoices() = mixer.active()
}

private fun audioTrackOut(rate: Int): PcmOut {
  val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
  val track = AudioTrack.Builder()
    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
    .setBufferSizeInBytes(max(min, 4096)).setTransferMode(AudioTrack.MODE_STREAM)
    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build()
  track.play()
  return object : PcmOut {
    override fun write(buf: ShortArray, frames: Int) { track.write(buf, 0, frames) }
    override fun close() { try { track.stop() } catch (_: Exception) {}; track.release() }
  }
}

/** What game models use to make sound and haptics. The app uses [GameSound]; tests record calls instead. */
interface SoundSink {
  fun play(data: FloatArray, gain: Float = 0.7f)
  fun animal(k: Critter, gain: Float = 1f, delayMs: Long = 0)
  fun haptic(strong: Boolean = false)
}
object GameSound : SoundSink {
  override fun play(data: FloatArray, gain: Float) = Audio.play(data, gain)
  override fun animal(k: Critter, gain: Float, delayMs: Long) = AnimalSounds.play(k, gain, delayMs)
  override fun haptic(strong: Boolean) = Fx.tap(strong)
}
