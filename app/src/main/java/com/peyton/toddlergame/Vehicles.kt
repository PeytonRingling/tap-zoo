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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.*
import kotlin.random.Random

private fun DrawScope.wheel(c: Offset, r: Float, rot: Float) {
  drawCircle(Color(0xFF3B3754), r, c); drawCircle(Color(0xFFD9D6EA), r * 0.5f, c)
  rotate(rot, c) { drawLine(Color(0xFF8A86A3), c - Offset(r * 0.45f, 0f), c + Offset(r * 0.45f, 0f), r * 0.14f); drawLine(Color(0xFF8A86A3), c - Offset(0f, r * 0.45f), c + Offset(0f, r * 0.45f), r * 0.14f) }
}
private fun DrawScope.window(r: Rect, rad: Float) = drawRoundRect(Brush.linearGradient(listOf(Color(0xFFE8F8FF), Color(0xFF9FD8F5)), r.topLeft, r.bottomRight), r.topLeft, r.size, CornerRadius(rad))
private fun DrawScope.eyes(c: Offset, s: Float, look: Float) { for (d in listOf(-1f, 1f)) { val e = c + Offset(d * s * 0.9f, 0f); drawCircle(Color.White, s * 0.55f, e); drawCircle(Pal.ink, s * 0.32f, e + Offset(look * s * 0.15f, s * 0.05f)); drawCircle(Color.White, s * 0.1f, e + Offset(-s * 0.08f, -s * 0.12f)) } }

/** Vehicle facing right, bottom of wheels at [base]. kind 0 car,1 bus,2 truck,3 train. Returns length. */
fun DrawScope.drawVehicle(kind: Int, x: Float, base: Float, L: Float, color: Color, t: Float, roll: Float) {
  val wr = L * 0.13f; val y = base - wr
  when (kind) {
    0 -> { val len = L * 1.5f; val body = Rect(x, y - L * 0.38f, x + len, y + wr * 0.2f)
      clayRect(Rect(x + len * 0.18f, body.top - L * 0.32f, x + len * 0.78f, body.top + L * 0.1f), L * 0.14f, color.lighten(0.1f), 0.5f)
      window(Rect(x + len * 0.25f, body.top - L * 0.24f, x + len * 0.47f, body.top), L * 0.06f); window(Rect(x + len * 0.51f, body.top - L * 0.24f, x + len * 0.72f, body.top), L * 0.06f)
      clayRect(body, L * 0.16f, color); eyes(Offset(x + len * 0.83f, body.top + L * 0.14f), L * 0.07f, 1f)
      drawCircle(Pal.sun, L * 0.05f, Offset(x + len - L * 0.04f, body.center.y + L * 0.06f))
      wheel(Offset(x + len * 0.24f, y), wr, roll); wheel(Offset(x + len * 0.76f, y), wr, roll) }
    1 -> { val len = L * 2.3f; val body = Rect(x, y - L * 0.75f, x + len, y + wr * 0.2f)
      clayRect(body, L * 0.16f, color)
      for (i in 0 until 4) window(Rect(x + len * (0.06f + i * 0.19f), body.top + L * 0.12f, x + len * (0.06f + i * 0.19f) + len * 0.15f, body.top + L * 0.38f), L * 0.06f)
      window(Rect(x + len * 0.84f, body.top + L * 0.12f, x + len * 0.96f, body.top + L * 0.5f), L * 0.06f)
      drawRoundRect(Color.White.copy(alpha = 0.6f), Offset(x + len * 0.04f, body.top + L * 0.48f), Size(len * 0.75f, L * 0.06f), CornerRadius(L * 0.03f))
      eyes(Offset(x + len * 0.9f, body.top + L * 0.26f), L * 0.05f, 1f)
      wheel(Offset(x + len * 0.2f, y), wr, roll); wheel(Offset(x + len * 0.8f, y), wr, roll) }
    2 -> { val len = L * 2.1f; val box = Rect(x, y - L * 0.85f, x + len * 0.62f, y + wr * 0.1f); val cab = Rect(x + len * 0.66f, y - L * 0.62f, x + len, y + wr * 0.2f)
      clayRect(box, L * 0.1f, Color(0xFFF4F1FF)); drawRoundRect(color.copy(alpha = 0.85f), Offset(box.left + L * 0.1f, box.top + L * 0.15f), Size(box.width - L * 0.2f, L * 0.12f), CornerRadius(L * 0.06f))
      clayRect(cab, L * 0.14f, color); window(Rect(cab.left + cab.width * 0.35f, cab.top + L * 0.08f, cab.right - L * 0.08f, cab.top + L * 0.3f), L * 0.05f)
      eyes(Offset(cab.left + cab.width * 0.6f, cab.top + L * 0.42f), L * 0.05f, 1f)
      wheel(Offset(x + len * 0.15f, y), wr, roll); wheel(Offset(x + len * 0.45f, y), wr, roll); wheel(Offset(x + len * 0.85f, y), wr, roll) }
    else -> { val car = L * 1.15f
      for (k in 0..1) { val wx = x + k * (car + L * 0.12f); val wc = if (k == 0) Pal.mint else Pal.sun
        drawLine(Color(0xFF6B6785), Offset(wx + car, y - L * 0.2f), Offset(wx + car + L * 0.12f, y - L * 0.2f), L * 0.06f)
        clayRect(Rect(wx, y - L * 0.6f, wx + car, y + wr * 0.1f), L * 0.12f, wc); wheel(Offset(wx + car * 0.25f, y), wr, roll); wheel(Offset(wx + car * 0.75f, y), wr, roll) }
      val ex = x + 2 * (car + L * 0.12f); val elen = L * 1.5f
      clayRect(Rect(ex, y - L * 0.95f, ex + elen * 0.4f, y), L * 0.1f, color.darken(0.1f)); window(Rect(ex + elen * 0.07f, y - L * 0.85f, ex + elen * 0.33f, y - L * 0.58f), L * 0.05f)
      clayRect(Rect(ex + elen * 0.38f, y - L * 0.62f, ex + elen, y + wr * 0.1f), L * 0.28f, color)
      drawRoundRect(Color(0xFF3B3754), Offset(ex + elen * 0.72f, y - L * 0.95f), Size(L * 0.16f, L * 0.36f), CornerRadius(L * 0.05f))
      eyes(Offset(ex + elen * 0.78f, y - L * 0.32f), L * 0.06f, 1f)
      wheel(Offset(ex + elen * 0.2f, y), wr * 1.2f, roll); wheel(Offset(ex + elen * 0.55f, y), wr, roll); wheel(Offset(ex + elen * 0.85f, y), wr, roll) }
  }
}
fun vehicleLen(kind: Int, L: Float) = when (kind) { 0 -> L * 1.5f; 1 -> L * 2.3f; 2 -> L * 2.1f; else -> L * 2 * 1.27f + L * 1.5f }

private class Veh(var kind: Int, var x: Float, val lane: Int, val dir: Float, var color: Color) { var boost = 0f; var hit = -10f; var roll = 0f }

private class VehModel {
  val vs = ArrayList<Veh>(); val parts = Particles().apply { gravity = -120f }
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = 0f
  fun lanes() = if (h > w * 1.2f) 4 else 3
  fun laneBase(i: Int) = h * (0.3f + 0.68f * (i + 1) / lanes()) - h * 0.04f
  fun L() = min(h * 0.68f / lanes() * 0.62f, w * 0.16f)
  fun reset(v: Veh) { v.kind = if (v.lane == 0) 3 else Random.nextInt(3); v.color = Pal.vivid.random(); val len = vehicleLen(v.kind, L()); v.x = if (v.dir > 0) -len - Random.nextFloat() * w * 0.5f else w + Random.nextFloat() * w * 0.5f }
  fun step(dt: Float) {
    if (w < 2f) return
    if (vs.isEmpty()) for (i in 0 until lanes()) { val v = Veh(if (i == 0) 3 else i % 3, 0f, i, if (i % 2 == 0) 1f else -1f, Pal.vivid[i * 2 % 9]); v.x = w * (0.15f + 0.2f * i); vs += v }
    for (v in vs) {
      val sp = w * 0.11f * (1 + v.boost * 3f); v.x += v.dir * sp * dt; v.roll += sp * dt / (L() * 0.13f) * 57f * v.dir; v.boost = max(0f, v.boost - dt * 0.7f)
      val len = vehicleLen(v.kind, L())
      if ((v.dir > 0 && v.x > w + len * 0.2f) || (v.dir < 0 && v.x < -len * 1.2f)) reset(v)
      if (v.boost > 0.2f && Random.nextFloat() < 0.5f) parts.burst(if (v.dir > 0) v.x else v.x + len, laneBase(v.lane) - L() * 0.2f, 1, listOf(Color.White.copy(alpha = 0.8f)), speed = 60f, size = L() * 0.12f, up = 30f, decay = 1.2f)
      if (v.kind == 3 && Random.nextFloat() < dt * 3f) parts.burst((if (v.dir > 0) v.x + len - L() * 0.35f else v.x + L() * 0.35f), laneBase(v.lane) - L() * 1.15f, 1, listOf(Color.White.copy(alpha = 0.9f)), speed = 40f, size = L() * 0.15f, up = 80f, decay = 0.6f)
    }
    parts.step(dt)
  }
  fun tap(p: Offset) {
    lastTouch = t
    val v = vs.firstOrNull { val len = vehicleLen(it.kind, L()); p.x in it.x - 30f..it.x + len + 30f && abs(p.y - (laneBase(it.lane) - L() * 0.45f)) < L() * 0.8f }
    if (v != null) { v.boost = 1f; v.hit = t; Audio.play(Sfx.honks[v.kind]); Audio.play(Sfx.zoom, 0.3f); Fx.tap(true)
      parts.burst(p.x, p.y, 14, Pal.vivid, kind = 1, speed = 600f, size = L() * 0.08f) }
    else { parts.burst(p.x, p.y, 6, listOf(Color.White, Pal.sun), kind = 1, speed = 250f, size = 12f); Audio.play(Sfx.plip, 0.3f) }
  }
}

@Composable
fun VehiclesScreen() {
  val m = remember { VehModel() }
  val time = rememberTicker { dt, t -> m.t = t; m.step(dt) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }.multiTouch(Unit, onDown = { _, p -> m.tap(p) })) {
    val t = time.value; val mm = min(size.width, size.height)
    meshBg(Color(0xFFBFE6FF), Color(0xFFE9F9E4), listOf(Pal.blush to Offset(0.85f, 0.15f), Color(0xFFFFF3C4) to Offset(0.1f, 0.05f)), t)
    for (i in 0 until 3) softCloud(Offset(((t * 12f + i * size.width / 2.2f) % (size.width * 1.5f)) - size.width * 0.25f, size.height * (0.07f + 0.06f * i)), mm * 0.06f, 0.85f)
    hills(Color(0xFFBFE8B4), size.height * 0.3f, size.height * 0.06f, 0f)
    drawRect(Brush.verticalGradient(listOf(Color(0xFFB4E3A8), Color(0xFF9AD68E)), size.height * 0.29f, size.height), Offset(0f, size.height * 0.29f), Size(size.width, size.height * 0.71f))
    val L = m.L()
    for (i in 1 until m.lanes()) { val top = m.laneBase(i) - L * 0.25f
      for (k in 0 until 5) { val tx = (size.width * (0.08f + k * 0.21f) + i * L * 0.9f) % size.width; val ty = top - L * 0.02f
        drawLine(Color(0xFF9C7A54), Offset(tx, ty), Offset(tx, ty - L * 0.25f), L * 0.05f, StrokeCap.Round); clayCircle(Offset(tx, ty - L * 0.38f), L * 0.16f, if (k % 2 == 0) Color(0xFF52C76A) else Color(0xFF7ED957), false) } }
    for (i in 0 until m.lanes()) { val base = m.laneBase(i); val top = base - L * 0.25f; val bot = base + L * 0.25f
      if (i == 0) { drawRect(Color(0xFFD9C6A5), Offset(0f, base - L * 0.05f), Size(size.width, L * 0.2f))
        var x = -((t * 40f) % (L * 0.4f)); while (x < size.width) { drawRoundRect(Color(0xFF9C7A54), Offset(x, base - L * 0.05f), Size(L * 0.12f, L * 0.2f), CornerRadius(4f)); x += L * 0.4f }
        drawLine(Color(0xFF8A86A3), Offset(0f, base), Offset(size.width, base), L * 0.04f) }
      else { drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF8E8AAE), Color(0xFF6E6A8E)), top, bot), Offset(-20f, top), Size(size.width + 40f, bot - top), CornerRadius(L * 0.1f))
        var x = 0f; while (x < size.width) { drawRoundRect(Color.White.copy(alpha = 0.7f), Offset(x, (top + bot) / 2 - L * 0.02f), Size(L * 0.3f, L * 0.04f), CornerRadius(L * 0.02f)); x += L * 0.6f } }
    }
    for (v in m.vs) { val len = vehicleLen(v.kind, L); val base = m.laneBase(v.lane)
      val bounce = (t - v.hit).let { if (it < 0.6f) -abs(sin(it * 15f)) * L * 0.08f * (1 - it / 0.6f) else -abs(sin(t * 8f + v.lane)) * L * 0.012f }
      val cx = v.x + len / 2
      scale(v.dir, 1f, Offset(cx, base)) { drawVehicle(v.kind, v.x, base + bounce, L, v.color, t, v.roll) }
      if (v.boost > 0.3f) for (k in 0..2) { val sx = if (v.dir > 0) v.x - L * 0.2f else v.x + len + L * 0.2f; val yy = base - L * (0.25f + k * 0.18f)
        drawLine(Color.White.copy(alpha = 0.7f * v.boost), Offset(sx, yy), Offset(sx - v.dir * L * (0.4f + k * 0.1f), yy), L * 0.04f, StrokeCap.Round) }
    }
    m.parts.draw(this)
    if (t - m.lastTouch > 7f) m.vs.firstOrNull { it.x in size.width * 0.1f..size.width * 0.6f }?.let { drawHand(Offset(it.x + vehicleLen(it.kind, L) / 2, m.laneBase(it.lane) - L * 0.4f), mm * 0.045f, abs(sin(t * 3f))) }
  }
}
