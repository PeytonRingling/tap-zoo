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

  val animal: Map<Critter, FloatArray> by lazy {
    mapOf(
      Critter.DOG to seq(tone(330.0, 240.0, 0.13, harm = 0.9, noise = 0.25, decay = 2.0), silence(0.06), tone(350.0, 250.0, 0.15, harm = 0.9, noise = 0.25, decay = 2.0)),
      Critter.CAT to seq(tone(450.0, 760.0, 0.2, vib = 15.0, harm = 0.5, decay = 0.3), tone(760.0, 470.0, 0.35, vib = 20.0, harm = 0.5, decay = 2.0)),
      Critter.PIG to oinkOink,
      Critter.COW to tone(190.0, 150.0, 0.9, vib = 8.0, vibRate = 4.0, harm = 1.0, decay = 1.2),
      Critter.FROG to seq(tone(180.0, 220.0, 0.09, harm = 1.0, vib = 60.0, vibRate = 45.0), silence(0.04), tone(200.0, 260.0, 0.14, harm = 1.0, vib = 60.0, vibRate = 45.0)),
      Critter.LION to tone(150.0, 115.0, 0.7, vib = 20.0, vibRate = 25.0, harm = 1.0, noise = 0.35, decay = 1.5),
      Critter.DUCK to seq(tone(520.0, 420.0, 0.16, harm = 1.0, noise = 0.2, vib = 40.0, vibRate = 50.0), silence(0.07), tone(500.0, 400.0, 0.18, harm = 1.0, noise = 0.2, vib = 40.0, vibRate = 50.0)),
      Critter.CHICK to seq(tone(1800.0, 2400.0, 0.07, decay = 1.0), silence(0.04), tone(1900.0, 2500.0, 0.07), silence(0.04), tone(2000.0, 2600.0, 0.08)),
      // Fallbacks only (before the recorded clip loads, or if it's missing): the recordings in res/raw are what normally plays.
      Critter.HORSE to tone(950.0, 520.0, 0.9, vib = 70.0, vibRate = 11.0, harm = 0.8, decay = 1.2, noise = 0.1, gain = 0.8),
      Critter.SHEEP to baa,
      Critter.ROOSTER to seq(tone(500.0, 650.0, 0.12, harm = 0.9, noise = 0.15), tone(650.0, 700.0, 0.12, harm = 0.9, noise = 0.15), tone(700.0, 750.0, 0.14, harm = 0.9, noise = 0.15), tone(800.0, 560.0, 0.4, vib = 20.0, vibRate = 12.0, harm = 0.9, noise = 0.15, decay = 1.5)),
      Critter.OWL to seq(tone(420.0, 390.0, 0.22, harm = 0.15, decay = 1.5), silence(0.12), tone(430.0, 370.0, 0.4, harm = 0.15, decay = 1.5)),
      Critter.DONKEY to seq(tone(950.0, 850.0, 0.28, harm = 0.9, noise = 0.2, decay = 0.8), tone(380.0, 320.0, 0.38, harm = 0.9, noise = 0.3, decay = 0.8), tone(950.0, 850.0, 0.28, harm = 0.9, noise = 0.2, decay = 0.8), tone(380.0, 300.0, 0.4, harm = 0.9, noise = 0.3, decay = 1.5)),
    )
  }
  /** Cartoon pig: two quick nasal "oink"s. A low buzzy pulse train (about 150 Hz gliding down to 105) plus breath noise, through
   *  a nasal formant bank, with a fast rough flutter (snort) and a squeezed start, so it grunts instead of whistling. */
  internal fun oink(f0: Double, f1: Double, secs: Double, seed: Long): FloatArray {
    val n = (RATE * secs).toInt(); val src = DoubleArray(n); val r = java.util.Random(seed); var ph = 0.0
    for (i in 0 until n) { val x = i.toDouble() / n; val f = f0 * (f1 / f0).pow(x); ph += f / RATE; if (ph >= 1) ph -= 1.0
      val pulse = if (ph < 0.12) sin(PI * ph / 0.12) else 0.0   // narrow glottal pulse: rich, buzzy harmonics
      src[i] = pulse * 0.9 + (r.nextDouble() * 2 - 1) * 0.35 }
    // nasal formants (Hz, bandwidth Hz, gain): a strong low "nn" hum, an "oi" vowel, and a little nasal edge up top
    val out = DoubleArray(n)
    for ((fc, bw, g) in listOf(Triple(320.0, 90.0, 1.0), Triple(900.0, 160.0, 0.75), Triple(2300.0, 260.0, 0.3))) {
      val rr = exp(-PI * bw / RATE); val c = 2 * rr * cos(2 * PI * fc / RATE); var y1 = 0.0; var y2 = 0.0
      for (i in 0 until n) { val y = (1 - rr) * src[i] + c * y1 - rr * rr * y2; y2 = y1; y1 = y; out[i] += y * g }
    }
    val peak = out.maxOf { abs(it) }.coerceAtLeast(1e-9)
    return FloatArray(n) { i -> val t = i.toDouble() / RATE; val x = i.toDouble() / n
      val env = min(1.0, t / 0.012) * (if (x > 0.7) (1 - x) / 0.3 else 1.0) * (0.75 + 0.25 * sin(PI * min(1.0, x * 2)))
      val rough = 0.62 + 0.38 * sin(2 * PI * 32 * t)   // the snorty flutter
      (out[i] / peak * env * rough).toFloat() }
  }
  /** Sample RMS that makes a synth voice (played at 0.7 × gain) about as loud as the animal clips (about -18 dBFS RMS). */
  const val ANIMAL_RMS = 0.18
  /** "Oink-oink!", loudness-matched to the other animals' clips, peak kept under full scale. */
  val oinkOink: FloatArray by lazy {
    val raw = seq(oink(155.0, 112.0, 0.17, 3), silence(0.07), oink(165.0, 105.0, 0.21, 4))
    val k = (ANIMAL_RMS / rms(raw)).coerceAtMost(0.98 / raw.maxOf { abs(it) })
    FloatArray(raw.size) { (raw[it] * k).toFloat() }
  }
  internal fun rms(a: FloatArray) = sqrt(a.sumOf { it.toDouble() * it } / a.size.coerceAtLeast(1))

  val peekaboo by lazy { seq(tone(1046.5, 1046.5, 0.14, decay = 1.5), tone(1318.5, 1318.5, 0.14, decay = 1.5), tone(784.0, 1568.0, 0.45, vib = 25.0, decay = 1.2)) }
  val giggle by lazy { seq(*(0 until 5).flatMap { listOf(tone(700.0 + it * 80, 900.0 + it * 80, 0.07, decay = 2.0), silence(0.03)) }.toTypedArray()) }
  val chime by lazy { seq(bell(783.99, 0.18), bell(1046.5, 0.5)) }
  val boom by lazy { mix(tone(90.0, 40.0, 0.8, noise = 0.85, decay = 4.0), tone(2000.0, 400.0, 0.25, noise = 0.5, decay = 6.0, gain = 0.4)) }
  val balloonPop by lazy { mix(tone(2600.0, 2600.0, 0.035, noise = 1.0, decay = 8.0), tone(170.0, 60.0, 0.14, harm = 0.2, decay = 5.0, gain = 0.8)) }
  val wheee by lazy { tone(500.0, 1400.0, 0.6, vib = 20.0, vibRate = 7.0, decay = 1.0, gain = 0.6) }
  val blub by lazy { seq(tone(300.0, 700.0, 0.06, harm = 0.0, decay = 2.0), tone(420.0, 950.0, 0.07, harm = 0.0, decay = 2.0)) }
  /** Aquarium: each fish blubs at its own pitch (low big-fish blub up to a tiny high one). */
  val blubs: List<FloatArray> by lazy { listOf(0.75, 0.88, 1.0, 1.15, 1.32).map { k -> seq(tone(300.0 * k, 700.0 * k, 0.06, harm = 0.0, decay = 2.0), tone(420.0 * k, 950.0 * k, 0.07, harm = 0.0, decay = 2.0)) } }
  val inflate by lazy { tone(260.0, 820.0, 0.2, harm = 0.25, decay = 3.0, gain = 0.45) }
  val bubblePop by lazy { tone(900.0, 1600.0, 0.06, harm = 0.0, decay = 6.0, gain = 0.5) }
  val plip by lazy { tone(1200.0, 1800.0, 0.05, harm = 0.0, decay = 4.0, gain = 0.6) }
  val snap by lazy { seq(tone(2500.0, 2500.0, 0.025, noise = 0.9), silence(0.05), tone(2700.0, 2700.0, 0.025, noise = 0.9)) }
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

  private fun midi(n: Int) = 440.0 * Math.pow(2.0, (n - 69) / 12.0)
  private fun xyloAt(f: Double) = mix(tone(f, f, 0.5, harm = 0.05, decay = 9.0), tone(f * 4, f * 4, 0.3, harm = 0.0, decay = 16.0, gain = 0.2))
  /** Balloons' combo ladder: G-major pentatonic (G A B D E), the key of "Monkeys Spinning Monkeys", G4 up to E6. */
  val BALLOON_KEY = intArrayOf(67, 69, 71, 74, 76, 79, 81, 83, 86, 88)
  val comboNotes: List<FloatArray> by lazy { BALLOON_KEY.map { xyloAt(midi(it)) } }
  /** Firework crackle: a scatter of tiny noise ticks that thins out, like sparks fizzling after the boom. */
  val crackle by lazy {
    val r = java.util.Random(11); val n = (RATE * 0.9).toInt(); val out = FloatArray(n); var i = (RATE * 0.05).toInt()
    while (i < n) {
      val len = (RATE * 0.003).toInt(); val amp = 0.55 * (1 - i.toDouble() / n)
      for (k in 0 until min(len, n - i)) out[i + k] = ((r.nextDouble() * 2 - 1) * amp * (1 - k.toDouble() / len)).toFloat()
      i += (RATE * (0.008 + r.nextDouble() * 0.03 * (1 + 2.0 * i / n))).toInt()
    }
    out
  }
  /** Monster chomp: two quick crunchy bites. */
  val chomp by lazy { seq(mix(tone(180.0, 90.0, 0.09, noise = 0.55, decay = 5.0), tone(1800.0, 900.0, 0.05, noise = 0.8, decay = 6.0, gain = 0.4)), silence(0.05),
    mix(tone(200.0, 100.0, 0.08, noise = 0.55, decay = 5.0), tone(2000.0, 1000.0, 0.045, noise = 0.8, decay = 6.0, gain = 0.35))) }
  /** Friendly cartoon burp: a low wobbly croak. */
  val burp by lazy { tone(120.0, 85.0, 0.45, vib = 18.0, vibRate = 22.0, harm = 0.8, decay = 2.0, noise = 0.15, gain = 0.7) }
  /** Squeaky scrub-a-dub: a quick rubbery squeak with a soapy hiss. */
  val scrub by lazy { mix(tone(950.0, 1400.0, 0.12, vib = 90.0, vibRate = 38.0, harm = 0.3, decay = 2.5, gain = 0.6), tone(4000.0, 3000.0, 0.1, noise = 0.95, decay = 4.0, gain = 0.25)) }
  /** Shower: a soft hiss with a few drips on top. */
  val shower by lazy { mix(tone(3000.0, 2500.0, 1.2, noise = 0.97, decay = 1.5, gain = 0.45),
    seq(silence(0.1), plip, silence(0.18), plip, silence(0.25), plip, silence(0.2), plip)) }
  /** Heart burst: three quick high bells, a sparkly twinkle. */
  val twinkle by lazy { mix(bell(1318.5, 0.5), mix(seq(silence(0.07), bell(1568.0, 0.45)), seq(silence(0.14), bell(2093.0, 0.4)))).let { a -> FloatArray(a.size) { a[it] * 0.4f } } }
  /** Ring burst: a deeper, longer boom with a rumble. */
  val bigBoom by lazy { mix(tone(90.0, 40.0, 0.9, noise = 0.55, decay = 2.2, gain = 0.75), tone(180.0, 80.0, 0.3, noise = 0.3, decay = 4.0, gain = 0.35)) }
  /** Sour lemon face: a wobbly "ooo-ee" slide down. */
  val pucker by lazy { tone(950.0, 480.0, 0.38, vib = 45.0, vibRate = 18.0, harm = 0.2, decay = 2.0, gain = 0.5) }
  /** Carrot crunch: three crisp noisy bites. */
  val crunch by lazy { seq(*(0 until 3).flatMap { listOf(tone(320.0 - it * 40, 180.0, 0.055, noise = 0.95, decay = 7.0, gain = 0.75), silence(0.045)) }.toTypedArray()) }
  /** Shake-off splash: a wet noisy burst with drips. */
  val splash by lazy { mix(tone(600.0, 200.0, 0.35, noise = 0.9, decay = 3.0, gain = 0.6), seq(silence(0.12), plip, silence(0.08), plip, silence(0.1), plip)) }
  /** Blow-dryer: a soft airy hum that fades. */
  val blowDry by lazy { mix(tone(180.0, 200.0, 1.5, vib = 6.0, vibRate = 30.0, harm = 0.5, decay = 0.8, gain = 0.25), tone(2500.0, 2500.0, 1.5, noise = 0.97, decay = 0.9, gain = 0.3)) }
  /** Elephant trumpet: a brassy rising toot with wobble. */
  val trumpet by lazy { tone(330.0, 620.0, 0.55, vib = 25.0, vibRate = 9.0, harm = 0.95, decay = 1.6, noise = 0.08, gain = 0.6) }
  /** Sheep baa: a bleaty wobble. */
  /** Pond plops: a rising water "bloop" over a little splash hiss, three pitches so taps don't sound identical. */
  val plops by lazy { listOf(260.0, 330.0, 400.0).map { f -> mix(tone(f, f * 2.6, 0.11, harm = 0.0, decay = 4.0, gain = 0.75),
    tone(2600.0, 1600.0, 0.14, noise = 0.95, decay = 5.0, gain = 0.22)) } }
  /** Pond wake: a soft swishy hiss while a finger drags through the water. */
  val swish by lazy { tone(1400.0, 900.0, 0.22, noise = 0.97, decay = 3.5, gain = 0.22) }
  val baa by lazy { seq(tone(420.0, 380.0, 0.5, vib = 30.0, vibRate = 24.0, harm = 0.85, decay = 1.8, noise = 0.05, gain = 0.6)) }
  /** Soft rubbery squeak: an idle balloon wiggling for attention. */
  val squeak by lazy { tone(700.0, 1150.0, 0.16, vib = 60.0, vibRate = 28.0, harm = 0.4, decay = 2.0, gain = 0.5) }

  /** Synthesizes every lazy sound up front (call off the main thread at startup) so the first tap in a mode doesn't stall
   *  the UI while its sounds are generated. `by lazy` is synchronized, so racing a tap is safe. Returns them for tests. */
  fun prewarm(): List<FloatArray> = animal.values + bells + xylo + honks +
    listOf(peekaboo, giggle, chime, boom, balloonPop, wheee, blub, inflate, bubblePop, plip, snap, zoom, kick, tom, snare, cymbal, march, whoosh) +
    comboNotes + blubs + listOf(squeak, crackle, chomp, burp, scrub, shower, twinkle, bigBoom, pucker, crunch, splash, blowDry, trumpet, baa, swish) + plops
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
