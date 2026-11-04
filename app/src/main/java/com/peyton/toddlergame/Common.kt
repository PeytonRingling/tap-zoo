package com.peyton.toddlergame

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlin.random.Random

val RAINBOW = listOf(0xFFFF6B6B, 0xFFFF9F43, 0xFFFFD43B, 0xFF94D82D, 0xFF38D9A9, 0xFF4CC9F0, 0xFF5B7CFA, 0xFF9B5DE5).map { Color(it) }

enum class Mode { BALLOONS, ANIMALS, AQUARIUM, BUBBLES, VEHICLES, GARDEN, DRUMS, PEEKABOO, PAINT, MUSIC, SKY, BEDTIME }

object Prefs {
  private var sp: android.content.SharedPreferences? = null
  fun init(c: Context) {
    sp = c.getSharedPreferences("toddler", Context.MODE_PRIVATE)
    // Music volume used to be the single volume: pin it once so changing sound effects later doesn't move the music.
    sp?.let { p -> if (!p.contains("mvol")) p.edit().putInt("mvol", volume).apply() }
  }
  var volume: Int get() = sp?.getInt("vol", 4) ?: 4; set(v) { sp?.edit()?.putInt("vol", v)?.apply() }
  var haptics: Boolean get() = sp?.getBoolean("hap", true) ?: true; set(v) { sp?.edit()?.putBoolean("hap", v)?.apply() }
  var enabled: Set<Mode>
    get() = sp?.getStringSet("modes", null)?.mapNotNull { runCatching { Mode.valueOf(it) }.getOrNull() }?.toSet()?.ifEmpty { null } ?: Mode.entries.toSet()
    set(v) { sp?.edit()?.putStringSet("modes", v.map { it.name }.toSet())?.apply() }
  /** Where music plays. First read migrates the old `mmus`/`bmus` switches (see [MusicDirector.migrate]); they're no longer written. */
  var musicSlots: Set<MusicSlot>
    get() { val p = sp ?: return MusicSlot.entries.toSet()
      return MusicDirector.migrate(p.getStringSet("musslots", null),
        if (p.contains("mmus")) p.getBoolean("mmus", true) else null, if (p.contains("bmus")) p.getBoolean("bmus", true) else null) }
    set(v) { sp?.edit()?.putStringSet("musslots", v.map { it.name }.toSet())?.apply() }
  /** Music volume 0..5. Until a parent sets it, it follows the old single volume so loudness doesn't change on update. */
  var musicVolume: Int get() = sp?.getInt("mvol", volume) ?: volume; set(v) { sp?.edit()?.putInt("mvol", v)?.apply() }
  var bedtimeMinutes: Int get() = sp?.getInt("bedmin", 15) ?: 15; set(v) { sp?.edit()?.putInt("bedmin", v)?.apply() }
  var kidLock: Boolean get() = sp?.getBoolean("klock", true) ?: true; set(v) { sp?.edit()?.putBoolean("klock", v)?.apply() }
  /** Balloons: a gentle stream keeps 5-7 on screen (default) vs classic "only what you make, they stay until popped". */
  var balloonStream: Boolean get() = sp?.getBoolean("bstream", true) ?: true; set(v) { sp?.edit()?.putBoolean("bstream", v)?.apply() }
  fun volumeGain(v: Int) = v / 5f
}

object Fx {
  var vib: Vibrator? = null
  var haptics = true
  fun tap(strong: Boolean = false) {
    Parade.onTap()
    if (!haptics) return
    val v = vib ?: return
    try {
      if (Build.VERSION.SDK_INT >= 29) v.vibrate(VibrationEffect.createPredefined(if (strong) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK))
      else v.vibrate(VibrationEffect.createOneShot(if (strong) 40 else 20, 200))
    } catch (_: Exception) {}
  }
}

/** Frame loop: calls [step] every vsync and returns elapsed seconds as state (forces redraw). */
@Composable
fun rememberTicker(step: (dt: Float, t: Float) -> Unit = { _, _ -> }): State<Float> {
  val time = remember { mutableFloatStateOf(0f) }
  val cb = rememberUpdatedState(step)
  LaunchedEffect(Unit) {
    var last = -1L; var t = 0f
    while (true) withFrameNanos { n ->
      val dt = if (last < 0) 0f else ((n - last) / 1e9f).coerceAtMost(0.05f)
      last = n; t += dt; cb.value(dt, t); time.floatValue = t
    }
  }
  return time
}

/** Raw multi-touch: every finger handled independently; extra fingers beyond 5 ignored. */
fun Modifier.multiTouch(key: Any?, onDown: (Long, Offset) -> Unit, onMove: (Long, Offset) -> Unit = { _, _ -> }, onUp: (Long) -> Unit = {}) =
  pointerInput(key) {
    awaitPointerEventScope {
      while (true) {
        val e = awaitPointerEvent()
        val pressed = e.changes.count { it.pressed }
        for (ch in e.changes) {
          when {
            ch.changedToDown() -> if (pressed <= 5) onDown(ch.id.value, ch.position)
            ch.changedToUp() -> onUp(ch.id.value)
            ch.pressed && ch.positionChanged() -> onMove(ch.id.value, ch.position)
          }
          ch.consume()
        }
      }
    }
  }

object Layout {
  /** Lays out n cells in the grid that maximizes cell size for the given screen (folded or unfolded). */
  fun grid(n: Int, w: Float, h: Float, gap: Float, square: Boolean = false): List<Rect> {
    var cols = 1; var best = 0f
    for (c in 1..n) { val r = (n + c - 1) / c; val cell = min((w - gap * (c + 1)) / c, (h - gap * (r + 1)) / r); if (cell > best) { best = cell; cols = c } }
    val rows = (n + cols - 1) / cols
    var cw = (w - gap * (cols + 1)) / cols; var ch = (h - gap * (rows + 1)) / rows
    if (square) { cw = min(cw, ch); ch = cw }
    val totalH = rows * ch + (rows - 1) * gap; val top = (h - totalH) / 2
    return (0 until n).map { i ->
      val row = i / cols; val col = i % cols
      val inRow = if (row == rows - 1) n - row * cols else cols
      val rowW = inRow * cw + (inRow - 1) * gap; val left = (w - rowW) / 2
      Rect(left + col * (cw + gap), top + row * (ch + gap), left + col * (cw + gap) + cw, top + row * (ch + gap) + ch)
    }
  }
}

class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Color, val size: Float, val kind: Int,
               var life: Float, val decay: Float, var rot: Float, val spin: Float)

/** kind: 0 dot, 1 star, 2 heart, 3 music note */
class Particles(private val max: Int = 450) {
  val list = ArrayList<Particle>()
  var gravity = 900f
  fun burst(x: Float, y: Float, n: Int, colors: List<Color> = RAINBOW, kind: Int = 0, speed: Float = 700f, size: Float = 14f, up: Float = 250f, decay: Float = 0.9f) {
    repeat(n) {
      if (list.size >= max) return
      val a = Random.nextFloat() * 2 * PI.toFloat(); val s = speed * (0.3f + Random.nextFloat() * 0.7f)
      list += Particle(x, y, cos(a) * s, sin(a) * s - up, colors.random(), size * (0.6f + Random.nextFloat() * 0.8f), kind,
        1f, decay * (0.7f + Random.nextFloat() * 0.6f), Random.nextFloat() * 360f, (Random.nextFloat() - 0.5f) * 400f)
    }
  }
  fun step(dt: Float) {
    val it = list.iterator()
    while (it.hasNext()) {
      val p = it.next()
      p.x += p.vx * dt; p.y += p.vy * dt; p.vy += gravity * dt; p.vx *= (1 - dt * 0.8f)
      p.life -= p.decay * dt; p.rot += p.spin * dt
      if (p.life <= 0f) it.remove()
    }
  }
  fun draw(d: DrawScope) = with(d) {
    for (p in list) {
      val c = p.color.copy(alpha = p.life.coerceIn(0f, 1f)); val o = Offset(p.x, p.y)
      when (p.kind) { 1 -> drawStar(o, p.size, c, p.rot); 2 -> drawHeart(o, p.size, c); 3 -> drawNote(o, p.size, c)
        4 -> { val fw = p.size * 2f * abs(cos(p.rot * 0.03f)).coerceAtLeast(0.15f); rotate(p.rot, o) { drawRect(c, Offset(o.x - fw / 2, o.y - p.size * 0.45f), Size(fw, p.size * 0.9f)) } }
        5 -> rotate(p.rot, o) { drawOval(c, Offset(o.x - p.size * 0.8f, o.y - p.size * 0.3f), Size(p.size * 1.6f, p.size * 0.6f)) }
        else -> drawCircle(c, p.size * p.life.coerceIn(0.3f, 1f), o) }
    }
  }
}

fun DrawScope.drawStar(c: Offset, r: Float, color: Color, rot: Float = 0f, points: Int = 5) {
  val p = Path()
  for (i in 0 until points * 2) {
    val rr = if (i % 2 == 0) r else r * 0.45f
    val a = (rot - 90f) * PI / 180 + i * PI / points
    val x = c.x + (cos(a) * rr).toFloat(); val y = c.y + (sin(a) * rr).toFloat()
    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
  }
  p.close(); drawPath(p, color)
}
fun DrawScope.drawHeart(c: Offset, s: Float, color: Color) {
  val p = Path()
  p.moveTo(c.x, c.y + s * 0.9f)
  p.cubicTo(c.x - s * 1.6f, c.y - s * 0.2f, c.x - s * 0.6f, c.y - s * 1.3f, c.x, c.y - s * 0.4f)
  p.cubicTo(c.x + s * 0.6f, c.y - s * 1.3f, c.x + s * 1.6f, c.y - s * 0.2f, c.x, c.y + s * 0.9f)
  drawPath(p, color)
}
fun DrawScope.drawNote(c: Offset, s: Float, color: Color) {
  drawOval(color, Offset(c.x - s * 0.5f, c.y - s * 0.35f), Size(s, s * 0.7f))
  drawLine(color, Offset(c.x + s * 0.42f, c.y), Offset(c.x + s * 0.42f, c.y - s * 1.8f), s * 0.18f)
  drawLine(color, Offset(c.x + s * 0.42f, c.y - s * 1.8f), Offset(c.x + s * 1.0f, c.y - s * 1.35f), s * 0.18f, StrokeCap.Round)
}
fun DrawScope.drawCloud(c: Offset, s: Float, color: Color) {
  drawCircle(color, s * 0.5f, c + Offset(-s * 0.55f, s * 0.1f)); drawCircle(color, s * 0.7f, c + Offset(0f, -s * 0.15f))
  drawCircle(color, s * 0.5f, c + Offset(s * 0.6f, s * 0.1f)); drawOval(color, c + Offset(-s * 1.05f, 0f), Size(s * 2.1f, s * 0.6f))
}
/** Friendly glove pointing at [tip]; [press] 0..1 makes it tap. */
fun DrawScope.drawHand(tip: Offset, s: Float, press: Float) {
  val t = tip + Offset(s * 0.25f * (1 - press), s * 0.35f * (1 - press))
  val palm = t + Offset(s * 0.7f, s * 1.05f)
  val edge = Color(0xFF555566)
  drawLine(edge, t, palm, s * 0.42f, StrokeCap.Round); drawCircle(edge, s * 0.62f, palm)
  drawLine(Color.White, t, palm, s * 0.32f, StrokeCap.Round); drawCircle(Color.White, s * 0.56f, palm)
  if (press > 0.6f) drawCircle(Color.White.copy(alpha = (press - 0.6f) * 1.5f), s * 0.6f * press, tip, style = Stroke(s * 0.08f))
}

/** Small corner button that only acts after a continuous 3-second hold (parents only). */
@Composable
fun HoldButton(modifier: Modifier, gear: Boolean, seconds: Float = if (gear) 3f else 1.5f, onTrigger: () -> Unit) {
  var holding by remember { mutableStateOf(false) }
  var progress by remember { mutableFloatStateOf(0f) }
  val trig by rememberUpdatedState(onTrigger)
  LaunchedEffect(holding) {
    if (holding) {
      val start = withFrameNanos { it }
      while (true) {
        val n = withFrameNanos { it }
        progress = (n - start) / (seconds * 1e9f)
        if (progress >= 1f) { holding = false; progress = 0f; Fx.tap(true); trig(); break }
      }
    } else progress = 0f
  }
  Canvas(modifier.padding(22.dp).size(54.dp).pointerInput(Unit) {
    awaitEachGesture {
      awaitFirstDown().consume(); holding = true
      do { val e = awaitPointerEvent(); e.changes.forEach { it.consume() } } while (e.changes.any { it.pressed })
      holding = false
    }
  }) {
    val r = size.minDimension / 2; val c = center
    drawCircle(Color.White.copy(alpha = if (holding) 0.92f else 0.38f), r); drawCircle(Color.White.copy(alpha = 0.5f), r, style = Stroke(r * 0.06f))
    val ic = Color(0xFF445566).copy(alpha = if (holding) 1f else 0.55f)
    if (gear) {
      for (i in 0 until 8) { val a = i * PI / 4; drawLine(ic, c, c + Offset((cos(a) * r * 0.6f).toFloat(), (sin(a) * r * 0.6f).toFloat()), r * 0.2f, StrokeCap.Round) }
      drawCircle(ic, r * 0.42f, c); drawCircle(Color.White, r * 0.17f, c)
    } else {
      val p = Path().apply { moveTo(c.x, c.y - r * 0.55f); lineTo(c.x + r * 0.55f, c.y - r * 0.02f); lineTo(c.x + r * 0.38f, c.y - r * 0.02f)
        lineTo(c.x + r * 0.38f, c.y + r * 0.48f); lineTo(c.x - r * 0.38f, c.y + r * 0.48f); lineTo(c.x - r * 0.38f, c.y - r * 0.02f); lineTo(c.x - r * 0.55f, c.y - r * 0.02f); close() }
      drawPath(p, ic)
    }
    if (progress > 0f) drawArc(Color(0xFF4D96FF), -90f, 360f * progress, false, Offset(r * 0.09f, r * 0.09f), Size(size.width - r * 0.18f, size.height - r * 0.18f), style = Stroke(r * 0.18f, cap = StrokeCap.Round))
  }
}
