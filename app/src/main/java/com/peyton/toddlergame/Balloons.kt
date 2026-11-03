package com.peyton.toddlergame

import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.*
import kotlin.random.Random

private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-black", Typeface.BOLD) }

/** symbol: 0 none, 1 star, 2 heart, 3 smiley, 4 letter/number text in [label]. */
fun DrawScope.drawBalloon(c: Offset, r: Float, color: Color, t: Float, phase: Float, symbol: Int = 0, label: String = "", string: Boolean = true) {
  val knot = c + Offset(0f, r * 1.17f)
  if (string) {
    val p = Path(); p.moveTo(knot.x, knot.y)
    p.cubicTo(knot.x + sin(t * 2f + phase) * r * 0.3f, knot.y + r * 0.8f, knot.x - sin(t * 2.3f + phase) * r * 0.3f, knot.y + r * 1.6f, knot.x + sin(t * 1.7f + phase) * r * 0.22f, knot.y + r * 2.4f)
    drawPath(p, Color(0xFF9A93B8).copy(alpha = 0.8f), style = Stroke(max(2f, r * 0.03f), cap = StrokeCap.Round))
  }
  softShadow(Rect(c.x - r * 0.6f + r * 0.25f, c.y - r * 0.5f + r * 0.35f, c.x + r * 0.6f + r * 0.25f, c.y + r * 1.05f + r * 0.35f), r * 0.6f, r * 0.35f, Pal.shadow.copy(alpha = 0.13f))
  val body = Path().apply {
    moveTo(c.x, c.y - r * 1.1f)
    cubicTo(c.x + r * 1.25f, c.y - r * 1.1f, c.x + r * 1.15f, c.y + r * 0.5f, c.x + r * 0.1f, c.y + r * 1.08f)
    lineTo(c.x - r * 0.1f, c.y + r * 1.08f)
    cubicTo(c.x - r * 1.15f, c.y + r * 0.5f, c.x - r * 1.25f, c.y - r * 1.1f, c.x, c.y - r * 1.1f); close()
  }
  drawPath(body, Brush.radialGradient(listOf(color.lighten(0.5f), color, color.darken(0.28f)), c + Offset(-r * 0.35f, -r * 0.5f), r * 1.75f))
  drawPath(body, Brush.radialGradient(listOf(Color.Transparent, color.lighten(0.3f).copy(alpha = 0.35f)), c + Offset(r * 0.25f, r * 0.55f), r * 0.7f))
  drawPath(Path().apply { moveTo(c.x - r * 0.15f, c.y + r * 1.24f); lineTo(c.x + r * 0.15f, c.y + r * 1.24f); lineTo(c.x, c.y + r * 1.04f); close() }, color.darken(0.2f))
  rotate(-28f, c + Offset(-r * 0.42f, -r * 0.5f)) { drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.1f)), c.y - r * 0.85f, c.y - r * 0.2f), c + Offset(-r * 0.58f, -r * 0.85f), Size(r * 0.32f, r * 0.62f)) }
  drawCircle(Color.White.copy(alpha = 0.85f), r * 0.065f, c + Offset(-r * 0.12f, -r * 0.86f))
  val sc = Color.White.copy(alpha = 0.92f); val sp = c + Offset(0f, -r * 0.02f)
  when (symbol) {
    1 -> drawStar(sp, r * 0.42f, sc)
    2 -> drawHeart(sp, r * 0.32f, sc)
    3 -> { drawCircle(sc, r * 0.08f, sp + Offset(-r * 0.24f, -r * 0.12f)); drawCircle(sc, r * 0.08f, sp + Offset(r * 0.24f, -r * 0.12f))
      drawArc(sc, 20f, 140f, false, sp + Offset(-r * 0.3f, -r * 0.15f), Size(r * 0.6f, r * 0.45f), style = Stroke(r * 0.08f, cap = StrokeCap.Round)) }
    4 -> { symbolPaint.textSize = r * 0.95f; symbolPaint.color = sc.toArgb(); drawContext.canvas.nativeCanvas.drawText(label, sp.x, sp.y + r * 0.33f, symbolPaint) }
  }
}

internal class Balloon(var x: Float, var y: Float, val r: Float, val color: Color, val phase: Float, var vx: Float, var vy: Float, val symbol: Int, val label: String) { var born = 0f; var leaving = false }
internal class SoapBubble(var x: Float, var y: Float, val r: Float, val phase: Float, var vy: Float) { var born = 0f }
internal class Floater(var x: Float, var y: Float, val critter: Critter, val t0: Float, val r: Float, val color: Color)
internal class PhotoFloat(var x: Float, var y: Float, val img: androidx.compose.ui.graphics.ImageBitmap, val t0: Float, val s: Float)
internal class Flash(val x: Float, val y: Float, val r: Float, val t0: Float, val color: Color)

/** Classic tap-to-make / tap-to-pop play: touching empty sky blows up a balloon right under the finger;
 *  balloons drift gently and stay until popped; soap bubbles sometimes tag along. Nothing to win or lose. */
internal class BalloonModel(private val sink: SoundSink = GameSound, private val rnd: Random = Random.Default) {
  val balloons = ArrayList<Balloon>(); val bubbles = ArrayList<SoapBubble>(); val floaters = ArrayList<Floater>(); val photos = ArrayList<PhotoFloat>(); val flashes = ArrayList<Flash>()
  val parts = Particles(600).apply { gravity = 700f }
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = 0f; var pops = 0; var lastSpawn = 0f; var emptySince = 0f; var started = false
  private val labels = ('A'..'Z').map { it.toString() } + (1..9).map { it.toString() }
  private var colorIdx = rnd.nextInt(9)
  val cap get() = (w * h / (min(w, h) * min(w, h) * 0.12f)).toInt().coerceIn(8, 16)
  fun make(x: Float, y: Float, rising: Boolean = false) {
    val m = min(w, h); val r = m * (0.09f + rnd.nextFloat() * 0.035f)
    colorIdx = (colorIdx + 1 + rnd.nextInt(3)) % Pal.vivid.size   // never the same color twice in a row
    val sym = if (!Prefs.balloonSymbols) 0 else when (rnd.nextInt(10)) { 0 -> 1; 1 -> 2; 2 -> 3; 3, 4 -> 4; else -> 0 }
    val cx = x.coerceIn(r, w - r); val cy = y.coerceIn(r * 1.2f, h - r * 1.3f)
    balloons += Balloon(cx, if (rising) h + r * 1.5f else cy, r, Pal.vivid[colorIdx], rnd.nextFloat() * 6f,
      (rnd.nextFloat() - 0.5f) * m * 0.05f, if (rising) -h * 0.35f else -m * (0.01f + rnd.nextFloat() * 0.02f), sym, labels.random(rnd)).also { it.born = t }
    if (balloons.count { !it.leaving } > cap) balloons.filter { !it.leaving }.minByOrNull { it.born }?.let { it.leaving = true }
    lastSpawn = t
  }
  fun bubble(x: Float, y: Float) { val m = min(w, h)
    bubbles += SoapBubble(x + (rnd.nextFloat() - 0.5f) * m * 0.2f, y + (rnd.nextFloat() - 0.5f) * m * 0.1f, m * (0.035f + rnd.nextFloat() * 0.035f), rnd.nextFloat() * 6f, -m * (0.08f + rnd.nextFloat() * 0.06f)).also { it.born = t }
    if (bubbles.size > 10) bubbles.removeAt(0) }
  fun step(dt: Float) {
    if (w < 2f) return
    if (!started) { started = true; make(w * 0.5f, h * 0.5f); make(w * 0.28f, h * 0.62f); make(w * 0.72f, h * 0.4f); balloons.forEach { it.born = -1f } }
    // Never leave the toddler staring at an empty sky: a fresh balloon floats up and settles.
    if (balloons.none { !it.leaving }) { if (emptySince < 0f) emptySince = t; if (t - emptySince > 2.5f) { make(w * (0.25f + rnd.nextFloat() * 0.5f), h * 0.5f, rising = true); emptySince = -1f } } else emptySince = -1f
    val m = min(w, h)
    for (b in balloons) {
      if (b.leaving) { b.vy -= h * 0.4f * dt; b.y += b.vy * dt; continue }
      if (b.vy < -m * 0.03f && b.y < h * 0.65f) b.vy += (-(m * 0.015f) - b.vy) * min(1f, dt * 1.6f)   // risers slow to a hover
      b.x += (b.vx + sin(t * 0.7f + b.phase) * m * 0.025f) * dt; b.y += (b.vy + cos(t * 0.9f + b.phase) * m * 0.02f) * dt
      if (b.x < b.r) { b.x = b.r; b.vx = abs(b.vx) }; if (b.x > w - b.r) { b.x = w - b.r; b.vx = -abs(b.vx) }
      if (b.y < b.r * 1.2f) { b.y = b.r * 1.2f; b.vy = abs(b.vy) * 0.6f + m * 0.01f }
      if (b.y > h - b.r * 1.3f && b.vy > 0f) b.vy = -abs(b.vy)
    }
    // soft separation so balloons don't stack on one spot
    for (i in balloons.indices) for (j in i + 1 until balloons.size) { val a = balloons[i]; val c = balloons[j]; if (a.leaving || c.leaving) continue
      val dx = c.x - a.x; val dy = c.y - a.y; val d = hypot(dx, dy); val md = (a.r + c.r) * 0.95f
      if (d in 0.01f..md) { val p = (md - d) / d * 0.5f * min(1f, dt * 4f); a.x -= dx * p; a.y -= dy * p; c.x += dx * p; c.y += dy * p } }
    balloons.removeAll { it.y < -it.r * 4f }
    for (u in bubbles) { u.y += u.vy * dt; u.x += sin(t * 2f + u.phase) * u.r * 1.2f * dt }; bubbles.removeAll { it.y < -it.r * 2f }
    for (f in floaters) { f.y -= h * 0.09f * dt; f.x += sin((t - f.t0) * 2f) * f.r * 0.6f * dt }
    floaters.removeAll { it.y < -it.r * 5f }
    for (p in photos) { p.y -= h * 0.06f * dt; p.x += sin((t - p.t0) * 1.5f) * p.s * 0.15f * dt }; photos.removeAll { it.y < -it.s * 2f }
    flashes.removeAll { t - it.t0 > 0.35f }
    parts.step(dt)
  }
  fun pop(b: Balloon) {
    balloons.remove(b); pops++
    sink.play(Sfx.balloonPop, 0.85f); sink.play(Sfx.xylo[rnd.nextInt(3, 10)], 0.35f); sink.haptic(true)
    flashes += Flash(b.x, b.y, b.r, t, b.color)
    parts.burst(b.x, b.y, 12, listOf(b.color, b.color.darken(0.1f)), kind = 5, speed = b.r * 9f, size = b.r * 0.16f, up = 50f, decay = 1.6f)
    parts.burst(b.x, b.y, 30, Pal.vivid, kind = 4, speed = b.r * 11f, size = b.r * 0.1f, up = b.r * 3f, decay = 0.55f)
    parts.burst(b.x, b.y, 8, listOf(Color.White, Pal.sun), kind = 1, speed = b.r * 6f, size = b.r * 0.13f, up = 100f, decay = 1.2f)
    if (Photos.images.isNotEmpty() && pops % 4 == 3 && photos.size < 2) { photos += PhotoFloat(b.x, b.y, Photos.images.random(rnd).second, t, min(w, h) * 0.3f); sink.play(Sfx.chime, 0.4f) }
    else if (pops % 6 == 5) { val cr = Critter.entries.random(rnd); floaters += Floater(b.x, b.y, cr, t, b.r * 0.55f, Pal.vivid.random(rnd)); sink.animal(cr, 0.85f, delayMs = 250) }
  }
  fun popBubble(u: SoapBubble) { bubbles.remove(u); sink.play(Sfx.bubblePop, 0.45f); sink.haptic()
    parts.burst(u.x, u.y, 8, listOf(Color.White, Color(0xFFBFE6FF), Color(0xFFFFD6F5)), kind = 0, speed = u.r * 8f, size = u.r * 0.12f, up = 20f, decay = 2f) }
  fun tap(p: Offset) {
    lastTouch = t
    val b = balloons.filter { !it.leaving && hypot(it.x - p.x, (it.y - p.y) * 0.85f) < it.r * 1.3f }.minByOrNull { hypot(it.x - p.x, it.y - p.y) }
    if (b != null) { pop(b); return }
    val u = bubbles.firstOrNull { hypot(it.x - p.x, it.y - p.y) < it.r * 1.6f }
    if (u != null) { popBubble(u); return }
    if (t - lastSpawn < 0.12f) return
    make(p.x, p.y); sink.play(Sfx.inflate, 0.4f); sink.haptic()
    if (rnd.nextInt(3) == 0) repeat(1 + rnd.nextInt(2)) { bubble(p.x, p.y) }
  }
}

@Composable
fun BalloonsScreen() {
  val m = remember { BalloonModel() }
  val time = rememberTicker { dt, t -> m.t = t; m.step(dt) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }
    .multiTouch(Unit, onDown = { _, p -> m.tap(p) }, onMove = { _, p -> m.balloons.firstOrNull { !it.leaving && hypot(it.x - p.x, it.y - p.y) < it.r * 0.9f }?.let { m.pop(it) } })) {
    val t = time.value; m.w = size.width; m.h = size.height; val mm = min(size.width, size.height)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF3FA9F5), Color(0xFF7CC8FA), Color(0xFFC6E9FF))))
    val sunC = Offset(size.width * 0.85f, size.height * 0.13f)
    drawCircle(Brush.radialGradient(listOf(Color(0x99FFF6C9), Color(0x00FFF6C9)), sunC, mm * 0.28f), mm * 0.28f, sunC)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF8D6), Color(0xFFFFD45C)), sunC - Offset(mm * 0.03f, mm * 0.03f), mm * 0.12f), mm * 0.085f, sunC)
    for (i in 0 until 3) softCloud(Offset(((t * 9f + i * size.width / 2.2f) % (size.width * 1.5f)) - size.width * 0.25f, size.height * (0.12f + 0.1f * i)), mm * 0.06f, 0.7f)
    for (i in 0 until 3) softCloud(Offset(((t * 18f + i * size.width / 1.6f + 300f) % (size.width * 1.5f)) - size.width * 0.25f, size.height * (0.55f + 0.14f * i)), mm * 0.1f, 0.95f)
    for (u in m.bubbles) { val g = ((t - u.born) / 0.25f).coerceIn(0f, 1f); val r = u.r * g * (1 + 0.04f * sin(t * 5f + u.phase)); val c = Offset(u.x, u.y)
      drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.05f), Color(0xFFBFE6FF).copy(alpha = 0.25f), Color(0xFFFFC6F0).copy(alpha = 0.55f)), c, r), r, c)
      drawCircle(Color.White.copy(alpha = 0.7f), r, c, style = Stroke(max(1.5f, r * 0.05f)))
      drawOval(Color.White.copy(alpha = 0.8f), c + Offset(-r * 0.55f, -r * 0.6f), Size(r * 0.4f, r * 0.25f)) }
    for (b in m.balloons.sortedBy { it.r }) {
      val age = t - b.born
      val grow = if (b.born < 0f || b.y > m.h) 1f else (age / 0.3f).coerceIn(0f, 1f).let { k -> (0.15f + 0.85f * k) * (1f + 0.22f * sin(k * PI.toFloat())) }
      val sq = 0.025f * sin(t * 2.4f + b.phase)
      rotate(sin(t * 0.9f + b.phase) * 6f, Offset(b.x, b.y + b.r)) { scale((1 + sq) * grow, (1 - sq) * grow, Offset(b.x, b.y + b.r)) {
        drawBalloon(Offset(b.x, b.y), b.r, b.color, t, b.phase, b.symbol, b.label)
      } }
    }
    for (f in m.flashes) { val k = (t - f.t0) / 0.35f
      drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.6f * (1 - k)), Color.Transparent), Offset(f.x, f.y), f.r * (0.6f + k)), f.r * (0.6f + k), Offset(f.x, f.y))
      drawCircle(f.color.copy(alpha = 0.6f * (1 - k)), f.r * (0.7f + k * 0.9f), Offset(f.x, f.y), style = Stroke(f.r * 0.08f * (1 - k) + 1f)) }
    for (f in m.floaters) { val age = t - f.t0; val sc = (age / 0.35f).coerceIn(0f, 1f); val c = Offset(f.x, f.y)
      scale(sc, sc, c) {
        drawLine(Color(0xFF9A93B8), c + Offset(0f, -f.r * 0.9f), c + Offset(f.r * 0.2f, -f.r * 2.6f), 3f)
        drawBalloon(c + Offset(f.r * 0.2f, -f.r * 3.4f), f.r * 0.65f, f.color, t, 0f, string = false)
        rotate(sin(age * 3f) * 10f, c) { drawCritter(f.critter, c, f.r, Offset(0f, -0.6f), 0f, 0.6f + 0.4f * sin(age * 6f).coerceAtLeast(0f)) }
      } }
    for (p in m.photos) { val age = t - p.t0; val sc = (age / 0.45f).coerceIn(0f, 1f); val c = Offset(p.x, p.y)
      scale(sc, sc, c) { drawLine(Color(0xFF9A93B8), c + Offset(0f, -p.s * 0.5f), c + Offset(0f, -p.s * 0.85f), 3f)
        drawBalloon(c + Offset(0f, -p.s * 1.05f), p.s * 0.2f, Pal.pink, t, 0f, 2, string = false)
        drawFramedPhoto(p.img, c, p.s, sin(age * 1.3f) * 6f) } }
    m.parts.draw(this)
    if (t - m.lastTouch > 5f) m.balloons.filter { !it.leaving }.maxByOrNull { it.r }?.let { drawHand(Offset(it.x, it.y), mm * 0.05f, abs(sin(t * 3f))) }
  }
}
