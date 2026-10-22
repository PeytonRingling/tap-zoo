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

private class Fish(var x: Float, var y: Float, var dir: Float, val s: Float, val color: Color, val stripe: Color, val phase: Float) { var boost = 0f; var baseY = y; var tapT = -10f; var targetX: Float? = null }
private class AquaBub(var x: Float, var y: Float, val r: Float)
private class Crumb(var x: Float, var y: Float, val t0: Float)

private class AquaModel {
  val fish = ArrayList<Fish>(); val bubs = ArrayList<AquaBub>(); val crumbs = ArrayList<Crumb>()
  val parts = Particles().apply { gravity = -200f }
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = 0f; var crabX = 0.3f; var crabDir = 1f; var crabHop = -10f
  fun init() {
    val m = min(w, h); val n = (w * h / (m * m * 0.18f)).toInt().coerceIn(5, 9)
    repeat(n) { i -> val c = Pal.vivid[(i * 2) % Pal.vivid.size]
      fish += Fish(Random.nextFloat() * w, h * (0.15f + Random.nextFloat() * 0.6f), if (Random.nextBoolean()) 1f else -1f, m * (0.07f + Random.nextFloat() * 0.05f), c, Color.White.copy(alpha = 0.9f).takeIf { i % 2 == 0 } ?: c.darken(0.25f), Random.nextFloat() * 6f) }
  }
  fun step(dt: Float) {
    if (w < 2f) return
    if (fish.isEmpty()) init()
    for (f in fish) {
      f.targetX?.let { tx -> f.dir = if (tx > f.x) 1f else -1f; if (abs(tx - f.x) < f.s) f.targetX = null }
      val sp = w * 0.05f * (1 + f.boost * 6f) * (if (f.targetX != null) 2.2f else 1f)
      f.x += f.dir * sp * dt; f.baseY += sin(t * 0.3f + f.phase) * h * 0.01f * dt
      f.baseY = f.baseY.coerceIn(h * 0.12f, h * 0.75f); f.y = f.baseY + sin(t * 1.4f + f.phase) * f.s * 0.3f
      f.boost = max(0f, f.boost - dt * 0.9f)
      if (f.x < -f.s * 1.5f && f.dir < 0) { f.dir = 1f } else if (f.x > w + f.s * 1.5f && f.dir > 0) f.dir = -1f
    }
    if (Random.nextFloat() < dt * 2.5f) bubs += AquaBub(w * (0.1f + Random.nextFloat() * 0.8f), h * 0.9f, min(w, h) * (0.006f + Random.nextFloat() * 0.012f))
    bubs.forEach { it.y -= h * 0.12f * dt; it.x += sin(t * 3f + it.r) * 15f * dt }; bubs.removeAll { it.y < -20f }
    crumbs.forEach { it.y += h * 0.05f * dt }
    crumbs.removeAll { c -> t - c.t0 > 5f || fish.any { f -> hypot(f.x - c.x, f.y - c.y) < f.s }.also { if (it) Audio.play(Sfx.plip, 0.3f) } }
    crabX += crabDir * dt * 0.04f; if (crabX > 0.85f || crabX < 0.1f) crabDir = -crabDir
    parts.step(dt)
  }
  fun tap(p: Offset) {
    lastTouch = t
    val m = min(w, h); val crab = Offset(crabX * w, h * 0.9f)
    if (hypot(p.x - crab.x, p.y - crab.y) < m * 0.09f) { crabHop = t; crabDir = -crabDir; Audio.play(Sfx.snap); Fx.tap(); parts.burst(crab.x, crab.y - m * 0.05f, 8, listOf(Color.White), speed = 300f, size = 8f); return }
    val f = fish.filter { hypot(it.x - p.x, it.y - p.y) < it.s * 1.5f }.minByOrNull { hypot(it.x - p.x, it.y - p.y) }
    if (f != null) { f.boost = 1f; f.tapT = t; f.targetX = null; f.dir = if (f.x >= p.x) 1f else -1f; f.baseY += (if (f.y > p.y) 1 else -1) * f.s
      Audio.play(Sfx.blub); Fx.tap(); parts.burst(f.x, f.y, 10, listOf(Color.White.copy(alpha = 0.8f)), speed = 250f, size = f.s * 0.12f, up = 100f) }
    else { repeat(5) { crumbs += Crumb(p.x + (Random.nextFloat() - 0.5f) * 60f, p.y + (Random.nextFloat() - 0.5f) * 30f, t) }; Audio.play(Sfx.plip, 0.5f)
      fish.sortedBy { hypot(it.x - p.x, it.y - p.y) }.take(3).forEach { it.targetX = p.x; it.baseY = p.y } }
  }
}

@Composable
fun AquariumScreen() {
  val m = remember { AquaModel() }
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
    for (b in m.bubs) { drawCircle(Color.White.copy(alpha = 0.35f), b.r, Offset(b.x, b.y)); drawCircle(Color.White.copy(alpha = 0.8f), b.r * 0.3f, Offset(b.x - b.r * 0.3f, b.y - b.r * 0.3f)) }
    for (c in m.crumbs) drawCircle(Color(0xFFFFB86B), mm * 0.008f, Offset(c.x, c.y))
    for (f in m.fish) { val c = Offset(f.x, f.y); val wag = sin(t * (6f + f.boost * 20f) + f.phase) * (15f + f.boost * 15f)
      scale(f.dir, 1f, c) { drawFish(c, f.s, f.color, f.stripe, wag, if (t - f.tapT < 0.6f) 1f else 0f) } }
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
