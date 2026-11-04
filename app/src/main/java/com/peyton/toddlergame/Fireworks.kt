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

/** The four burst patterns, used in turn so every few taps look different. Each gives unit-radius spark directions. */
internal enum class BurstShape { RING, PEONY, HEART, STAR }

internal class Rocket(val x: Float, val y0: Float, val ty: Float, val t0: Float, val color: Color, val shape: BurstShape)
internal class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Color, val t0: Float)
internal class Burst(val x: Float, val y: Float, val t0: Float, val color: Color, val shape: BurstShape)

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
  }
  val rockets = ArrayList<Rocket>(); val sparks = ArrayList<Spark>(); val bursts = ArrayList<Burst>()
  private val holds = HashMap<Long, Offset>(); private val lastFire = HashMap<Long, Float>()
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = 0f
  private var shapeIdx = 0; private var colorIdx = 0
  private var lastWhoosh = -1f; private var lastBoom = -1f

  private fun ok(p: Offset) = p.x.isFinite() && p.y.isFinite() && w >= 2f && h >= 2f

  fun down(id: Long, p: Offset) { if (!ok(p)) return; lastTouch = t; holds[id] = p; lastFire[id] = t; fire(p) }
  fun move(id: Long, p: Offset) { if (!ok(p) || id !in holds) return; lastTouch = t; holds[id] = p }
  fun up(id: Long) { holds.remove(id); lastFire.remove(id) }

  /** Launch now (sound + rocket in the same frame). Over [MAX_ROCKETS], the oldest bursts early instead of being dropped. */
  fun fire(p: Offset) {
    val x = p.x.coerceIn(0f, w); val ty = p.y.coerceIn(0f, h)
    while (rockets.size >= MAX_ROCKETS) burst(rockets.first())
    val shape = BurstShape.entries[shapeIdx++ % BurstShape.entries.size]
    val color = RAINBOW[colorIdx++ % RAINBOW.size]
    rockets += Rocket(x, h, ty, t, color, shape)
    if (t - lastWhoosh >= WHOOSH_GAP) { lastWhoosh = t; sink.play(Sfx.whoosh, 0.35f) }
    sink.haptic()
  }

  private fun burst(r: Rocket) {
    rockets.remove(r)
    val m = min(w, h); val dirs = shapeDirs(r.shape)
    val over = sparks.size + dirs.size - MAX_SPARKS
    if (over > 0) sparks.subList(0, min(over, sparks.size)).clear()   // a storm drops the oldest sparks, every new burst shows in full
    val white = Color.White
    for ((i, d) in dirs.withIndex()) {
      val v = m * SPEED * (0.92f + rnd.nextFloat() * 0.16f)
      sparks += Spark(r.x, r.ty, d.x * v, d.y * v, if (i % 5 == 0) white else r.color, t)
    }
    bursts += Burst(r.x, r.ty, t, r.color, r.shape)
    if (bursts.size > 12) bursts.removeAt(0)
    if (t - lastBoom >= BOOM_GAP) {
      lastBoom = t
      sink.play(Sfx.boom, 0.6f); sink.play(Sfx.crackle, 0.35f)
      sink.play(Sfx.bells[((r.x / w) * 9).toInt().coerceIn(0, 9)], 0.22f)
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
  }

  fun step(dtIn: Float) {
    if (w < 2f || h < 2f) return
    val dt = if (dtIn.isNaN()) 0f else dtIn.coerceIn(0f, 0.1f)
    for ((id, p) in holds) if (t - (lastFire[id] ?: t) >= HOLD_EVERY) { lastFire[id] = t; fire(p) }
    rockets.filter { t - it.t0 >= FLIGHT }.forEach { burst(it) }
    val m = min(w, h); val drag = exp(-DRAG * dt); val g = m * 0.12f
    for (s in sparks) { s.vx *= drag; s.vy = s.vy * drag + g * dt; s.x += s.vx * dt; s.y += s.vy * dt }
    sparks.removeAll { t - it.t0 > SPARK_LIFE }
    bursts.removeAll { t - it.t0 > 0.5f }
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
    for (b in m.bursts) { val k = ((t - b.t0) / 0.5f).coerceIn(0f, 1f); val c = Offset(b.x, b.y)
      drawCircle(Brush.radialGradient(listOf(b.color.copy(alpha = 0.45f * (1 - k)), Color.Transparent), c, mm * 0.25f), mm * 0.25f, c)
      drawCircle(Color.White.copy(alpha = 0.9f * (1 - k)), mm * 0.04f * (1 - k), c) }
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
