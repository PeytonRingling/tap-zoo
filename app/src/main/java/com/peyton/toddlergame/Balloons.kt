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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
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

/** Shared body for the non-round balloon shapes: string, soft shadow, glossy fill, rim light, knot. [knotY] = where the string ties. */
private fun DrawScope.shapedBalloon(body: Path, c: Offset, r: Float, fill: Brush, rim: Color, knotY: Float, t: Float, phase: Float, string: Boolean = true) {
  val knot = Offset(c.x, knotY)
  if (string) {
    val p = Path(); p.moveTo(knot.x, knot.y)
    p.cubicTo(knot.x + sin(t * 2f + phase) * r * 0.3f, knot.y + r * 0.8f, knot.x - sin(t * 2.3f + phase) * r * 0.3f, knot.y + r * 1.6f, knot.x + sin(t * 1.7f + phase) * r * 0.22f, knot.y + r * 2.4f)
    drawPath(p, Color(0xFF9A93B8).copy(alpha = 0.8f), style = Stroke(max(2f, r * 0.03f), cap = StrokeCap.Round))
  }
  softShadow(Rect(c.x - r * 0.6f + r * 0.25f, c.y - r * 0.5f + r * 0.35f, c.x + r * 0.6f + r * 0.25f, c.y + r * 1.05f + r * 0.35f), r * 0.6f, r * 0.35f, Pal.shadow.copy(alpha = 0.13f))
  drawPath(body, fill)
  drawPath(body, rim.copy(alpha = 0.45f), style = Stroke(max(1.5f, r * 0.035f)))
  drawPath(Path().apply { moveTo(c.x - r * 0.13f, knotY + r * 0.16f); lineTo(c.x + r * 0.13f, knotY + r * 0.16f); lineTo(c.x, knotY - r * 0.02f); close() }, rim)
}

fun DrawScope.drawHeartBalloon(c: Offset, r: Float, color: Color, t: Float, phase: Float) {
  val tip = c.y + r * 1.05f
  val body = Path().apply {
    moveTo(c.x, tip)
    cubicTo(c.x - r * 1.75f, c.y - r * 0.05f, c.x - r * 0.95f, c.y - r * 1.45f, c.x, c.y - r * 0.6f)
    cubicTo(c.x + r * 0.95f, c.y - r * 1.45f, c.x + r * 1.75f, c.y - r * 0.05f, c.x, tip); close()
  }
  shapedBalloon(body, c, r, Brush.radialGradient(listOf(color.lighten(0.5f), color, color.darken(0.3f)), c + Offset(-r * 0.4f, -r * 0.55f), r * 1.8f), color.darken(0.25f), tip, t, phase)
  rotate(-30f, c + Offset(-r * 0.55f, -r * 0.6f)) { drawOval(Color.White.copy(alpha = 0.6f), c + Offset(-r * 0.75f, -r * 0.85f), Size(r * 0.28f, r * 0.5f)) }
}

/** Foil star: a metallic sheen slides across it. */
fun DrawScope.drawStarBalloon(c: Offset, r: Float, color: Color, t: Float, phase: Float, sparkles: Boolean = false) {
  val outer = r * 1.2f; val inner = r * 0.6f
  val body = Path()
  for (i in 0 until 10) { val rr = if (i % 2 == 0) outer else inner; val a = -PI / 2 + i * PI / 5
    val x = c.x + (cos(a) * rr).toFloat(); val y = c.y + (sin(a) * rr).toFloat(); if (i == 0) body.moveTo(x, y) else body.lineTo(x, y) }
  body.close()
  val sweep = (sin(t * 0.8f + phase) * 0.5f + 0.5f) * r * 1.6f - r * 0.8f
  val fill = Brush.linearGradient(listOf(color.darken(0.25f), color.lighten(0.25f), Color.White.copy(alpha = 0.95f).compositeOn(color), color.lighten(0.15f), color.darken(0.3f)),
    c + Offset(-outer + sweep, -outer), c + Offset(outer + sweep, outer))
  shapedBalloon(body, c, r, fill, color.darken(0.35f), c.y + inner, t, phase)
  if (sparkles) for (i in 0 until 3) { val a = t * 1.3f + i * 2.1f + phase; val k = (sin(t * 4f + i * 1.7f) * 0.5f + 0.5f)
    drawStar(c + Offset(cos(a) * r * 1.45f, sin(a) * r * 1.25f), r * (0.08f + 0.1f * k), Color.White.copy(alpha = 0.5f + 0.5f * k), t * 90f) }
}

private fun Color.compositeOn(under: Color) = Color(red * alpha + under.red * (1 - alpha), green * alpha + under.green * (1 - alpha), blue * alpha + under.blue * (1 - alpha))

/** The giant: a striped rainbow balloon. */
fun DrawScope.drawRainbowBalloon(c: Offset, r: Float, t: Float, phase: Float) {
  val body = Path().apply {
    moveTo(c.x, c.y - r * 1.1f)
    cubicTo(c.x + r * 1.25f, c.y - r * 1.1f, c.x + r * 1.15f, c.y + r * 0.5f, c.x + r * 0.1f, c.y + r * 1.08f)
    lineTo(c.x - r * 0.1f, c.y + r * 1.08f)
    cubicTo(c.x - r * 1.15f, c.y + r * 0.5f, c.x - r * 1.25f, c.y - r * 1.1f, c.x, c.y - r * 1.1f); close()
  }
  shapedBalloon(body, c, r, Brush.linearGradient(RAINBOW, c + Offset(-r, -r * 1.1f), c + Offset(r, r * 1.1f)), Color(0xFF9B5DE5), c.y + r * 1.08f, t, phase)
  drawPath(body, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), c + Offset(-r * 0.4f, -r * 0.5f), r * 1.2f))
  rotate(-28f, c + Offset(-r * 0.42f, -r * 0.5f)) { drawOval(Color.White.copy(alpha = 0.6f), c + Offset(-r * 0.58f, -r * 0.85f), Size(r * 0.3f, r * 0.6f)) }
}

/** A round balloon with a friendly animal face: 0 bear ears, 1 cat ears, 2 bunny ears. */
fun DrawScope.drawFaceBalloon(c: Offset, r: Float, color: Color, t: Float, phase: Float, face: Int, blink: Float) {
  val ear = color.darken(0.12f)
  for (s in listOf(-1f, 1f)) when (face) {
    0 -> { drawCircle(ear, r * 0.36f, c + Offset(s * r * 0.72f, -r * 0.9f)); drawCircle(color.lighten(0.35f), r * 0.18f, c + Offset(s * r * 0.72f, -r * 0.9f)) }
    1 -> drawPath(Path().apply { moveTo(c.x + s * r * 0.95f, c.y - r * 0.45f); lineTo(c.x + s * r * 0.85f, c.y - r * 1.45f); lineTo(c.x + s * r * 0.2f, c.y - r * 1.0f); close() }, ear)
    else -> rotate(s * 12f, c + Offset(s * r * 0.4f, -r * 0.9f)) { drawOval(ear, c + Offset(s * r * 0.4f - r * 0.2f, -r * 1.9f), Size(r * 0.4f, r * 1.1f))
      drawOval(Color(0xFFFFC2D6), c + Offset(s * r * 0.4f - r * 0.1f, -r * 1.75f), Size(r * 0.2f, r * 0.8f)) }
  }
  drawBalloon(c, r, color, t, phase)
  val ink = Color(0xFF2D2A4A)
  for (s in listOf(-1f, 1f)) {
    val e = c + Offset(s * r * 0.33f, -r * 0.2f); val h = r * 0.24f * (1 - blink).coerceAtLeast(0.15f)
    drawOval(ink, Offset(e.x - r * 0.1f, e.y - h / 2), Size(r * 0.2f, h))
    if (blink < 0.5f) drawCircle(Color.White, r * 0.04f, e + Offset(-r * 0.03f, -r * 0.05f))
    drawCircle(Color(0xFFFF6F91).copy(alpha = 0.45f), r * 0.12f, c + Offset(s * r * 0.55f, r * 0.12f))
  }
  drawArc(ink, 20f, 140f, false, c + Offset(-r * 0.25f, -r * 0.05f), Size(r * 0.5f, r * 0.35f), style = Stroke(r * 0.07f, cap = StrokeCap.Round))
}

internal enum class BKind { ROUND, HEART, STAR, FACE, ANIMAL, GOLDEN, GIANT, MINI }

internal class Balloon(var x: Float, var y: Float, val r: Float, val color: Color, val phase: Float, var vx: Float, var vy: Float, val symbol: Int, val label: String,
                       val kind: BKind = BKind.ROUND, val critter: Critter = Critter.CAT, val face: Int = 0) {
  var born = 0f; var leaving = false
  /** Time the squash started (tap), -1 while intact. It bursts [BalloonModel.SQUASH] s later. */
  var popAt = -1f
  val live get() = !leaving && popAt < 0f
}
internal class SoapBubble(var x: Float, var y: Float, val r: Float, val phase: Float, var vy: Float) { var born = 0f }
/** An animal that was inside a see-through balloon, floating down on a little parachute. */
internal class Parachute(var x: Float, var y: Float, val critter: Critter, val t0: Float, val r: Float, val color: Color)
internal class PhotoFloat(var x: Float, var y: Float, val img: androidx.compose.ui.graphics.ImageBitmap, val t0: Float, val s: Float)
internal class Flash(val x: Float, val y: Float, val r: Float, val t0: Float, val color: Color)

/** Balloons: a bright sky that always has 6-12 balloons to pop (a gentle stream rises from the bottom), or with the parent
 *  option off, classic "only what you make, they stay until popped". Touching empty sky blows one up under the finger.
 *  Every pop squashes then bursts with a cartoon POP and a note that climbs the music's scale while pops come quickly;
 *  five in a row get a little ta-da. Surprises: animals in see-through balloons (they parachute down), a golden star,
 *  and now and then a giant rainbow balloon that bursts into minis. Nothing to win or lose, no score. */
internal class BalloonModel(private val sink: SoundSink = GameSound, private val rnd: Random = Random.Default,
                            private val popSound: (Float) -> FloatArray = Samples::balloonPop,
                            val stream: Boolean = Prefs.balloonStream, private val symbols: Boolean = Prefs.balloonSymbols) {
  companion object {
    const val SQUASH = 0.06f; const val COMBO_GAP = 1.2f
    const val MIN_LIVE = 6; const val MAX_LIVE = 12; const val HARD_CAP = 16; const val MINIS = 7
    const val GIANT_MIN = 25f; const val GIANT_MAX = 40f
    const val IDLE_HINT = 5f; const val IDLE_SHOW = 20f; const val IDLE_GIVE_UP = 90f
    const val MAX_PARACHUTES = 3; const val MAX_ANIMAL_BALLOONS = 2
  }
  val balloons = ArrayList<Balloon>(); val bubbles = ArrayList<SoapBubble>(); val parachutes = ArrayList<Parachute>()
  val photos = ArrayList<PhotoFloat>(); val flashes = ArrayList<Flash>(); val ribbons = ArrayList<Float>()
  val parts = Particles(600).apply { gravity = 700f }
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = 0f; var pops = 0; var lastSpawn = 0f; var emptySince = 0f; var started = false
  /** Pops in the current quick run (resets after [COMBO_GAP] s without a pop). Never shown. */
  var streak = 0; private set
  private var lastPopT = -10f; private var jingleUntil = -1f
  var nextGiant = 0f; private set
  private var lastStream = 0f; private var streamGap = 1.5f
  var shakeUntil = -1f; private set
  // idle attract: a wiggle (+ a soft squeak the first few times), then a tiny auto-show
  var wiggle: Balloon? = null; private set
  var wiggleAt = -10f; private set
  private var squeaks = 0; private var shows = 0; private var lastShow = -100f
  private val autoPops = ArrayList<Float>()
  private val labels = ('A'..'Z').map { it.toString() } + (1..9).map { it.toString() }
  private var colorIdx = rnd.nextInt(9)
  val cap get() = (w * h / (min(w, h) * min(w, h) * 0.12f)).toInt().coerceIn(8, 16)
  /** Most the stream keeps on screen: 12, fewer on a squarer screen (Fold inner: 10) so balloons stay big. */
  val targetMax get() = min(MAX_LIVE, cap)
  fun liveCount() = balloons.count { it.live }

  private fun pickKind(): BKind {
    val n = rnd.nextInt(100)
    return when {
      n < 12 && balloons.count { it.live && it.kind == BKind.ANIMAL } < MAX_ANIMAL_BALLOONS -> BKind.ANIMAL
      n in 12..15 && balloons.none { it.live && it.kind == BKind.GOLDEN } -> BKind.GOLDEN
      n < 26 -> BKind.HEART
      n < 36 -> BKind.STAR
      n < 48 -> BKind.FACE
      else -> BKind.ROUND
    }
  }

  fun make(x: Float, y: Float, rising: Boolean = false, kind: BKind = pickKind()): Balloon {
    val m = min(w, h)
    val r = when (kind) { BKind.GIANT -> m * 0.22f; BKind.MINI -> m * 0.06f; BKind.GOLDEN -> m * 0.11f; else -> m * (0.095f + rnd.nextFloat() * 0.05f) }
    colorIdx = (colorIdx + 1 + rnd.nextInt(3)) % Pal.vivid.size   // never the same color twice in a row
    val color = when (kind) { BKind.GOLDEN -> Color(0xFFFFC83D); BKind.ANIMAL -> Color(0xFFBFE6FF); else -> Pal.vivid[colorIdx] }
    val sym = if (!symbols || kind != BKind.ROUND) 0 else when (rnd.nextInt(10)) { 0 -> 1; 1 -> 2; 2 -> 3; 3, 4 -> 4; else -> 0 }
    val cx = x.coerceIn(r, max(r, w - r)); val cy = y.coerceIn(r * 1.2f, max(r * 1.2f, h - r * 1.3f))
    val b = Balloon(cx, if (rising) h + r * 1.5f else cy, r, color, rnd.nextFloat() * 6f,
      (rnd.nextFloat() - 0.5f) * m * 0.05f, if (rising) -h * 0.35f else -m * (0.01f + rnd.nextFloat() * 0.02f), sym, labels.random(rnd),
      kind, Critter.entries.random(rnd), rnd.nextInt(3)).also { it.born = t }
    balloons += b
    trim(if (stream) targetMax + 2 else cap)
    lastSpawn = t
    return b
  }
  /** Over [limit] live balloons: the oldest ordinary one drifts off the top (specials stay, they're the fun ones). */
  private fun trim(limit: Int) {
    while (liveCount() > min(limit, HARD_CAP)) {
      val live = balloons.filter { it.live }
      (live.filter { it.kind !in setOf(BKind.GIANT, BKind.ANIMAL, BKind.GOLDEN) }.ifEmpty { live }).minByOrNull { it.born }?.leaving = true
    }
  }
  fun bubble(x: Float, y: Float) { val m = min(w, h)
    bubbles += SoapBubble(x + (rnd.nextFloat() - 0.5f) * m * 0.2f, y + (rnd.nextFloat() - 0.5f) * m * 0.1f, m * (0.035f + rnd.nextFloat() * 0.035f), rnd.nextFloat() * 6f, -m * (0.08f + rnd.nextFloat() * 0.06f)).also { it.born = t }
    if (bubbles.size > 10) bubbles.removeAt(0) }

  private fun start() {
    started = true
    if (stream) {
      // A full, busy sky from the first frame: 7 balloons spread across the screen.
      for (i in 0 until 7) make(w * (0.08f + 0.84f * (i + 0.5f) / 7f) + (rnd.nextFloat() - 0.5f) * w * 0.05f, h * (0.25f + rnd.nextFloat() * 0.5f),
        kind = if (i == 3) BKind.ANIMAL else pickKind().let { when (it) { BKind.GOLDEN -> BKind.STAR; BKind.ANIMAL -> BKind.ROUND; else -> it } })
      nextGiant = GIANT_MIN + rnd.nextFloat() * (GIANT_MAX - GIANT_MIN)
    } else { make(w * 0.5f, h * 0.5f); make(w * 0.28f, h * 0.62f); make(w * 0.72f, h * 0.4f) }
    balloons.forEach { it.born = -1f }
  }

  fun step(dtIn: Float) {
    if (w < 2f || h < 2f) return
    val dt = if (dtIn.isNaN()) 0f else dtIn.coerceIn(0f, 0.1f)
    if (!started) start()
    balloons.filter { it.popAt >= 0f && t - it.popAt >= SQUASH }.forEach { burst(it) }
    val m = min(w, h)
    if (stream) {
      val live = liveCount()
      if ((live < MIN_LIVE && t - lastStream > 0.45f) || (live < targetMax && t - lastStream > streamGap)) {
        make(w * (0.12f + rnd.nextFloat() * 0.76f), h * 0.5f, rising = true); lastStream = t; streamGap = 1.2f + rnd.nextFloat() * 0.8f
      }
      if (t >= nextGiant) {
        if (balloons.none { it.kind == BKind.GIANT && !it.leaving }) make(w * (0.3f + rnd.nextFloat() * 0.4f), h * 0.5f, rising = true, kind = BKind.GIANT)
        nextGiant = t + GIANT_MIN + rnd.nextFloat() * (GIANT_MAX - GIANT_MIN)
      }
    } else {
      // Classic: never leave the toddler staring at an empty sky: a fresh balloon floats up and settles.
      if (balloons.none { !it.leaving }) { if (emptySince < 0f) emptySince = t; if (t - emptySince > 2.5f) { make(w * (0.25f + rnd.nextFloat() * 0.5f), h * 0.5f, rising = true); emptySince = -1f } } else emptySince = -1f
    }
    for (b in balloons) {
      if (b.popAt >= 0f) continue
      if (b.leaving) { b.vy -= h * 0.4f * dt; b.y += b.vy * dt; continue }
      if (b.kind == BKind.MINI) { val k = min(1f, dt * 2.2f); b.vx -= b.vx * k * 0.8f; b.vy += (-(m * 0.015f) - b.vy) * k }   // burst outward, then settle
      if (b.vy < -m * 0.03f && b.y < h * 0.65f) b.vy += (-(m * 0.015f) - b.vy) * min(1f, dt * 1.6f)   // risers slow to a hover
      b.x += (b.vx + sin(t * 0.7f + b.phase) * m * 0.025f) * dt; b.y += (b.vy + cos(t * 0.9f + b.phase) * m * 0.02f) * dt
      if (b.x < b.r) { b.x = b.r; b.vx = abs(b.vx) }; if (b.x > w - b.r) { b.x = w - b.r; b.vx = -abs(b.vx) }
      if (b.y < b.r * 1.2f) { b.y = b.r * 1.2f; b.vy = abs(b.vy) * 0.6f + m * 0.01f }
      if (b.y > h - b.r * 1.3f && b.vy > 0f && b.y < h + b.r) b.vy = -abs(b.vy)
    }
    // soft separation so balloons don't stack on one spot
    for (i in balloons.indices) for (j in i + 1 until balloons.size) { val a = balloons[i]; val c = balloons[j]; if (!a.live || !c.live) continue
      val dx = c.x - a.x; val dy = c.y - a.y; val d = hypot(dx, dy); val md = (a.r + c.r) * 0.95f
      if (d in 0.01f..md) { val p = (md - d) / d * 0.5f * min(1f, dt * 4f); a.x -= dx * p; a.y -= dy * p; c.x += dx * p; c.y += dy * p } }
    balloons.removeAll { it.y < -it.r * 4f }
    for (u in bubbles) { u.y += u.vy * dt; u.x += sin(t * 2f + u.phase) * u.r * 1.2f * dt }; bubbles.removeAll { it.y < -it.r * 2f }
    for (p in parachutes) { p.y += h * 0.075f * dt; p.x += sin((t - p.t0) * 1.6f) * p.r * 0.5f * dt }
    parachutes.removeAll { it.y > h + it.r * 3f || t - it.t0 > 20f }
    for (p in photos) { p.y -= h * 0.06f * dt; p.x += sin((t - p.t0) * 1.5f) * p.s * 0.15f * dt }; photos.removeAll { it.y < -it.s * 2f }
    flashes.removeAll { t - it.t0 > 0.35f }
    ribbons.removeAll { t - it > 1.4f }
    idle()
    parts.step(dt)
  }

  private fun idle() {
    val idle = t - lastTouch
    if (idle >= IDLE_GIVE_UP) { autoPops.clear(); return }   // nobody's there: stay quiet
    if (idle >= IDLE_HINT && t - wiggleAt >= 4f) {
      wiggle = balloons.filter { it.live }.maxByOrNull { it.r }
      if (wiggle != null) { wiggleAt = t; if (squeaks < 3) { squeaks++; sink.play(Sfx.squeak, 0.22f) } }
    }
    if (idle >= IDLE_SHOW && shows < 2 && t - lastShow >= IDLE_SHOW) { shows++; lastShow = t; autoPops += t; autoPops += t + 0.45f }
    while (autoPops.isNotEmpty() && t >= autoPops[0]) {
      autoPops.removeAt(0)
      balloons.filter { it.live && it.kind != BKind.GIANT }.ifEmpty { null }?.random(rnd)?.let { pop(it, auto = true) }
    }
  }

  /** Squash now (with the sound, so it feels instant), burst [SQUASH] s later. */
  fun pop(b: Balloon, auto: Boolean = false) {
    if (!b.live) return
    b.popAt = t; pops++
    val m = min(w, h)
    val size01 = when (b.kind) { BKind.MINI -> 0f; BKind.GIANT -> 1f; else -> (b.r / m - 0.095f) / 0.05f }
    sink.play(popSound(size01.coerceIn(0f, 1f)), if (auto) 0.55f else 0.85f)
    if (auto) sink.play(Sfx.comboNotes[rnd.nextInt(5)], 0.22f)
    else {
      streak = if (t - lastPopT <= COMBO_GAP) streak + 1 else 1
      lastPopT = t
      sink.play(Sfx.comboNotes[(streak - 1) % Sfx.comboNotes.size], 0.3f)
      sink.haptic(true)
      if (streak % 10 == 5 && t >= jingleUntil) { sink.play(Sfx.tada, 0.45f); ribbons += t; jingleUntil = t + 1.5f }
    }
    when (b.kind) {
      BKind.ANIMAL -> sink.animal(b.critter, 0.9f)
      BKind.GOLDEN -> sink.play(Sfx.sparkle, 0.45f)
      BKind.GIANT -> sink.play(Sfx.fanfare, 0.5f)
      else -> if (Photos.images.isNotEmpty() && pops % 4 == 3 && photos.size < 2) { photos += PhotoFloat(b.x, b.y, Photos.images.random(rnd).second, t, m * 0.3f); sink.play(Sfx.chime, 0.4f) }
    }
  }

  private fun burst(b: Balloon) {
    balloons.remove(b)
    val m = min(w, h)
    val k = if (parts.list.size > 400) 0.5f else 1f   // storm of pops: lighter bursts, keeps the Fold smooth
    fun n(x: Int) = max(1, (x * k).toInt())
    flashes += Flash(b.x, b.y, b.r, t, b.color)
    val scrap = if (b.kind == BKind.ANIMAL) listOf(Color.White, Color(0xFFBFE6FF)) else listOf(b.color, b.color.darken(0.1f))
    parts.burst(b.x, b.y, n(if (b.kind == BKind.MINI) 5 else 12), scrap, kind = 5, speed = b.r * 9f, size = b.r * 0.16f, up = 50f, decay = 1.6f)
    parts.burst(b.x, b.y, n(if (b.kind == BKind.MINI) 10 else 30), Pal.vivid, kind = 4, speed = b.r * 11f, size = min(b.r, m * 0.13f) * 0.1f, up = b.r * 3f, decay = 0.55f)
    parts.burst(b.x, b.y, n(8), listOf(Color.White, Pal.sun), kind = 1, speed = b.r * 6f, size = min(b.r, m * 0.13f) * 0.13f, up = 100f, decay = 1.2f)
    when (b.kind) {
      BKind.ANIMAL -> { parachutes += Parachute(b.x, b.y, b.critter, t, b.r * 0.5f, Pal.vivid[(colorIdx + 4) % Pal.vivid.size]); if (parachutes.size > MAX_PARACHUTES) parachutes.removeAt(0) }
      BKind.GOLDEN -> parts.burst(b.x, b.y, n(24), listOf(Color(0xFFFFD43B), Color(0xFFFFF3B0), Color(0xFFFFB800)), kind = 1, speed = b.r * 10f, size = b.r * 0.2f, up = 150f, decay = 0.8f)
      BKind.GIANT -> {
        shakeUntil = t + 0.25f
        parts.burst(b.x, b.y, n(40), RAINBOW, kind = 4, speed = b.r * 8f, size = m * 0.014f, up = b.r * 2f, decay = 0.45f)
        for (i in 0 until MINIS) {
          val a = i * 2 * PI.toFloat() / MINIS + rnd.nextFloat() * 0.4f
          val mini = Balloon(b.x, b.y, m * 0.06f, Pal.vivid[(colorIdx + i) % Pal.vivid.size], rnd.nextFloat() * 6f, cos(a) * m * 0.9f, sin(a) * m * 0.9f, 0, "", BKind.MINI)
          mini.born = t; balloons += mini
        }
        trim(HARD_CAP)
      }
      else -> {}
    }
  }

  fun popBubble(u: SoapBubble) { bubbles.remove(u); sink.play(Sfx.bubblePop, 0.45f); sink.haptic()
    parts.burst(u.x, u.y, 8, listOf(Color.White, Color(0xFFBFE6FF), Color(0xFFFFD6F5)), kind = 0, speed = u.r * 8f, size = u.r * 0.12f, up = 20f, decay = 2f) }

  fun touched() { lastTouch = t; squeaks = 0; shows = 0; lastShow = -100f; autoPops.clear(); wiggle = null }

  fun hit(p: Offset, reach: Float = 1.3f) = balloons.filter { it.live && hypot(it.x - p.x, (it.y - p.y) * 0.85f) < it.r * reach }.minByOrNull { hypot(it.x - p.x, it.y - p.y) }

  fun tap(p: Offset) {
    if (!p.x.isFinite() || !p.y.isFinite()) return
    touched()
    val b = hit(p)
    if (b != null) { pop(b); return }
    val u = bubbles.firstOrNull { hypot(it.x - p.x, it.y - p.y) < it.r * 1.6f }
    if (u != null) { popBubble(u); return }
    if (t - lastSpawn < 0.12f || w < 2f) return
    make(p.x, p.y); sink.play(Sfx.inflate, 0.4f); sink.haptic()
    if (rnd.nextInt(3) == 0) repeat(1 + rnd.nextInt(2)) { bubble(p.x, p.y) }
  }
  /** A finger sliding across the sky pops what it touches (but doesn't inflate). */
  fun drag(p: Offset) { if (!p.x.isFinite() || !p.y.isFinite()) return; touched(); hit(p, 0.9f)?.let { pop(it) } }
}

private fun DrawScope.drawParachute(p: Parachute, t: Float) {
  val age = t - p.t0; val open = (age / 0.4f).coerceIn(0f, 1f); val c = Offset(p.x, p.y)
  rotate(sin(age * 1.6f) * 10f, c + Offset(0f, -p.r * 2.6f)) {
    val cw = p.r * 2.6f * (0.3f + 0.7f * open); val top = c + Offset(0f, -p.r * 3.3f)
    for (s in listOf(-1f, 0f, 1f)) drawLine(Color(0xFF9A93B8), c + Offset(s * p.r * 0.5f, -p.r * 0.6f), top + Offset(s * cw * 0.48f, p.r * 0.35f), max(1.5f, p.r * 0.05f))
    val rect = Rect(top.x - cw / 2, top.y - cw * 0.45f, top.x + cw / 2, top.y + cw * 0.45f)
    for (i in 0 until 4) drawArc(if (i % 2 == 0) p.color else Color.White, 180f + i * 45f, 45f, true, rect.topLeft, rect.size)
    drawCritter(p.critter, c, p.r, Offset(0f, 0.4f), 0f, 0.5f + 0.5f * abs(sin(age * 3f)))
  }
}

/** Comic "POP!" starburst, no text: spiky, quick, then gone. */
private fun DrawScope.drawStarburst(c: Offset, r: Float, k: Float) {
  if (k >= 1f) return
  val grow = (k / 0.35f).coerceAtMost(1f); val a = (1f - k).coerceIn(0f, 1f)
  val p = Path()
  for (i in 0 until 20) { val rr = r * (if (i % 2 == 0) 1.35f else 0.8f) * (0.4f + 0.6f * grow); val ang = i * PI / 10
    val x = c.x + (cos(ang) * rr).toFloat(); val y = c.y + (sin(ang) * rr).toFloat(); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
  p.close()
  drawPath(p, Color(0xFFFFF6B8).copy(alpha = 0.7f * a)); drawPath(p, Color.White.copy(alpha = 0.95f * a), style = Stroke(max(2f, r * 0.06f)))
}

@Composable
fun BalloonsScreen() { BalloonsScreenFor(remember { BalloonModel() }) }

@Composable
internal fun BalloonsScreenFor(m: BalloonModel) {
  val time = rememberTicker { dt, t -> m.t = t; m.step(dt) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }
    .multiTouch(Unit, onDown = { _, p -> m.tap(p) }, onMove = { _, p -> m.drag(p) })) {
    val t = time.value; m.w = size.width; m.h = size.height; val mm = min(size.width, size.height)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF3FA9F5), Color(0xFF7CC8FA), Color(0xFFC6E9FF))))
    val sunC = Offset(size.width * 0.85f, size.height * 0.13f)
    drawCircle(Brush.radialGradient(listOf(Color(0x99FFF6C9), Color(0x00FFF6C9)), sunC, mm * 0.28f), mm * 0.28f, sunC)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF8D6), Color(0xFFFFD45C)), sunC - Offset(mm * 0.03f, mm * 0.03f), mm * 0.12f), mm * 0.085f, sunC)
    for (i in 0 until 3) softCloud(Offset(((t * 9f + i * size.width / 2.2f) % (size.width * 1.5f)) - size.width * 0.25f, size.height * (0.12f + 0.1f * i)), mm * 0.06f, 0.7f)
    for (i in 0 until 3) softCloud(Offset(((t * 18f + i * size.width / 1.6f + 300f) % (size.width * 1.5f)) - size.width * 0.25f, size.height * (0.55f + 0.14f * i)), mm * 0.1f, 0.95f)
    val shake = if (t < m.shakeUntil) Offset(sin(t * 90f), cos(t * 77f)) * (3.dp.toPx() * ((m.shakeUntil - t) / 0.25f)) else Offset.Zero
    translate(shake.x, shake.y) {
      for (rb in m.ribbons) { val k = ((t - rb) / 1.4f).coerceIn(0f, 1f); val a = (if (k < 0.2f) k / 0.2f else (1f - k) / 0.8f) * 0.55f
        val rr = size.width * (0.45f + 0.15f * k); val c = Offset(size.width / 2f, size.height * 0.62f)
        RAINBOW.forEachIndexed { i, col -> val r = rr - i * mm * 0.022f
          drawArc(col.copy(alpha = a), 180f, 180f, false, c - Offset(r, r), Size(r * 2, r * 2), style = Stroke(mm * 0.022f)) } }
      for (u in m.bubbles) { val g = ((t - u.born) / 0.25f).coerceIn(0f, 1f); val r = u.r * g * (1 + 0.04f * sin(t * 5f + u.phase)); val c = Offset(u.x, u.y)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.05f), Color(0xFFBFE6FF).copy(alpha = 0.25f), Color(0xFFFFC6F0).copy(alpha = 0.55f)), c, r), r, c)
        drawCircle(Color.White.copy(alpha = 0.7f), r, c, style = Stroke(max(1.5f, r * 0.05f)))
        drawOval(Color.White.copy(alpha = 0.8f), c + Offset(-r * 0.55f, -r * 0.6f), Size(r * 0.4f, r * 0.25f)) }
      for (b in m.balloons.sortedBy { it.r }) {
        val age = t - b.born
        val grow = if (b.born < 0f || b.y > m.h || b.kind == BKind.MINI) 1f else (age / 0.3f).coerceIn(0f, 1f).let { k -> (0.15f + 0.85f * k) * (1f + 0.22f * sin(k * PI.toFloat())) }
        val sq = 0.025f * sin(t * 2.4f + b.phase)
        // squash before the burst: wider and flatter, like it's about to give
        val pk = if (b.popAt >= 0f) ((t - b.popAt) / BalloonModel.SQUASH).coerceIn(0f, 1f) else 0f
        val wig = if (b === m.wiggle) (t - m.wiggleAt).let { a -> if (a < 0.8f) sin(a * 28f) * 9f * (1f - a / 0.8f) else 0f } else 0f
        val pivot = Offset(b.x, b.y + b.r)
        rotate(sin(t * 0.9f + b.phase) * 6f + wig, pivot) { scale((1 + sq + 0.22f * pk) * grow, (1 - sq - 0.16f * pk) * grow, pivot) {
          val c = Offset(b.x, b.y)
          when (b.kind) {
            BKind.HEART -> drawHeartBalloon(c, b.r, b.color, t, b.phase)
            BKind.STAR -> drawStarBalloon(c, b.r, b.color, t, b.phase)
            BKind.GOLDEN -> drawStarBalloon(c, b.r, b.color, t, b.phase, sparkles = true)
            BKind.FACE -> drawFaceBalloon(c, b.r, b.color, t, b.phase, b.face, if ((t + b.phase) % 4f < 0.15f) 1f else 0f)
            BKind.GIANT -> drawRainbowBalloon(c, b.r, t, b.phase)
            BKind.ANIMAL -> { drawCritter(b.critter, c + Offset(0f, b.r * 0.05f), b.r * 0.52f, Offset(sin(t + b.phase) * 0.6f, 0f), 0f, 0.3f)
              drawBalloon(c, b.r, b.color.copy(alpha = 0.38f), t, b.phase) }
            else -> drawBalloon(c, b.r, b.color, t, b.phase, b.symbol, b.label, string = b.kind != BKind.MINI || age > 0.6f)
          }
        } }
      }
      for (f in m.flashes) { val k = (t - f.t0) / 0.35f
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.6f * (1 - k)), Color.Transparent), Offset(f.x, f.y), f.r * (0.6f + k)), f.r * (0.6f + k), Offset(f.x, f.y))
        drawStarburst(Offset(f.x, f.y), min(f.r, mm * 0.14f), (t - f.t0) / 0.25f)
        drawCircle(f.color.copy(alpha = (0.6f * (1 - k)).coerceIn(0f, 1f)), f.r * (0.7f + k * 0.9f), Offset(f.x, f.y), style = Stroke(f.r * 0.08f * (1 - k) + 1f)) }
      for (p in m.parachutes) drawParachute(p, t)
      for (p in m.photos) { val age = t - p.t0; val sc = (age / 0.45f).coerceIn(0f, 1f); val c = Offset(p.x, p.y)
        scale(sc, sc, c) { drawLine(Color(0xFF9A93B8), c + Offset(0f, -p.s * 0.5f), c + Offset(0f, -p.s * 0.85f), 3f)
          drawBalloon(c + Offset(0f, -p.s * 1.05f), p.s * 0.2f, Pal.pink, t, 0f, 2, string = false)
          drawFramedPhoto(p.img, c, p.s, sin(age * 1.3f) * 6f) } }
      m.parts.draw(this)
    }
    if (t - m.lastTouch > BalloonModel.IDLE_HINT && t - m.lastTouch < BalloonModel.IDLE_GIVE_UP)
      (m.wiggle?.takeIf { it.live } ?: m.balloons.filter { it.live }.maxByOrNull { it.r })?.let { drawHand(Offset(it.x, it.y), mm * 0.05f, abs(sin(t * 3f))) }
  }
}
