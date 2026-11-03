package com.peyton.toddlergame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.*

fun DrawScope.drawShape(kind: Int, c: Offset, r: Float, color: Color) {
  when (kind % 8) {
    0 -> drawCircle(color, r, c)
    1 -> drawStar(c, r * 1.1f, color)
    2 -> drawHeart(c + Offset(0f, r * 0.1f), r * 0.9f, color)
    3 -> drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y + r * 0.8f); lineTo(c.x - r, c.y + r * 0.8f); close() }, color)
    4 -> drawPath(Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }, color)
    5 -> { for (i in 0 until 6) { val a = i * PI / 3; drawCircle(color, r * 0.42f, c + Offset((cos(a) * r * 0.6f).toFloat(), (sin(a) * r * 0.6f).toFloat())) }; drawCircle(Color(0xFFFFE066), r * 0.35f, c) }
    6 -> drawRoundRect(color, Offset(c.x - r * 0.85f, c.y - r * 0.85f), Size(r * 1.7f, r * 1.7f), CornerRadius(r * 0.35f))
    else -> drawCircle(color, r * 0.8f, c, style = Stroke(r * 0.35f))
  }
}

internal class Ripple(val x: Float, val y: Float, val t0: Float, val color: Color)
internal class MusicModel(private val sink: SoundSink = GameSound) {
  val hit = FloatArray(8) { -10f }; val ripples = ArrayList<Ripple>(); val finger = HashMap<Long, Int>()
  val parts = Particles().apply { gravity = -350f }
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = 0f
  fun rects(): List<Rect> = Layout.grid(8, w, h, min(w, h) * 0.035f)
  fun press(id: Long, p: Offset, fresh: Boolean) {
    lastTouch = t
    val i = rects().indexOfFirst { it.contains(p) }
    if (i < 0 || (!fresh && finger[id] == i)) return
    finger[id] = i; hit[i] = t
    sink.play(Sfx.bells[i + 1], 0.6f); sink.haptic()
    ripples += Ripple(p.x, p.y, t, RAINBOW[i])
    parts.burst(p.x, p.y, 3, listOf(Color.White, RAINBOW[i]), kind = 3, speed = 250f, size = min(w, h) * 0.03f, up = 300f, decay = 0.8f)
  }
  fun step(dt: Float) { ripples.removeAll { t - it.t0 > 1.0f }; if (ripples.size > 40) ripples.removeAt(0); parts.step(dt) }
}

@Composable
fun MusicScreen() {
  val m = remember { MusicModel() }
  val time = rememberTicker { dt, t -> m.t = t; m.step(dt) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }.multiTouch(Unit, onDown = { id, p -> m.press(id, p, true) }, onMove = { id, p -> m.press(id, p, false) }, onUp = { m.finger.remove(it) })) {
    val t = time.value; m.w = size.width; m.h = size.height
    meshBg(Color(0xFFF6EEFF), Color(0xFFFFF0E6), listOf(Pal.blush to Offset(0.1f, 0.9f), Color(0xFFD7F5FF) to Offset(0.9f, 0.1f)), t)
    val rects = m.rects(); val idle = t - m.lastTouch > 6f; val demo = ((t * 2.5f).toInt()) % 8
    rects.forEachIndexed { i, rc ->
      val since = t - m.hit[i]; val k = (1 - since / 0.4f).coerceIn(0f, 1f)
      val sc = 1f - 0.06f * k * sin(since * 30f).let { if (since < 0.4f) abs(it) else 0f }
      val col = RAINBOW[i]; val cr = CornerRadius(min(rc.width, rc.height) * 0.18f)
      scale(sc, sc, rc.center) {
        clayRect(rc, cr.x, col.compositeLight(k), 1f - 0.6f * k)
        val pulse = 1f + 0.25f * k + (if (idle && i == demo) 0.08f * sin(t * 15f) else 0.03f * sin(t * 2f + i))
        drawShape(i, rc.center, min(rc.width, rc.height) * 0.22f * pulse, Color.White.copy(alpha = 0.92f))
      }
    }
    for (rp in m.ripples) { val k = (t - rp.t0); val rc = rects.firstOrNull { it.contains(Offset(rp.x, rp.y)) } ?: continue
      clipRect(rc.left, rc.top, rc.right, rc.bottom) { for (j in 0..2) { val kk = k - j * 0.15f; if (kk > 0) drawCircle(Color.White.copy(alpha = (0.7f * (1 - kk)).coerceAtLeast(0f)), kk * min(m.w, m.h) * 0.5f, Offset(rp.x, rp.y), style = Stroke(10f)) } } }
    m.parts.draw(this)
    if (idle) rects.getOrNull(demo)?.let { drawHand(it.center + Offset(it.width * 0.1f, it.height * 0.1f), min(it.width, it.height) * 0.12f, abs(sin(t * 7.85f))) }
  }
}

private fun Color.compositeLight(k: Float) = Color(red + (1 - red) * 0.4f * k, green + (1 - green) * 0.4f * k, blue + (1 - blue) * 0.4f * k, alpha)
