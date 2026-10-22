package com.peyton.toddlergame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.*

private class Wave(val x: Float, val y: Float, val t0: Float, val color: Color)
private class DrumModel {
  val barHit = FloatArray(8) { -10f }; val drumHit = FloatArray(4) { -10f }; val waves = ArrayList<Wave>()
  val parts = Particles().apply { gravity = -300f }; val finger = HashMap<Long, Int>()
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = 0f
  fun portrait() = h > w
  fun xyloArea() = if (portrait()) Rect(w * 0.06f, h * 0.1f, w * 0.94f, h * 0.52f) else Rect(w * 0.08f, h * 0.08f, w * 0.92f, h * 0.5f)
  fun bars(): List<Rect> { val a = xyloArea(); val gap = a.width * 0.02f; val bw = (a.width - gap * 9) / 8
    return (0 until 8).map { i -> val bh = a.height * (0.95f - i * 0.045f); val x = a.left + gap + i * (bw + gap); Rect(x, a.center.y - bh / 2, x + bw, a.center.y + bh / 2) } }
  fun drums(): List<Pair<Offset, Float>> { val top = h * 0.58f; val avail = h - top - h * 0.05f
    val r = min(w / 4f * 0.42f, avail * 0.42f); return (0 until 4).map { i -> Offset(w * (0.125f + i * 0.25f), top + avail * 0.55f) to r * (if (i == 1) 1.15f else 1f) } }
  fun hit(id: Long, p: Offset, fresh: Boolean) {
    lastTouch = t
    val bi = bars().indexOfFirst { it.inflate(10f).contains(p) }
    if (bi >= 0) { if (!fresh && finger[id] == bi) return; finger[id] = bi; barHit[bi] = t; Audio.play(Sfx.xylo[bi + 1], 0.7f); Fx.tap(); waves += Wave(p.x, p.y, t, RAINBOW[bi])
      parts.burst(p.x, p.y, 2, listOf(Color.White, RAINBOW[bi]), kind = 3, speed = 200f, size = min(w, h) * 0.025f, up = 250f); return }
    if (!fresh) return
    val di = drums().indexOfFirst { (c, r) -> hypot(p.x - c.x, (p.y - c.y) * 1.2f) < r * 1.2f }
    if (di >= 0) { drumHit[di] = t; Audio.play(listOf(Sfx.tom, Sfx.kick, Sfx.snare, Sfx.cymbal)[di], 0.9f); Fx.tap(true)
      val (c, r) = drums()[di]; waves += Wave(c.x, c.y - r * 0.4f, t, Pal.vivid[di * 2]); parts.burst(c.x, c.y - r * 0.5f, 10, Pal.vivid, kind = 1, speed = 500f, size = r * 0.1f, up = 300f) }
  }
  fun step(dt: Float) { waves.removeAll { t - it.t0 > 0.8f }; parts.step(dt) }
}

@Composable
fun DrumsScreen() {
  val m = remember { DrumModel() }
  val time = rememberTicker { dt, t -> m.t = t; m.step(dt) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }
    .multiTouch(Unit, onDown = { id, p -> m.hit(id, p, true) }, onMove = { id, p -> m.hit(id, p, false) }, onUp = { m.finger.remove(it) })) {
    val t = time.value; val mm = min(size.width, size.height)
    meshBg(Color(0xFFFFF0E0), Color(0xFFEDE4FF), listOf(Pal.blush to Offset(0.1f, 0.2f), Color(0xFFD7F5FF) to Offset(0.9f, 0.8f)), t)
    val a = m.xyloArea()
    clayRect(Rect(a.left - mm * 0.02f, a.center.y - a.height * 0.32f, a.right + mm * 0.02f, a.center.y - a.height * 0.22f), mm * 0.02f, Color(0xFFC08A5B), 0.6f)
    clayRect(Rect(a.left - mm * 0.02f, a.center.y + a.height * 0.22f, a.right + mm * 0.02f, a.center.y + a.height * 0.32f), mm * 0.02f, Color(0xFFC08A5B), 0.6f)
    m.bars().forEachIndexed { i, r -> val since = t - m.barHit[i]; val k = (1 - since / 0.35f).coerceIn(0f, 1f)
      scale(1f + 0.05f * k, 1f - 0.04f * k, r.center) {
        clayRect(r, r.width * 0.3f, RAINBOW[i].lighten(0.15f * k), 1f - 0.5f * k)
        for (yy in listOf(r.top + r.height * 0.12f, r.bottom - r.height * 0.12f)) { drawCircle(Color.White.copy(alpha = 0.85f), r.width * 0.09f, Offset(r.center.x, yy)); drawCircle(RAINBOW[i].darken(0.3f), r.width * 0.045f, Offset(r.center.x, yy)) } } }
    m.drums().forEachIndexed { i, (c, r) -> val since = t - m.drumHit[i]; val k = (1 - since / 0.3f).coerceIn(0f, 1f); val sq = 0.08f * k * cos(since * 40f)
      scale(1f + sq, 1f - sq, c) {
        if (i == 3) { drawLine(Color(0xFF8A86A3), c + Offset(0f, -r * 0.2f), c + Offset(0f, r * 0.9f), r * 0.08f)
          val cy = c.y - r * 0.25f - k * r * 0.1f
          drawOval(Brush.radialGradient(listOf(Color(0xFFFFF0A8), Color(0xFFF2B93B), Color(0xFFD08F1F)), Offset(c.x - r * 0.3f, cy - r * 0.1f), r * 1.3f), Offset(c.x - r, cy - r * 0.22f), Size(r * 2, r * 0.44f))
          drawCircle(Color(0xFFB57A14), r * 0.08f, Offset(c.x, cy - r * 0.02f)) }
        else { val col = listOf(Pal.blue, Pal.coral, Pal.mint)[i]; val body = Rect(c.x - r, c.y - r * 0.45f, c.x + r, c.y + r * 0.75f)
          softShadow(Rect(body.left, body.top + r * 0.3f, body.right, body.bottom + r * 0.2f), r * 0.3f, r * 0.25f)
          drawRoundRect(Brush.horizontalGradient(listOf(col.darken(0.15f), col.lighten(0.25f), col, col.darken(0.2f)), body.left, body.right), body.topLeft, body.size, CornerRadius(r * 0.25f))
          val zig = Path(); for (j in 0..8) { val x = body.left + body.width * j / 8; val y = if (j % 2 == 0) body.top + r * 0.2f else body.bottom - r * 0.15f; if (j == 0) zig.moveTo(x, y) else zig.lineTo(x, y) }
          drawPath(zig, Color.White.copy(alpha = 0.8f), style = Stroke(r * 0.05f))
          drawOval(Brush.verticalGradient(listOf(Color(0xFFFFFBF2), Color(0xFFEDE3D0)), body.top - r * 0.3f, body.top + r * 0.3f), Offset(body.left, body.top - r * 0.3f), Size(body.width, r * 0.6f))
          drawOval(col.darken(0.1f), Offset(body.left, body.top - r * 0.3f), Size(body.width, r * 0.6f), style = Stroke(r * 0.08f))
          drawOval(Pal.ink.copy(alpha = 0.06f + 0.15f * k), Offset(c.x - r * 0.4f, body.top - r * 0.12f), Size(r * 0.8f, r * 0.24f)) }
      } }
    for (wv in m.waves) { val k = (t - wv.t0) / 0.8f; drawCircle(wv.color.copy(alpha = 0.5f * (1 - k)), mm * 0.25f * k, Offset(wv.x, wv.y), style = Stroke(mm * 0.012f * (1 - k) + 2f)) }
    m.parts.draw(this)
    if (t - m.lastTouch > 6f) { val i = ((t * 1.5f).toInt()) % 8; m.bars().getOrNull(i)?.let { drawHand(it.center, mm * 0.045f, abs(sin(t * 4.7f))) } }
  }
}
