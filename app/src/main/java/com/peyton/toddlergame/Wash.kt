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
/** A soap bubble floating up out of the tub; tap it to pop. */
internal class TubBubble(var x: Float, var y: Float, val r: Float, val t0: Float, val phase: Float)
/** DIRTY (scrubbing) → RINSE (shower) → SHAKE (shake-off splash) → DRY (blow-dryer; rubbing dries faster) → FLUFFY (sparkly) →
 *  ENTERING (next friend). Only DIRTY can be scrubbed; every phase still answers every touch. */
internal enum class WashPhase { DIRTY, RINSE, SHAKE, DRY, FLUFFY, ENTERING }
/** Who's in the tub. Elephant and sheep are drawn here and have synthesized voices; the rest are shared [Critter]s. */
internal enum class WashAnimal(val critter: Critter?) { PIG(Critter.PIG), DOG(Critter.DOG), ELEPHANT(null), SHEEP(null), COW(Critter.COW), DUCK(Critter.DUCK), CAT(Critter.CAT), LION(Critter.LION) }

/** Wash the animal, for an 18-month-old: one big muddy animal; rubbing or tapping scrubs the mud off with foam, and rubbing the
 *  belly makes it giggle. Soap bubbles float up to be popped. When it's mostly clean: shower, a shake-off splash, a blow-dry
 *  (rubbing helps, but it finishes by itself) and a fluffy sparkly finish, then the next muddy friend walks in. No fail. */
internal class WashModel(private val sink: SoundSink = GameSound, private val rnd: Random = Random.Default, start: WashAnimal? = null) {
  companion object {
    const val SPLATS = 12; const val RUBS = 3; const val CLEAN_AT = 0.2f; const val ENTER = 0.8f
    const val RINSE = 1.2f; const val SHAKE = 0.7f; const val DRY = 1.6f; const val FLUFFY = 1.4f
    const val STALL = 10f; const val MAX_FOAM = 80; const val FOAM_LIFE = 1.6f; const val SCRUB_GAP = 0.09f; const val IDLE_HINT = 7f
    const val GIGGLE_GAP = 0.8f; const val MAX_BUBBLES = 6; const val BUBBLE_EVERY = 0.7f; const val BUBBLE_LIFE = 6f
    val ORDER = WashAnimal.entries
  }
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = -100f
  var animal = start ?: ORDER[rnd.nextInt(ORDER.size)]; private set
  val splats = ArrayList<Splat>(); val foam = ArrayList<Foam>(); val bubbles = ArrayList<TubBubble>()
  val parts = Particles(300).apply { gravity = 1400f }
  var phase = WashPhase.DIRTY; private set
  var phaseAt = 0f; private set
  var animalStart = 0f; private set
  var washed = 0; private set
  var rubAt = -10f; private set
  var giggleAt = -10f; private set
  var popped = 0; private set
  /** 0 wet .. 1 dry, during [WashPhase.DRY]. */
  var dryness = 0f; private set
  private val lastRub = HashMap<Long, Offset>()
  private var lastScrub = -10f; private var lastBubble = 0f; private var lastDryRub = -10f

  init { mud() }

  val m get() = min(w, h)
  val radius get() = m * 0.3f
  val center: Offset get() {
    val home = Offset(w * 0.5f, h * 0.5f)
    if (phase != WashPhase.ENTERING) return home
    val k = ((t - phaseAt) / ENTER).coerceIn(0f, 1f); val e = 1 - (1 - k) * (1 - k)
    return Offset(home.x + (w * 0.5f + radius * 1.5f) * (1 - e), home.y)
  }
  /** The tummy: lower middle of the animal, above the tub rim. */
  val belly get() = center + Offset(0f, radius * 0.36f)
  val bellyR get() = radius * 0.24f
  val bubbleR get() = m * 0.075f
  fun dirtLeft() = splats.sumOf { it.dirt } / (SPLATS * RUBS).toFloat()
  fun splatAt(s: Splat) = center + Offset(s.dx * radius, s.dy * radius)
  fun bubbleAt(b: TubBubble) = Offset(b.x + sin((t - b.t0) * 2f + b.phase) * bubbleR * 0.5f, b.y)

  private fun mud() {
    splats.clear()
    repeat(SPLATS) { i -> val a = (i + rnd.nextFloat() * 0.6f) * 2 * PI.toFloat() / SPLATS; val d = 0.25f + 0.55f * sqrt(rnd.nextFloat())
      splats += Splat(cos(a) * d, (sin(a) * d * 0.9f).coerceAtMost(0.3f),  // never hidden behind the tub rim (0.55 r)
        0.16f + rnd.nextFloat() * 0.06f, rnd.nextInt(1000)) }
    animalStart = t
  }
  private fun ok(p: Offset) = p.x.isFinite() && p.y.isFinite() && w >= 2f && h >= 2f
  private fun go(p: WashPhase) { phase = p; phaseAt = t }

  fun down(id: Long, p: Offset) {
    if (!ok(p)) return
    lastTouch = t
    val b = bubbles.lastOrNull { (bubbleAt(it) - p).getDistance() < bubbleR * 1.4f }
    if (b != null) { pop(b); return }
    lastRub[id] = p; rub(p)
  }
  fun move(id: Long, p: Offset) {
    if (!ok(p)) return; lastTouch = t
    val last = lastRub[id] ?: return
    if (hypot(p.x - last.x, p.y - last.y) >= radius * 0.12f) { lastRub[id] = p; rub(p) }
  }
  fun up(id: Long) { lastRub.remove(id) }

  private fun pop(b: TubBubble) {
    bubbles.remove(b); popped++; val c = bubbleAt(b)
    sink.play(Sfx.bubblePop, 0.6f); sink.haptic()
    parts.burst(c.x, c.y, 10, listOf(Color.White, Pal.sky, Pal.ice), kind = 0, speed = m * 0.5f, size = m * 0.01f, up = m * 0.2f, decay = 1.3f)
  }

  /** One rub at [p]: always foam + a sound. While dirty it scrubs the splats under the finger (twice as hard once a wash has gone on
   *  [STALL] s) and the belly giggles; while drying it towels the fur dry faster. */
  fun rub(p: Offset) {
    rubAt = t
    repeat(3) { foam += Foam(p.x + (rnd.nextFloat() - 0.5f) * radius * 0.25f, p.y + (rnd.nextFloat() - 0.5f) * radius * 0.2f, radius * (0.06f + rnd.nextFloat() * 0.07f), t, (rnd.nextFloat() - 0.5f) * radius * 0.3f) }
    if (foam.size > MAX_FOAM) foam.subList(0, foam.size - MAX_FOAM).clear()
    var hit = false
    if (phase == WashPhase.DIRTY) {
      val power = if (t - animalStart > STALL) 2 else 1
      for (s in splats) if (s.dirt > 0 && (splatAt(s) - p).getDistance() < (s.r + 0.22f) * radius) { s.dirt = max(0, s.dirt - power); hit = true }
    }
    if (phase != WashPhase.ENTERING && (p - belly).getDistance() < bellyR * 1.5f && t - giggleAt >= GIGGLE_GAP) { giggleAt = t; sink.play(Sfx.giggle, 0.55f) }
    if (phase == WashPhase.DRY && (p - center).getDistance() < radius * 1.3f) { dryness = min(1f, dryness + 0.12f)
      if (t - lastDryRub >= 0.15f) { lastDryRub = t; sink.play(Sfx.whoosh, 0.25f) } }
    if (t - lastScrub >= SCRUB_GAP) { lastScrub = t; sink.play(if (hit) Sfx.scrub else Sfx.plip, if (hit) 0.55f else 0.35f) }
    sink.haptic()
    if (phase == WashPhase.DIRTY && dirtLeft() <= CLEAN_AT) clean()
  }

  private fun clean() {
    go(WashPhase.RINSE); washed++
    splats.forEach { it.dirt = 0 }
    sink.play(Sfx.shower, 0.5f); sink.haptic(true)
  }

  private fun voice() { val v = animal.critter ?: Critter.SHEEP.takeIf { animal == WashAnimal.SHEEP }   // the sheep has a real baa now
    if (v != null) sink.animal(v, 0.9f, 0) else sink.play(Sfx.trumpet, 0.6f) }

  fun step(dtIn: Float) {
    if (w < 2f || h < 2f) return
    val dt = if (dtIn.isNaN()) 0f else dtIn.coerceIn(0f, 0.1f)
    val k = t - phaseAt
    when (phase) {
      WashPhase.RINSE -> {
        if (k < RINSE * 0.9f) repeat(3) { parts.burst(center.x + (rnd.nextFloat() - 0.5f) * radius * 2.2f, center.y - radius * 1.5f, 1, listOf(Pal.sky, Color.White, Pal.ice), kind = 0, speed = 60f, size = m * 0.012f, up = -m * 0.3f, decay = 1.2f) }
        if (k >= RINSE) { go(WashPhase.SHAKE); sink.play(Sfx.splash, 0.6f); sink.haptic(true)
          repeat(2) { parts.burst(center.x, center.y, 20, listOf(Pal.sky, Color.White, Pal.ice), kind = 0, speed = m * 1.4f, size = m * 0.014f, up = m * 0.5f, decay = 1.1f) } }
      }
      WashPhase.SHAKE -> if (k >= SHAKE) { go(WashPhase.DRY); dryness = 0f; sink.play(Sfx.blowDry, 0.4f) }
      WashPhase.DRY -> { dryness = min(1f, dryness + dt / DRY)
        if (dryness >= 1f) { go(WashPhase.FLUFFY); voice(); sink.play(Sfx.giggle, 0.5f); sink.play(Sfx.chime, 0.4f); sink.haptic(true)
          parts.burst(center.x, center.y - radius * 0.3f, 24, listOf(Pal.sun, Color.White, Pal.pink), kind = 1, speed = m * 0.9f, size = m * 0.02f, up = m * 0.3f, decay = 0.9f) } }
      WashPhase.FLUFFY -> if (k >= FLUFFY) { go(WashPhase.ENTERING); animal = ORDER[(ORDER.indexOf(animal) + 1) % ORDER.size]; mud() }
      WashPhase.ENTERING -> if (k >= ENTER) { go(WashPhase.DIRTY); animalStart = t }
      WashPhase.DIRTY -> {}
    }
    // soap bubbles drift up from the tub
    if (t - lastBubble >= BUBBLE_EVERY && bubbles.size < MAX_BUBBLES) { lastBubble = t
      bubbles += TubBubble(w * 0.5f + (rnd.nextFloat() - 0.5f) * radius * 3f, center.y + radius * 0.6f, bubbleR * (0.8f + rnd.nextFloat() * 0.4f), t, rnd.nextFloat() * 6.28f) }
    for (b in bubbles) b.y -= m * 0.09f * dt
    bubbles.removeAll { it.y < -bubbleR || t - it.t0 > BUBBLE_LIFE }
    for (f in foam) { f.y -= radius * 0.12f * dt; f.x += f.vx * dt }
    foam.removeAll { t - it.t0 > FOAM_LIFE }
    parts.step(dt)
  }
}

/** Elephant and sheep faces (the others use [drawCritter]). [fluff] 0..1 extra fluffiness for the sheep's wool. */
internal fun DrawScope.drawWashAnimal(a: WashAnimal, c: Offset, r: Float, look: Offset, blink: Float, mouth: Float, fluff: Float) {
  a.critter?.let { drawCritter(it, c, r, look, blink, mouth); return }
  fun o(x: Float, y: Float) = c + Offset(x * r, y * r)
  val ink = Color(0xFF3B2A20)
  fun eyes(y: Float) { for (s in listOf(-1f, 1f)) { val e = o(s * 0.35f, y)
    if (blink > 0.5f) drawLine(ink, e - Offset(r * 0.13f, 0f), e + Offset(r * 0.13f, 0f), r * 0.05f)
    else { drawCircle(Color.White, r * 0.19f, e); drawCircle(ink, r * 0.11f, e + Offset(look.x * r * 0.05f, look.y * r * 0.05f)); drawCircle(Color.White, r * 0.035f, e + Offset(-r * 0.04f, -r * 0.04f)) } } }
  when (a) {
    WashAnimal.ELEPHANT -> { val g = Color(0xFFA7B4C8)
      for (s in listOf(-1f, 1f)) { drawOval(g.darken(0.08f), o(if (s < 0) -1.55f else 0.55f, -0.75f), Size(r * 1.0f, r * 1.2f)); drawOval(Color(0xFFF4B6C6), o(if (s < 0) -1.38f else 0.7f, -0.55f), Size(r * 0.66f, r * 0.8f)) }
      drawCircle(g, r, c); drawCircle(Color.White.copy(alpha = 0.18f), r * 0.35f, o(-0.35f, -0.4f))
      eyes(-0.2f)
      val trunk = androidx.compose.ui.graphics.Path().apply { moveTo(o(0f, 0.05f).x, o(0f, 0.05f).y); cubicTo(o(0f, 0.6f).x, o(0f, 0.6f).y, o(0.05f, 0.95f).x, o(0.05f, 0.95f).y, o(0.4f, 0.85f).x, o(0.4f, 0.85f).y) }
      drawPath(trunk, g.darken(0.05f), style = Stroke(r * 0.3f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
      for (s in listOf(-1f, 1f)) drawCircle(Color(0xFFFF6F91).copy(alpha = 0.35f), r * 0.14f, o(s * 0.6f, 0.25f))
      if (mouth > 0.5f) drawArc(ink, 20f, 140f, false, o(-0.45f, 0.2f), Size(r * 0.3f, r * 0.2f), style = Stroke(r * 0.04f)) }
    else -> { val wool = Color(0xFFFFFDF7); val n = 14; val wr = r * (0.36f + 0.1f * fluff)   // SHEEP: a cloud of wool round a cream face
      for (i in 0 until n) { val ang = i * 2 * PI.toFloat() / n; drawCircle(wool.darken(0.04f), wr, o(cos(ang) * (0.98f + 0.1f * fluff), sin(ang) * (0.92f + 0.1f * fluff))) }
      drawCircle(wool, r * 0.98f, c)
      for (s in listOf(-1f, 1f)) drawOval(Color(0xFF6B5446), o(if (s < 0) -1.25f else 0.75f, -0.3f), Size(r * 0.5f, r * 0.26f))
      drawOval(Color(0xFF8A6E5C), o(-0.55f, -0.45f), Size(r * 1.1f, r * 1.25f))
      for (i in 0 until 5) drawCircle(wool, r * 0.2f, o(-0.4f + i * 0.2f, -0.75f))
      eyes(-0.05f)
      drawOval(Color(0xFF5A4336), o(-0.12f, 0.35f), Size(r * 0.24f, r * 0.14f))
      if (mouth > 0.5f) drawArc(Color(0xFF3B2A20), 20f, 140f, false, o(-0.15f, 0.4f), Size(r * 0.3f, r * 0.2f), style = Stroke(r * 0.04f)) }
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
    val c = m.center; val r = m.radius; val k = t - m.phaseAt
    val tub = Rect(size.width * 0.5f - r * 1.7f, c.y + r * 0.55f, size.width * 0.5f + r * 1.7f, min(size.height, c.y + r * 1.7f))
    softShadow(Rect(tub.left, tub.top + r * 0.1f, tub.right, tub.bottom), r * 0.4f, r * 0.15f)
    val g = t - m.giggleAt
    val shake = when (m.phase) {
      WashPhase.SHAKE -> sin(k * 45f) * 16f * (1 - (k / WashModel.SHAKE).coerceIn(0f, 1f))
      WashPhase.FLUFFY -> sin(k * 8f) * 5f * (1 - (k / WashModel.FLUFFY).coerceIn(0f, 1f))
      else -> 0f } + (if (g in 0f..0.6f) sin(g * 35f) * 6f * (1 - g / 0.6f) else 0f)
    val happy = m.phase != WashPhase.DIRTY || t - m.rubAt < 0.3f
    val fluffy = when (m.phase) { WashPhase.DRY -> m.dryness; WashPhase.FLUFFY -> 1f; else -> 0f }
    rotate(shake, c + Offset(0f, r)) {
      if (fluffy > 0f) for (i in 0 until 16) { val a = i * 2 * PI.toFloat() / 16 + 0.2f   // fur fluffing up as it dries
        drawCircle(Color.White.copy(alpha = 0.55f * fluffy), r * 0.22f * fluffy, c + Offset(cos(a) * r * 0.98f, sin(a) * r * 0.95f)) }
      drawWashAnimal(m.animal, c, r, Offset(sin(t * 0.8f) * 0.5f, 0.2f), if ((t % 3.5f) < 0.15f || g in 0f..0.6f) 1f else 0f, if (happy) 0.8f else 0.2f,
        if (m.animal == WashAnimal.SHEEP) fluffy else 0f)
      for (s in m.splats) drawMud(m.splatAt(s), s.r * r, s.seed, s.dirt / WashModel.RUBS.toFloat())
      if (m.phase == WashPhase.RINSE || m.phase == WashPhase.SHAKE || (m.phase == WashPhase.DRY && m.dryness < 0.6f)) {   // wet: drips
        val wet = if (m.phase == WashPhase.DRY) 1f - m.dryness / 0.6f else 1f
        for (i in 0 until 7) { val dx = (i - 3) * r * 0.28f; val dy = ((t * 1.2f + i * 0.37f) % 1f)
          drawCircle(Pal.sky.copy(alpha = 0.7f * wet * (1 - dy)), r * 0.05f, c + Offset(dx, r * (0.1f + dy * 0.5f))) } }
    }
    drawRoundRect(Color.White, tub.topLeft, tub.size, CornerRadius(r * 0.35f))
    drawRoundRect(Pal.sky.copy(alpha = 0.35f), Offset(tub.left, tub.top), Size(tub.width, r * 0.18f), CornerRadius(r * 0.1f))
    for (i in 0 until 9) drawFoam(Offset(tub.left + tub.width * (0.06f + i * 0.11f), tub.top + sin(t * 2f + i) * r * 0.03f), r * (0.11f + (i % 3) * 0.03f), 1f)
    for (f in m.foam) drawFoam(Offset(f.x, f.y), f.r, (1f - (t - f.t0) / WashModel.FOAM_LIFE).coerceIn(0f, 1f))
    for (b in m.bubbles) { val p = m.bubbleAt(b); val a = ((t - b.t0) / 0.4f).coerceIn(0f, 1f)
      drawCircle(Color.White.copy(alpha = 0.25f * a), b.r, p); drawCircle(Pal.sky.copy(alpha = 0.7f * a), b.r, p, style = Stroke(b.r * 0.08f))
      drawCircle(Color.White.copy(alpha = 0.9f * a), b.r * 0.22f, p + Offset(-b.r * 0.38f, -b.r * 0.38f))
      drawArc(Pal.pink.copy(alpha = 0.35f * a), 200f, 70f, false, p - Offset(b.r * 0.8f, b.r * 0.8f), Size(b.r * 1.6f, b.r * 1.6f), style = Stroke(b.r * 0.08f)) }
    if (m.phase == WashPhase.RINSE) { val hc = Offset(c.x, c.y - r * 1.65f)
      drawRoundRect(Color(0xFFB0BEC5), hc - Offset(r * 0.35f, r * 0.1f), Size(r * 0.7f, r * 0.2f), CornerRadius(r * 0.1f)) }
    if (m.phase == WashPhase.DRY) {   // blow-dryer at the side, puffing warm air
      val dc = Offset(c.x + r * 1.45f, c.y - r * 0.75f); val dr = r * 0.22f
      drawRoundRect(Pal.coral, dc - Offset(dr * 1.6f, dr * 0.7f), Size(dr * 2.4f, dr * 1.4f), CornerRadius(dr * 0.7f))
      drawRoundRect(Pal.coral.darken(0.15f), dc + Offset(dr * 0.2f, dr * 0.3f), Size(dr * 0.55f, dr * 1.6f), CornerRadius(dr * 0.25f))
      drawCircle(Color.White, dr * 0.35f, dc + Offset(dr * 0.4f, -dr * 0.05f))
      for (i in 0 until 4) { val q = ((t * 2.5f + i * 0.25f) % 1f)
        drawArc(Color.White.copy(alpha = 0.8f * (1 - q)), 150f, 60f, false, dc - Offset(dr * (2.2f + q * 3f), dr * (0.9f + q * 0.6f)), Size(dr * (1.4f + q), dr * (1.8f + q * 1.2f)), style = Stroke(dr * 0.12f)) } }
    if (m.phase == WashPhase.FLUFFY) for (i in 0 until 7) { val a = i * 0.9f + k * 2f
      drawStar(c + Offset(cos(a) * r * 1.25f, sin(a) * r * 1.1f), mm * (0.022f + 0.01f * sin(k * 9f + i)), Pal.sun.copy(alpha = (1 - k / WashModel.FLUFFY).coerceIn(0f, 1f)), k * 120f) }
    m.parts.draw(this)
    if (t - m.lastTouch > WashModel.IDLE_HINT && m.phase == WashPhase.DIRTY) m.splats.firstOrNull { it.dirt > 0 }?.let { drawHand(m.splatAt(it), mm * 0.05f, abs(sin(t * 3f))) }
  }
}
