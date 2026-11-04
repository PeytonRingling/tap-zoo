package com.peyton.toddlergame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.*
import kotlin.random.Random

/** Burst patterns. The first four take turns; every [FireworksModel.FACE_EVERY]th rocket is a smiley or a cat face instead.
 *  Each gives unit-radius spark directions, and each has its own sound. */
internal enum class BurstShape { RING, PEONY, HEART, STAR, SMILEY, CAT }

/** [power] 0..[FireworksModel.MAX_POWER]: taps in quick succession on the same spot build bigger bursts. */
internal class Rocket(val x: Float, val y0: Float, val ty: Float, val t0: Float, val color: Color, val shape: BurstShape, val power: Int = 0)
internal class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Color, val t0: Float)
internal class Burst(val x: Float, val y: Float, val t0: Float, val color: Color, val shape: BurstShape, val power: Int = 0)
/** A slow glowing dot that wanders the sky; they glow brighter the longer nobody touches. */
internal class Firefly(var x: Float, var y: Float, var heading: Float, val phase: Float)

/** Fireworks for an 18-month-old: any touch anywhere sends a rocket up from the bottom under the finger, and it bursts big right
 *  where the finger is. Holding a finger keeps firing. Nothing to aim at, nothing to win or lose, no music (the booms are it). */
internal class FireworksModel(private val sink: SoundSink = GameSound, private val rnd: Random = Random.Default) {
  companion object {
    const val FLIGHT = 0.4f; const val HOLD_EVERY = 0.3f; const val MAX_ROCKETS = 6; const val MAX_SPARKS = 900
    const val SPARK_LIFE = 1.5f; const val DRAG = 3f
    /** Spark speed as a share of the short side; with [DRAG] they coast out to about 0.2 of it, a burst 40% of the screen across. */
    const val SPEED = 0.6f
    const val WHOOSH_GAP = 0.06f; const val BOOM_GAP = 0.08f
    const val IDLE_HINT = 7f
    /** A tap within [BUILD_GAP] s and [BUILD_NEAR] × short side of the last one builds a bigger burst, up to [MAX_POWER]. */
    const val BUILD_GAP = 0.7f; const val BUILD_NEAR = 0.15f; const val MAX_POWER = 3
    const val FACE_EVERY = 6; const val FIREFLIES = 12
    val ROTATION = listOf(BurstShape.RING, BurstShape.PEONY, BurstShape.HEART, BurstShape.STAR)
  }
  val fireflies = ArrayList<Firefly>()
  val rockets = ArrayList<Rocket>(); val sparks = ArrayList<Spark>(); val bursts = ArrayList<Burst>()
  private val holds = HashMap<Long, Offset>(); private val lastFire = HashMap<Long, Float>()
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = 0f
  private var shapeIdx = 0; private var colorIdx = 0
  private var lastWhoosh = -1f; private var lastBoom = -1f
  private var fired = 0; private var faces = 0
  private var lastFireAt = -10f; private var lastFireP = Offset.Zero; private var power = 0

  private fun ok(p: Offset) = p.x.isFinite() && p.y.isFinite() && w >= 2f && h >= 2f

  fun down(id: Long, p: Offset) { if (!ok(p)) return; lastTouch = t; holds[id] = p; lastFire[id] = t; fire(p) }
  fun move(id: Long, p: Offset) { if (!ok(p) || id !in holds) return; lastTouch = t; holds[id] = p }
  fun up(id: Long) { holds.remove(id); lastFire.remove(id) }

  /** Launch now (sound + rocket in the same frame). Over [MAX_ROCKETS], the oldest bursts early instead of being dropped. */
  fun fire(p: Offset) {
    val x = p.x.coerceIn(0f, w); val ty = p.y.coerceIn(0f, h)
    while (rockets.size >= MAX_ROCKETS) burst(rockets.first())
    val m = min(w, h)
    power = if (t - lastFireAt <= BUILD_GAP && hypot(x - lastFireP.x, ty - lastFireP.y) <= m * BUILD_NEAR) min(MAX_POWER, power + 1) else 0
    lastFireAt = t; lastFireP = Offset(x, ty)
    fired++
    val shape = if (fired % FACE_EVERY == 0) (if (faces++ % 2 == 0) BurstShape.SMILEY else BurstShape.CAT) else ROTATION[shapeIdx++ % ROTATION.size]
    val color = RAINBOW[colorIdx++ % RAINBOW.size]
    rockets += Rocket(x, h, ty, t, color, shape, power)
    if (t - lastWhoosh >= WHOOSH_GAP) { lastWhoosh = t; sink.play(Sfx.whoosh, 0.35f) }
    sink.haptic()
  }

  private fun burst(r: Rocket) {
    rockets.remove(r)
    val m = min(w, h); val base = shapeDirs(r.shape)
    // A built-up burst flies wider and gets an inner layer per level.
    val dirs = base + (1..r.power).flatMap { lv -> base.map { it * (1f - lv * 0.22f) } }
    val over = sparks.size + dirs.size - MAX_SPARKS
    if (over > 0) sparks.subList(0, min(over, sparks.size)).clear()   // a storm drops the oldest sparks, every new burst shows in full
    val white = Color.White
    for ((i, d) in dirs.withIndex()) {
      val face = if (r.shape == BurstShape.SMILEY || r.shape == BurstShape.CAT) 1.3f else 1f   // faces open wider so they read
      val v = m * SPEED * face * (1f + r.power * 0.3f) * (0.92f + rnd.nextFloat() * 0.16f)
      sparks += Spark(r.x, r.ty, d.x * v, d.y * v, if (i % 5 == 0) white else r.color, t)
    }
    bursts += Burst(r.x, r.ty, t, r.color, r.shape, r.power)
    if (bursts.size > 12) bursts.removeAt(0)
    if (t - lastBoom >= BOOM_GAP) {
      lastBoom = t
      val big = 1f + r.power * 0.12f
      when (r.shape) {
        BurstShape.HEART -> { sink.play(Sfx.boom, 0.3f * big); sink.play(Sfx.twinkle, 0.5f) }
        BurstShape.STAR -> { sink.play(Sfx.boom, 0.35f * big); sink.play(Sfx.crackle, 0.6f) }
        BurstShape.RING -> sink.play(Sfx.bigBoom, 0.7f * big)
        BurstShape.PEONY -> { sink.play(Sfx.boom, 0.6f * big); sink.play(Sfx.crackle, 0.35f) }
        BurstShape.SMILEY -> { sink.play(Sfx.boom, 0.5f); sink.play(Sfx.giggle, 0.5f) }
        BurstShape.CAT -> { sink.play(Sfx.boom, 0.5f); sink.animal(Critter.CAT, 0.7f, 150) }
      }
      if (r.shape != BurstShape.HEART) sink.play(Sfx.bells[((r.x / w) * 9).toInt().coerceIn(0, 9)], 0.22f)
    }
    sink.haptic(true)
  }

  /** Unit-radius directions for one burst. */
  internal fun shapeDirs(s: BurstShape): List<Offset> = when (s) {
    BurstShape.RING -> List(48) { val a = it * 2 * PI.toFloat() / 48; Offset(cos(a), sin(a)) }
    BurstShape.PEONY -> List(64) { val a = rnd.nextFloat() * 2 * PI.toFloat(); val k = sqrt(rnd.nextFloat()); Offset(cos(a) * k, sin(a) * k) }
    BurstShape.HEART -> List(56) { val a = it * 2 * PI.toFloat() / 56
      Offset(16 * sin(a).pow(3) / 17f, -(13 * cos(a) - 5 * cos(2 * a) - 2 * cos(3 * a) - cos(4 * a)) / 17f) }
    BurstShape.STAR -> List(60) { val seg = it / 6; val k = (it % 6) / 6f
      fun v(j: Int): Offset { val a = -PI.toFloat() / 2 + j * PI.toFloat() / 5; val rr = if (j % 2 == 0) 1f else 0.45f; return Offset(cos(a) * rr, sin(a) * rr) }
      val p = v(seg); val q = v(seg + 1); Offset(p.x + (q.x - p.x) * k, p.y + (q.y - p.y) * k) }
    BurstShape.SMILEY -> face()
    BurstShape.CAT -> face() + List(16) { val k = (it % 8) / 7f; val s = if (it < 8) -1f else 1f   // two pointy ears on top
      if (k < 0.5f) Offset(s * (0.5f + k * 0.2f), -0.78f - k * 0.34f) else Offset(s * (0.6f - (k - 0.5f) * 0.6f), -0.95f + (k - 0.5f) * 0.34f) }
  }
  /** Round face outline, two eyes and a smile, as spark directions. */
  private fun face(): List<Offset> = List(36) { val a = it * 2 * PI.toFloat() / 36; Offset(cos(a), sin(a)) } +
    listOf(-1f, 1f).flatMap { s -> List(4) { val a = it * PI.toFloat() / 2; Offset(s * 0.35f + cos(a) * 0.07f, -0.25f + sin(a) * 0.07f) } } +
    List(12) { val a = (0.15f + 0.7f * it / 11f) * PI.toFloat(); Offset(cos(a) * 0.55f, 0.05f + sin(a) * 0.45f) }

  /** 0..1: fireflies fade in once the sky has been quiet a moment, and dim while fingers are busy. */
  fun fireflyGlow() = ((t - lastTouch - 0.6f) / 1.5f).coerceIn(0.15f, 1f)

  fun step(dtIn: Float) {
    if (w < 2f || h < 2f) return
    val dt = if (dtIn.isNaN()) 0f else dtIn.coerceIn(0f, 0.1f)
    for ((id, p) in holds) if (t - (lastFire[id] ?: t) >= HOLD_EVERY) { lastFire[id] = t; fire(p) }
    rockets.filter { t - it.t0 >= FLIGHT }.forEach { burst(it) }
    val m = min(w, h); val drag = exp(-DRAG * dt); val g = m * 0.12f
    for (s in sparks) { s.vx *= drag; s.vy = s.vy * drag + g * dt; s.x += s.vx * dt; s.y += s.vy * dt }
    sparks.removeAll { t - it.t0 > SPARK_LIFE }
    bursts.removeAll { t - it.t0 > 0.5f }
    if (fireflies.isEmpty()) repeat(FIREFLIES) { fireflies += Firefly(rnd.nextFloat() * w, (0.15f + rnd.nextFloat() * 0.7f) * h, rnd.nextFloat() * 6.28f, rnd.nextFloat() * 6.28f) }
    for (f in fireflies) {
      f.heading += (rnd.nextFloat() - 0.5f) * 3f * dt
      f.x += cos(f.heading) * m * 0.04f * dt; f.y += sin(f.heading) * m * 0.03f * dt
      if (f.x < 0f || f.x > w) { f.heading = PI.toFloat() - f.heading; f.x = f.x.coerceIn(0f, w) }
      if (f.y < h * 0.1f || f.y > h * 0.9f) { f.heading = -f.heading; f.y = f.y.coerceIn(h * 0.1f, h * 0.9f) }
    }
  }

  fun rocketY(r: Rocket): Float { val k = ((t - r.t0) / FLIGHT).coerceIn(0f, 1f); return r.y0 + (r.ty - r.y0) * (1 - (1 - k) * (1 - k)) }
  fun holding() = holds.size
}

@Composable
fun FireworksScreen() { FireworksScreenFor(remember { FireworksModel() }) }

@Composable
internal fun FireworksScreenFor(m: FireworksModel) {
  val stars = remember { List(80) { Offset(Random.nextFloat(), Random.nextFloat() * 0.8f) } }
  val time = rememberTicker { dt, t -> m.t = t; m.step(dt) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }
    .multiTouch(Unit, onDown = { id, p -> m.down(id, p) }, onMove = { id, p -> m.move(id, p) }, onUp = { m.up(it) })) {
    val t = time.value; m.w = size.width; m.h = size.height; val mm = min(size.width, size.height)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF070B2A), Color(0xFF17206A), Color(0xFF3A2D7A))))
    stars.forEachIndexed { i, s -> drawStar(Offset(s.x * size.width, s.y * size.height), 2.5f + (i % 3) * 2f, Color.White.copy(alpha = 0.3f + 0.3f * sin(t * (1 + i % 3) + i))) }
    for (i in 0 until 5) drawCircle(Color(0xFF0C1238), size.width * 0.16f, Offset(size.width * (0.1f + i * 0.2f), size.height * 1.04f))
    val glow = m.fireflyGlow()
    for (f in m.fireflies) { val a = glow * (0.55f + 0.45f * sin(t * 2.2f + f.phase)); val c = Offset(f.x, f.y)
      drawCircle(Brush.radialGradient(listOf(Color(0xFFE8FF7A).copy(alpha = 0.5f * a), Color.Transparent), c, mm * 0.035f), mm * 0.035f, c)
      drawCircle(Color(0xFFF4FFB0).copy(alpha = a), mm * 0.008f, c) }
    for (b in m.bursts) { val k = ((t - b.t0) / 0.5f).coerceIn(0f, 1f); val c = Offset(b.x, b.y); val gr = mm * 0.25f * (1f + b.power * 0.3f)
      drawCircle(Brush.radialGradient(listOf(b.color.copy(alpha = 0.45f * (1 - k)), Color.Transparent), c, gr), gr, c)
      drawCircle(Color.White.copy(alpha = 0.9f * (1 - k)), mm * 0.04f * (1 + b.power * 0.4f) * (1 - k), c) }
    for (r in m.rockets) { val y = m.rocketY(r); val head = Offset(r.x, y)
      drawLine(Color(0xFFFFE680).copy(alpha = 0.5f), head, Offset(r.x, y + mm * 0.12f), mm * 0.012f, StrokeCap.Round)
      drawCircle(r.color.copy(alpha = 0.35f), mm * 0.03f, head); drawCircle(Color.White, mm * 0.014f, head) }
    val sr = mm * 0.011f
    for (s in m.sparks) { val a = (1f - (t - s.t0) / FireworksModel.SPARK_LIFE).coerceIn(0f, 1f); val o = Offset(s.x, s.y)
      drawLine(s.color.copy(alpha = 0.5f * a), o, Offset(s.x - s.vx * 0.05f, s.y - s.vy * 0.05f), sr * 1.2f, StrokeCap.Round)
      drawCircle(s.color.copy(alpha = a), sr * (0.6f + 0.4f * a), o) }
    if (t - m.lastTouch > FireworksModel.IDLE_HINT && m.holding() == 0) drawHand(Offset(size.width * 0.5f, size.height * 0.45f), mm * 0.05f, abs(sin(t * 3f)))
  }
}
