package com.peyton.toddlergame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.*
import kotlin.random.Random

/** One mud splat on the animal, at [dx],[dy] from its centre (in animal radii). [dirt] 3 = fresh, 0 = clean. */
internal class Splat(val dx: Float, val dy: Float, val r: Float, val seed: Int) { var dirt = WashModel.RUBS }
internal class Foam(var x: Float, var y: Float, val r: Float, val t0: Float, val vx: Float)
internal enum class WashPhase { DIRTY, CLEAN, ENTERING }

/** Wash the animal, for an 18-month-old: one big muddy animal; rubbing or tapping scrubs the mud off with foam. When it's mostly
 *  clean it gets a shower, shakes and giggles, then the next muddy friend walks in. Every touch makes foam and a sound. No fail. */
internal class WashModel(private val sink: SoundSink = GameSound, private val rnd: Random = Random.Default) {
  companion object {
    const val SPLATS = 12; const val RUBS = 3; const val CLEAN_AT = 0.2f; const val CELEBRATE = 2.2f; const val ENTER = 0.8f
    const val STALL = 10f; const val MAX_FOAM = 80; const val FOAM_LIFE = 1.6f; const val SCRUB_GAP = 0.09f; const val IDLE_HINT = 7f
    val ORDER = listOf(Critter.DOG, Critter.PIG, Critter.COW, Critter.CAT, Critter.LION, Critter.DUCK, Critter.FROG, Critter.CHICK)
  }
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = -100f
  var critter = ORDER[rnd.nextInt(ORDER.size)]; private set
  val splats = ArrayList<Splat>(); val foam = ArrayList<Foam>()
  val parts = Particles(300).apply { gravity = 1400f }
  var phase = WashPhase.DIRTY; private set
  var phaseAt = 0f; private set
  var animalStart = 0f; private set
  var washed = 0; private set
  var rubAt = -10f; private set
  private val lastRub = HashMap<Long, Offset>()
  private var lastScrub = -10f

  init { mud() }
  val m get() = min(w, h)
  val radius get() = m * 0.3f
  val center: Offset get() {
    val home = Offset(w * 0.5f, h * 0.5f)
    if (phase != WashPhase.ENTERING) return home
    val k = ((t - phaseAt) / ENTER).coerceIn(0f, 1f); val e = 1 - (1 - k) * (1 - k)
    return Offset(w + radius * 1.5f + (home.x - w - radius * 1.5f) * e, home.y)
  }
  fun dirtLeft() = splats.sumOf { it.dirt } / (SPLATS * RUBS).toFloat()
  fun splatAt(s: Splat) = center + Offset(s.dx * radius, s.dy * radius)

  private fun mud() {
    splats.clear()
    repeat(SPLATS) { i -> val a = (i + rnd.nextFloat() * 0.6f) * 2 * PI.toFloat() / SPLATS; val d = 0.25f + 0.55f * sqrt(rnd.nextFloat())
      splats += Splat(cos(a) * d, (sin(a) * d * 0.9f).coerceAtMost(0.3f),  // never hidden behind the tub rim (0.55 r)
        0.16f + rnd.nextFloat() * 0.06f, rnd.nextInt(1000)) }
    animalStart = t
  }
  private fun ok(p: Offset) = p.x.isFinite() && p.y.isFinite() && w >= 2f && h >= 2f

  fun down(id: Long, p: Offset) { if (!ok(p)) return; lastTouch = t; lastRub[id] = p; rub(p) }
  fun move(id: Long, p: Offset) {
    if (!ok(p)) return; lastTouch = t
    val last = lastRub[id] ?: return
    if (hypot(p.x - last.x, p.y - last.y) >= radius * 0.12f) { lastRub[id] = p; rub(p) }
  }
  fun up(id: Long) { lastRub.remove(id) }

  /** One rub at [p]: scrubs the splats under the finger (twice as hard once a wash has gone on [STALL] s), always makes foam. */
  fun rub(p: Offset) {
    rubAt = t
    repeat(3) { foam += Foam(p.x + (rnd.nextFloat() - 0.5f) * radius * 0.25f, p.y + (rnd.nextFloat() - 0.5f) * radius * 0.2f, radius * (0.06f + rnd.nextFloat() * 0.07f), t, (rnd.nextFloat() - 0.5f) * radius * 0.3f) }
    if (foam.size > MAX_FOAM) foam.subList(0, foam.size - MAX_FOAM).clear()
    var hit = false
    if (phase == WashPhase.DIRTY) {
      val power = if (t - animalStart > STALL) 2 else 1
      for (s in splats) if (s.dirt > 0 && (splatAt(s) - p).getDistance() < (s.r + 0.22f) * radius) { s.dirt = max(0, s.dirt - power); hit = true }
    }
    if (t - lastScrub >= SCRUB_GAP) { lastScrub = t; sink.play(if (hit) Sfx.scrub else Sfx.plip, if (hit) 0.55f else 0.35f) }
    sink.haptic()
    if (phase == WashPhase.DIRTY && dirtLeft() <= CLEAN_AT) clean()
  }

  private fun clean() {
    phase = WashPhase.CLEAN; phaseAt = t; washed++
    splats.forEach { it.dirt = 0 }
    sink.play(Sfx.shower, 0.5f); sink.animal(critter, 0.9f, 300); sink.play(Sfx.giggle, 0.5f); sink.haptic(true)
  }

  fun step(dtIn: Float) {
    if (w < 2f || h < 2f) return
    val dt = if (dtIn.isNaN()) 0f else dtIn.coerceIn(0f, 0.1f)
    when (phase) {
      WashPhase.CLEAN -> {
        if (t - phaseAt < 1.2f) repeat(3) { parts.burst(center.x + (rnd.nextFloat() - 0.5f) * radius * 2.2f, center.y - radius * 1.5f, 1, listOf(Pal.sky, Color.White, Pal.ice), kind = 0, speed = 60f, size = m * 0.012f, up = -m * 0.3f, decay = 1.2f) }
        if (t - phaseAt >= CELEBRATE) { phase = WashPhase.ENTERING; phaseAt = t; critter = ORDER[(ORDER.indexOf(critter) + 1) % ORDER.size]; mud() }
      }
      WashPhase.ENTERING -> if (t - phaseAt >= ENTER) { phase = WashPhase.DIRTY; animalStart = t }
      else -> {}
    }
    for (f in foam) { f.y -= radius * 0.12f * dt; f.x += f.vx * dt }
    foam.removeAll { t - it.t0 > FOAM_LIFE }
    parts.step(dt)
  }
}

/** Mud splat: a lumpy brown blob, fading as it's scrubbed. */
internal fun DrawScope.drawMud(c: Offset, r: Float, seed: Int, a: Float) {
  if (a <= 0f) return
  val col = Color(0xFF7A4E2D).copy(alpha = 0.92f * a)
  drawCircle(col, r, c)
  for (i in 0 until 5) { val ang = (seed % 7 + i * 1.3f); drawCircle(col, r * (0.45f + (seed + i) % 3 * 0.1f), c + Offset(cos(ang) * r * 0.75f, sin(ang) * r * 0.7f)) }
  drawCircle(Color(0xFF9A6A43).copy(alpha = 0.6f * a), r * 0.35f, c + Offset(-r * 0.25f, -r * 0.25f))
}

internal fun DrawScope.drawFoam(c: Offset, r: Float, a: Float) {
  drawCircle(Color.White.copy(alpha = 0.85f * a), r, c); drawCircle(Pal.sky.copy(alpha = 0.5f * a), r, c, style = Stroke(max(1.5f, r * 0.12f)))
  drawCircle(Color.White.copy(alpha = a), r * 0.3f, c + Offset(-r * 0.35f, -r * 0.35f))
}

@Composable
fun WashScreen() { WashScreenFor(remember { WashModel() }) }

@Composable
internal fun WashScreenFor(m: WashModel) {
  val time = rememberTicker { dt, t -> m.t = t; m.step(dt) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }
    .multiTouch(Unit, onDown = { id, p -> m.down(id, p) }, onMove = { id, p -> m.move(id, p) }, onUp = { m.up(it) })) {
    val t = time.value; m.w = size.width; m.h = size.height; val mm = min(size.width, size.height)
    drawRect(Color(0xFFDDF3FF))
    val tile = mm * 0.12f; var y = 0f
    while (y < size.height) { var x = if ((y / tile).toInt() % 2 == 0) 0f else tile / 2; while (x < size.width) { drawRoundRect(Color(0xFFC6E8FB), Offset(x + 3f, y + 3f), Size(tile - 6f, tile - 6f), CornerRadius(tile * 0.15f)); x += tile }; y += tile }
    val c = m.center; val r = m.radius
    val tub = Rect(size.width * 0.5f - r * 1.7f, c.y + r * 0.55f, size.width * 0.5f + r * 1.7f, min(size.height, c.y + r * 1.7f))
    softShadow(Rect(tub.left, tub.top + r * 0.1f, tub.right, tub.bottom), r * 0.4f, r * 0.15f)
    val shake = if (m.phase == WashPhase.CLEAN) sin((t - m.phaseAt) * 30f) * 8f * (1 - ((t - m.phaseAt) / WashModel.CELEBRATE).coerceIn(0f, 1f)) else 0f
    val happy = m.phase != WashPhase.DIRTY || t - m.rubAt < 0.3f
    rotate(shake, c + Offset(0f, r)) {
      drawCritter(m.critter, c, r, Offset(sin(t * 0.8f) * 0.5f, 0.2f), if ((t % 3.5f) < 0.15f) 1f else 0f, if (happy) 0.8f else 0.2f)
      for (s in m.splats) drawMud(m.splatAt(s), s.r * r, s.seed, s.dirt / WashModel.RUBS.toFloat())
    }
    drawRoundRect(Color.White, tub.topLeft, tub.size, CornerRadius(r * 0.35f))
    drawRoundRect(Pal.sky.copy(alpha = 0.35f), Offset(tub.left, tub.top), Size(tub.width, r * 0.18f), CornerRadius(r * 0.1f))
    for (i in 0 until 9) drawFoam(Offset(tub.left + tub.width * (0.06f + i * 0.11f), tub.top + sin(t * 2f + i) * r * 0.03f), r * (0.11f + (i % 3) * 0.03f), 1f)
    for (f in m.foam) drawFoam(Offset(f.x, f.y), f.r, (1f - (t - f.t0) / WashModel.FOAM_LIFE).coerceIn(0f, 1f))
    if (m.phase == WashPhase.CLEAN) { val k = t - m.phaseAt; val hc = Offset(c.x, c.y - r * 1.65f)
      drawRoundRect(Color(0xFFB0BEC5), hc - Offset(r * 0.35f, r * 0.1f), Size(r * 0.7f, r * 0.2f), CornerRadius(r * 0.1f))
      for (i in 0 until 5) drawStar(c + Offset(cos(i * 1.26f + k * 2f) * r * 1.25f, sin(i * 1.26f + k * 2f) * r * 1.1f), mm * 0.025f, Pal.sun.copy(alpha = (1 - k / WashModel.CELEBRATE).coerceIn(0f, 1f))) }
    m.parts.draw(this)
    if (t - m.lastTouch > WashModel.IDLE_HINT && m.phase == WashPhase.DIRTY) m.splats.firstOrNull { it.dirt > 0 }?.let { drawHand(m.splatAt(it), mm * 0.05f, abs(sin(t * 3f))) }
  }
}
