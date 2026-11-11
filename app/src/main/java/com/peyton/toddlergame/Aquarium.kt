package com.peyton.toddlergame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.*
import kotlin.random.Random

/** Cute fish facing right; [wag] tail angle, [mouth] 0..1 */
fun DrawScope.drawFish(c: Offset, s: Float, color: Color, stripe: Color, wag: Float, mouth: Float = 0f) {
  val tail = Path().apply { moveTo(c.x - s * 0.75f, c.y); lineTo(c.x - s * 1.35f, c.y - s * 0.5f); quadraticBezierTo(c.x - s * 1.15f, c.y, c.x - s * 1.35f, c.y + s * 0.5f); close() }
  rotate(wag, Offset(c.x - s * 0.75f, c.y)) { drawPath(tail, Brush.horizontalGradient(listOf(color.darken(0.1f), color.lighten(0.2f)), c.x - s * 1.35f, c.x - s * 0.75f)) }
  drawPath(Path().apply { moveTo(c.x - s * 0.3f, c.y - s * 0.5f); quadraticBezierTo(c.x, c.y - s * 1.0f, c.x + s * 0.35f, c.y - s * 0.52f); close() }, color.darken(0.08f))
  softShadow(Rect(c.x - s * 0.85f, c.y - s * 0.4f, c.x + s * 0.85f, c.y + s * 0.75f), s * 0.6f, s * 0.25f, Pal.shadow.copy(alpha = 0.15f))
  val body = Rect(c.x - s * 0.9f, c.y - s * 0.62f, c.x + s * 0.95f, c.y + s * 0.62f)
  drawOval(Brush.radialGradient(listOf(color.lighten(0.45f), color, color.darken(0.2f)), c + Offset(-s * 0.1f, -s * 0.35f), s * 1.4f), body.topLeft, body.size)
  clipPath(Path().apply { addOval(body) }) { for (i in 0..1) drawRect(stripe.copy(alpha = 0.85f), Offset(c.x - s * (0.35f - i * 0.5f), body.top), Size(s * 0.18f, body.height)) }
  drawOval(Color.White.copy(alpha = 0.35f), Offset(c.x - s * 0.45f, c.y - s * 0.5f), Size(s * 0.8f, s * 0.25f))
  rotate(-20f + wag * 0.5f, c + Offset(-s * 0.05f, s * 0.15f)) { drawOval(color.darken(0.15f).copy(alpha = 0.8f), c + Offset(-s * 0.25f, s * 0.1f), Size(s * 0.4f, s * 0.22f)) }
  val e = c + Offset(s * 0.48f, -s * 0.12f)
  drawCircle(Color.White, s * 0.2f, e); drawCircle(Pal.ink, s * 0.12f, e + Offset(s * 0.04f, 0f)); drawCircle(Color.White, s * 0.045f, e + Offset(0f, -s * 0.05f))
  drawCircle(Color(0xFFFF8FA3).copy(alpha = 0.5f), s * 0.09f, c + Offset(s * 0.5f, s * 0.2f))
  if (mouth > 0.1f) drawOval(Color(0xFF8B1E3F), c + Offset(s * 0.78f, s * 0.12f), Size(s * 0.14f, s * 0.18f * mouth))
  else drawArc(Pal.ink, 0f, 120f, false, c + Offset(s * 0.62f, s * 0.05f), Size(s * 0.24f, s * 0.18f), style = Stroke(s * 0.05f, cap = StrokeCap.Round))
}

/** One fish. Motion is velocity-based: behaviours only change the velocity it *wants* ([AquaModel.desired]); the real velocity
 *  eases toward that, so nothing ever jumps. [face] eases between -1 (left) and 1 (right) and the sprite is squeezed through it,
 *  so a turn looks like the fish swinging round instead of a mirror snap. */
internal class Fish(var x: Float, var y: Float, val s: Float, val color: Color, val stripe: Color, val phase: Float, val voice: Int, var heading: Float) {
  var vx = 0f; var vy = 0f; var face = heading; var depth = y; var nextWander = 0f
  var fear = 0f; var fleeX = 0f; var fleeY = 0f; var tapT = -10f; var eatT = -10f; var happyT = -10f
  var seek: Crumb? = null; var nextBlow = 0f
  val speed get() = hypot(vx, vy)
}
internal class AquaBub(var x: Float, var y: Float, val r: Float, val t0: Float)
/** Sinking fish food; [treat] = the golden star cookie that comes with every [AquaModel.TREAT_EVERY]th feeding. */
internal class Crumb(var x: Float, var y: Float, val t0: Float, val treat: Boolean = false, val sway: Float = 0f)

/** Aquarium for an 18-month-old: calm fish that glide and turn smoothly. Tap a fish: it blub-s in its own voice, darts away from
 *  the finger and blows bubbles. Tap the water: food sprinkles down and the nearest fish swim over to eat it; every 5th sprinkle
 *  is a golden treat and the fish that gets it does a happy wiggle. Tap a bubble to pop it, tap the crab and it hops. */
internal class AquaModel(private val sink: SoundSink = GameSound, private val rnd: Random = Random.Default) {
  companion object {
    /** Cruise speed (share of width per second), dart speed when scared, swim-to-food speed. */
    const val CRUISE = 0.045f; const val DART = 0.32f; const val SEEK = 0.16f
    /** How fast the velocity eases to the wanted one (1/s): calm cruising vs a dart. Turning eases at [TURN]. */
    const val STEER = 1.6f; const val STEER_DART = 5f; const val TURN = 5f
    const val FEAR_DECAY = 0.8f; const val TREAT_EVERY = 5; const val MAX_CRUMBS = 24; const val MAX_BUBS = 40; const val EAT_GAP = 0.25f
    const val TOP = 0.14f; const val BOTTOM = 0.74f
  }
  val fish = ArrayList<Fish>(); val bubs = ArrayList<AquaBub>(); val crumbs = ArrayList<Crumb>()
  val parts = Particles().apply { gravity = -200f }
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = 0f; var crabX = 0.3f; var crabDir = 1f; var crabHop = -10f
  var feeds = 0; private set
  var eaten = 0; private set
  private var lastEatSound = -10f; private var lastBub = 0f

  private fun ok(p: Offset) = p.x.isFinite() && p.y.isFinite() && w >= 2f && h >= 2f
  val m get() = min(w, h)
  fun init() {
    val n = (w * h / (m * m * 0.18f)).toInt().coerceIn(5, 9)
    repeat(n) { i -> val c = Pal.vivid[(i * 2) % Pal.vivid.size]
      val f = Fish(rnd.nextFloat() * w, h * (0.18f + rnd.nextFloat() * 0.5f), m * (0.07f + rnd.nextFloat() * 0.05f), c,
        if (i % 2 == 0) Color.White.copy(alpha = 0.9f) else c.darken(0.25f), rnd.nextFloat() * 6f, i % 5, if (rnd.nextBoolean()) 1f else -1f)
      f.vx = f.heading * w * CRUISE; f.nextWander = 2f + rnd.nextFloat() * 5f; f.nextBlow = 2f + rnd.nextFloat() * 6f
      fish += f }
  }

  /** The velocity [f] wants right now. */
  internal fun desired(f: Fish): Offset {
    val cruise = w * CRUISE * (0.8f + (f.phase / 6f) * 0.4f)
    val home = Offset(f.heading * cruise, ((f.depth - f.y) * 0.5f).coerceIn(-h * 0.05f, h * 0.05f))
    val food = f.seek?.takeIf { it in crumbs }
    var want = if (food != null) { val d = Offset(food.x - f.x, food.y - f.y); val len = d.getDistance().coerceAtLeast(1f)
      d * (min(w * SEEK, len * 2f) / len) } else home   // slows down as it arrives, no overshoot
    if (f.fear > 0f) { val d = Offset(f.x - f.fleeX, f.y - f.fleeY); val len = d.getDistance().coerceAtLeast(1f)
      want = want * (1 - f.fear) + d * (w * DART * f.fear / len) }
    return want
  }

  fun step(dtIn: Float) {
    if (w < 2f || h < 2f) return
    val dt = if (dtIn.isNaN()) 0f else dtIn.coerceIn(0f, 0.05f)
    if (fish.isEmpty()) init()
    for (f in fish) {
      if (t >= f.nextWander) { f.nextWander = t + 4f + rnd.nextFloat() * 5f; f.depth = h * (TOP + 0.04f + rnd.nextFloat() * (BOTTOM - TOP - 0.08f))
        if (rnd.nextFloat() < 0.3f) f.heading = -f.heading }
      // turn round gently past the glass: the heading flips, the velocity eases round
      if (f.x < f.s * 0.5f && f.heading < 0) f.heading = 1f else if (f.x > w - f.s * 0.5f && f.heading > 0) f.heading = -1f
      if (f.seek != null && f.seek !in crumbs) f.seek = null
      val want = desired(f); val k = 1f - exp(-(if (f.fear > 0.05f) STEER_DART else STEER) * dt)
      f.vx += (want.x - f.vx) * k; f.vy += (want.y - f.vy) * k
      f.x += f.vx * dt; f.y += f.vy * dt
      f.x = f.x.coerceIn(-f.s, w + f.s); f.y = f.y.coerceIn(h * (TOP - 0.06f), h * (BOTTOM + 0.08f))
      f.fear = max(0f, f.fear - dt * FEAR_DECAY)
      if (abs(f.vx) > w * 0.004f) f.face += (sign(f.vx) - f.face) * (1f - exp(-TURN * dt))
      if (t >= f.nextBlow) { f.nextBlow = t + 4f + rnd.nextFloat() * 6f; blow(f, 1) }
      // eat what's under the mouth
      crumbs.firstOrNull { hypot(mouth(f).x - it.x, mouth(f).y - it.y) < f.s * 0.6f }?.let { eat(f, it) }
    }
    if (t - lastBub > 0.4f) { lastBub = t; if (rnd.nextFloat() < 0.8f && bubs.size < MAX_BUBS) bubs += AquaBub(w * (0.1f + rnd.nextFloat() * 0.8f), h * 0.9f, m * (0.006f + rnd.nextFloat() * 0.012f), t) }
    bubs.forEach { it.y -= h * 0.12f * dt; it.x += sin(t * 3f + it.r) * m * 0.01f * dt }; bubs.removeAll { it.y < -20f }
    val floor = h * 0.88f
    crumbs.forEach { if (it.y < floor) { it.y = min(floor, it.y + h * 0.05f * dt); it.x += sin(t * 2f + it.sway) * m * 0.01f * dt } }
    crumbs.removeAll { t - it.t0 > 8f }
    crabX += crabDir * dt * 0.04f; if (crabX > 0.85f) crabDir = -1f else if (crabX < 0.1f) crabDir = 1f
    parts.step(dt)
  }

  fun mouth(f: Fish) = Offset(f.x + f.face * f.s * 0.85f, f.y + f.s * 0.12f)
  private fun blow(f: Fish, n: Int) { repeat(n) { if (bubs.size < MAX_BUBS) bubs += AquaBub(mouth(f).x + it * f.s * 0.1f, mouth(f).y - it * f.s * 0.25f, f.s * (0.1f + rnd.nextFloat() * 0.06f), t) } }
  private fun eat(f: Fish, c: Crumb) {
    crumbs.remove(c); f.eatT = t; eaten++
    if (t - lastEatSound >= EAT_GAP) { lastEatSound = t; sink.play(Sfx.plip, 0.35f) }
    if (c.treat) { f.happyT = t; sink.play(Sfx.chime, 0.45f); sink.haptic(true)
      parts.burst(f.x, f.y - f.s * 0.5f, 16, listOf(Pal.sun, Color.White, Pal.pink), kind = 1, speed = m * 0.6f, size = f.s * 0.18f, up = m * 0.2f, decay = 1.1f) }
    else parts.burst(f.x, f.y - f.s * 0.6f, 1, listOf(Pal.pink), kind = 2, speed = 40f, size = f.s * 0.2f, up = m * 0.15f, decay = 1.2f)
    if (f.seek === c) f.seek = crumbs.minByOrNull { hypot(it.x - f.x, it.y - f.y) }?.takeIf { hypot(it.x - f.x, it.y - f.y) < m * 0.5f }   // next bite nearby
  }

  fun tap(p: Offset) {
    if (!ok(p)) return
    lastTouch = t
    val crab = Offset(crabX * w, h * 0.9f)
    if (hypot(p.x - crab.x, p.y - crab.y) < m * 0.09f) { crabHop = t; crabDir = -crabDir; sink.play(Sfx.snap); sink.haptic(); parts.burst(crab.x, crab.y - m * 0.05f, 8, listOf(Color.White), speed = 300f, size = 8f); return }
    val f = fish.filter { hypot(it.x - p.x, it.y - p.y) < it.s * 1.5f }.minByOrNull { hypot(it.x - p.x, it.y - p.y) }
    if (f != null) { f.fear = 1f; f.fleeX = p.x; f.fleeY = p.y; f.tapT = t; f.seek = null
      sink.play(Sfx.blubs[f.voice], 0.7f); sink.haptic(); blow(f, 3); return }
    val b = bubs.filter { it.r >= m * 0.012f && hypot(it.x - p.x, it.y - p.y) < max(it.r * 2.5f, m * 0.04f) }.minByOrNull { hypot(it.x - p.x, it.y - p.y) }
    if (b != null) { bubs.remove(b); sink.play(Sfx.bubblePop, 0.5f); sink.haptic(); parts.burst(b.x, b.y, 8, listOf(Color.White), speed = 200f, size = b.r * 0.4f, up = 50f); return }
    feeds++; val treat = feeds % TREAT_EVERY == 0
    val drop = if (treat) listOf(Crumb(p.x, p.y, t, true, rnd.nextFloat() * 6f)) else List(3) { Crumb(p.x + (rnd.nextFloat() - 0.5f) * m * 0.06f, p.y + (rnd.nextFloat() - 0.5f) * m * 0.03f, t, false, rnd.nextFloat() * 6f) }
    crumbs += drop; while (crumbs.size > MAX_CRUMBS) crumbs.removeAt(0)
    sink.play(if (treat) Sfx.twinkle else Sfx.plip, if (treat) 0.5f else 0.45f); sink.haptic()
    fish.sortedBy { hypot(it.x - p.x, it.y - p.y) }.take(if (treat) 3 else 2).forEach { it.seek = drop.minByOrNull { c -> hypot(c.x - it.x, c.y - it.y) } }
  }
}

@Composable
fun AquariumScreen() {
  AquariumScreenFor(remember { AquaModel() })
}

@Composable
internal fun AquariumScreenFor(m: AquaModel) {
  val time = rememberTicker { dt, t -> m.t = t; m.step(dt) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }.multiTouch(Unit, onDown = { _, p -> m.tap(p) })) {
    val t = time.value; val mm = min(size.width, size.height)
    meshBg(Color(0xFF7FDBFF), Color(0xFF2F6BD8), listOf(Color(0xFFB6F3FF) to Offset(0.2f, 0.1f), Color(0xFF6C5CE7) to Offset(0.9f, 0.9f)), t)
    for (i in 0 until 5) { val x = size.width * (0.1f + i * 0.22f) + sin(t * 0.4f + i) * mm * 0.03f
      drawPath(Path().apply { moveTo(x - mm * 0.03f, 0f); lineTo(x + mm * 0.04f, 0f); lineTo(x + mm * 0.2f - size.height * 0.2f, size.height); lineTo(x - mm * 0.05f - size.height * 0.2f, size.height); close() },
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0f)))) }
    val sandY = size.height * 0.86f
    drawPath(Path().apply { moveTo(0f, size.height); lineTo(0f, sandY); quadraticBezierTo(size.width * 0.3f, sandY - mm * 0.05f, size.width * 0.6f, sandY); quadraticBezierTo(size.width * 0.85f, sandY + mm * 0.04f, size.width, sandY - mm * 0.02f); lineTo(size.width, size.height); close() },
      Brush.verticalGradient(listOf(Color(0xFFFFE4B5), Color(0xFFF3C98B)), sandY - mm * 0.05f, size.height))
    for (i in 0 until 7) { val x = size.width * (0.05f + i * 0.15f); val hh = mm * (0.18f + (i % 3) * 0.08f)
      val col = if (i % 2 == 0) Color(0xFF2EC4B6) else Color(0xFF52D273)
      val p = Path(); p.moveTo(x, sandY + mm * 0.03f)
      for (k in 1..4) { val yy = sandY - hh * k / 4; p.quadraticBezierTo(x + sin(t * 1.5f + i + k) * mm * 0.035f * k, yy + hh / 8, x + sin(t * 1.5f + i + k + 1) * mm * 0.02f * k, yy) }
      drawPath(p, col, style = Stroke(mm * 0.025f, cap = StrokeCap.Round)) }
    listOf(0.2f to Pal.pink, 0.55f to Pal.sun, 0.75f to Pal.grape).forEach { (fx, c) -> clayCircle(Offset(size.width * fx, sandY + mm * 0.04f), mm * 0.03f, c.lighten(0.3f)) }
    for (b in m.bubs) { val a = ((t - b.t0) / 0.3f).coerceIn(0f, 1f)
      drawCircle(Color.White.copy(alpha = 0.35f * a), b.r, Offset(b.x, b.y)); drawCircle(Color.White.copy(alpha = 0.8f * a), b.r * 0.3f, Offset(b.x - b.r * 0.3f, b.y - b.r * 0.3f)) }
    for (c in m.crumbs) { val a = (1f - (t - c.t0 - 7f)).coerceIn(0f, 1f)
      if (c.treat) drawStar(Offset(c.x, c.y), mm * 0.028f, Pal.sun.copy(alpha = a), t * 40f) else drawCircle(Color(0xFFFFB86B).copy(alpha = a), mm * 0.008f, Offset(c.x, c.y)) }
    for (f in m.fish) {
      val bob = sin(t * 1.4f + f.phase) * f.s * 0.12f; val c = Offset(f.x, f.y + bob)
      val sp = (f.speed / (size.width * AquaModel.CRUISE)).coerceIn(0.5f, 4f)   // tail beats faster when it swims faster
      val happy = (t - f.happyT).let { if (it < 1.2f) sin(it * 18f) * 12f * (1 - it / 1.2f) else 0f }
      val wag = sin(t * (4f + sp * 3f) + f.phase) * (12f + sp * 5f)
      val tilt = (atan2(f.vy, abs(f.vx) + 1f) * 180f / PI.toFloat()).coerceIn(-20f, 20f) * sign(f.face)
      val sx = if (abs(f.face) < 0.15f) 0.15f * (if (f.face < 0) -1f else 1f) else f.face   // squeezes through the turn
      rotate(tilt + happy, c) { scale(sx, 1f, c) { drawFish(c, f.s, f.color, f.stripe, wag, if (t - f.tapT < 0.6f || t - f.eatT < 0.3f) 1f else 0f) } } }
    val crab = Offset(m.crabX * size.width, size.height * 0.9f); val hop = (t - m.crabHop).let { if (it < 0.5f) -sin(it / 0.5f * PI.toFloat()) * mm * 0.06f else 0f }
    val cc = crab + Offset(0f, hop); val cs = mm * 0.05f
    for (sd in listOf(-1f, 1f)) { for (l in 0..2) drawLine(Color(0xFFE8504F), cc + Offset(sd * cs * 0.6f, cs * 0.2f), cc + Offset(sd * cs * (1.1f + l * 0.12f), cs * (0.5f + l * 0.2f) + sin(t * 10f + l) * 4f), cs * 0.12f, StrokeCap.Round)
      val claw = cc + Offset(sd * cs * 1.2f, -cs * 0.6f + sin(t * 4f) * cs * 0.1f); drawLine(Color(0xFFE8504F), cc + Offset(sd * cs * 0.7f, -cs * 0.1f), claw, cs * 0.16f, StrokeCap.Round); clayCircle(claw, cs * 0.3f, Color(0xFFFF6B6B), false) }
    drawOval(Brush.radialGradient(listOf(Color(0xFFFF9A8B), Color(0xFFE8504F)), cc - Offset(cs * 0.3f, cs * 0.4f), cs * 1.4f), cc - Offset(cs, cs * 0.6f), Size(cs * 2, cs * 1.2f))
    for (sd in listOf(-1f, 1f)) { drawLine(Color(0xFFE8504F), cc + Offset(sd * cs * 0.3f, -cs * 0.5f), cc + Offset(sd * cs * 0.35f, -cs * 0.95f), cs * 0.08f); drawCircle(Color.White, cs * 0.17f, cc + Offset(sd * cs * 0.35f, -cs * 1.0f)); drawCircle(Pal.ink, cs * 0.09f, cc + Offset(sd * cs * 0.35f, -cs * 1.0f)) }
    drawArc(Pal.ink, 20f, 140f, false, cc + Offset(-cs * 0.25f, -cs * 0.25f), Size(cs * 0.5f, cs * 0.3f), style = Stroke(cs * 0.07f, cap = StrokeCap.Round))
    m.parts.draw(this)
    if (t - m.lastTouch > 7f) m.fish.firstOrNull { it.x in size.width * 0.2f..size.width * 0.8f }?.let { drawHand(Offset(it.x, it.y), mm * 0.045f, abs(sin(t * 3f))) }
  }
}
