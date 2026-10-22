package com.peyton.toddlergame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.*
import kotlin.random.Random

fun DrawScope.drawButterfly(c: Offset, s: Float, color: Color, flap: Float) {
  val k = 0.25f + 0.75f * abs(flap)
  for (sd in listOf(-1f, 1f)) scale(k, 1f, c) {
    drawOval(Brush.radialGradient(listOf(color.lighten(0.5f), color, color.darken(0.15f)), c + Offset(sd * s * 0.5f, -s * 0.3f), s * 1.0f), c + Offset(if (sd < 0) -s * 1.05f else s * 0.05f, -s * 0.85f), Size(s, s * 0.95f))
    drawOval(Brush.radialGradient(listOf(color.lighten(0.3f), color.darken(0.1f)), c + Offset(sd * s * 0.4f, s * 0.3f), s * 0.7f), c + Offset(if (sd < 0) -s * 0.75f else s * 0.05f, -s * 0.05f), Size(s * 0.7f, s * 0.65f))
    drawCircle(Color.White.copy(alpha = 0.75f), s * 0.13f, c + Offset(sd * s * 0.55f, -s * 0.4f))
  }
  drawRoundRect(Pal.ink, c + Offset(-s * 0.09f, -s * 0.6f), Size(s * 0.18f, s * 1.15f), androidx.compose.ui.geometry.CornerRadius(s * 0.09f))
  for (sd in listOf(-1f, 1f)) { drawLine(Pal.ink, c + Offset(0f, -s * 0.55f), c + Offset(sd * s * 0.3f, -s * 0.95f), s * 0.05f, StrokeCap.Round); drawCircle(Pal.ink, s * 0.07f, c + Offset(sd * s * 0.3f, -s * 0.95f)) }
}
fun DrawScope.drawFlower(base: Offset, hgt: Float, s: Float, color: Color, sway: Float, bloom: Float) {
  val head = base + Offset(sway * hgt * 0.15f, -hgt)
  val stem = Path().apply { moveTo(base.x, base.y); quadraticBezierTo(base.x + sway * hgt * 0.1f, base.y - hgt * 0.5f, head.x, head.y) }
  drawPath(stem, Color(0xFF3FAE5A), style = Stroke(s * 0.16f, cap = StrokeCap.Round))
  rotate(-35f, base + Offset(0f, -hgt * 0.35f)) { drawOval(Color(0xFF52C76A), base + Offset(0f, -hgt * 0.42f), Size(s * 0.9f, s * 0.38f)) }
  val ps = s * (1f + 0.25f * bloom)
  for (i in 0 until 6) { val a = i * PI / 3 + sway * 0.3; clayCircle(head + Offset((cos(a) * ps * 0.6f).toFloat(), (sin(a) * ps * 0.6f).toFloat()), ps * 0.42f, color, false) }
  clayCircle(head, ps * 0.36f, Pal.sun, false)
  drawCircle(Pal.ink, ps * 0.05f, head + Offset(-ps * 0.12f, -ps * 0.04f)); drawCircle(Pal.ink, ps * 0.05f, head + Offset(ps * 0.12f, -ps * 0.04f))
  drawArc(Pal.ink, 20f, 140f, false, head + Offset(-ps * 0.12f, -ps * 0.02f), Size(ps * 0.24f, ps * 0.16f), style = Stroke(ps * 0.04f, cap = StrokeCap.Round))
}

private class Fly(var x: Float, var y: Float, val color: Color, val phase: Float) { var tx = x; var ty = y; var flee = 0f; var vx = 0f; var vy = 0f }
private class Flower(val fx: Float, val hf: Float, val color: Color) { var bloom = -10f }
private class Bug(var x: Float, var dir: Float) { var hop = -10f }

private class GardenModel {
  val flies = ArrayList<Fly>(); val flowers = ArrayList<Flower>(); val bugs = ArrayList<Bug>()
  val parts = Particles().apply { gravity = -80f }
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = 0f
  fun ground() = h * 0.8f
  fun fs() = min(w, h) * 0.055f
  fun init() {
    val n = if (w > h) 6 else 4
    repeat(n) { flowers += Flower((it + 0.5f) / n + (Random.nextFloat() - 0.5f) * 0.05f, 0.18f + Random.nextFloat() * 0.12f, listOf(Pal.pink, Pal.coral, Pal.grape, Pal.sky, Pal.tangerine, Pal.blue)[it % 6]) }
    repeat(5) { flies += Fly(Random.nextFloat() * w, h * (0.15f + Random.nextFloat() * 0.4f), listOf(Pal.sky, Pal.pink, Pal.tangerine, Pal.grape, Pal.sun)[it], Random.nextFloat() * 6f) }
    repeat(2) { bugs += Bug(w * (0.25f + it * 0.5f), if (it == 0) 1f else -1f) }
  }
  fun step(dt: Float) {
    if (w < 2f) return
    if (flies.isEmpty()) init()
    for (f in flies) {
      if (hypot(f.tx - f.x, f.ty - f.y) < 30f || Random.nextFloat() < dt * 0.2f) { f.tx = w * (0.08f + Random.nextFloat() * 0.84f); f.ty = h * (0.1f + Random.nextFloat() * 0.5f) }
      val sp = h * (0.08f + f.flee * 0.6f)
      val d = hypot(f.tx - f.x, f.ty - f.y).coerceAtLeast(1f)
      f.vx += ((f.tx - f.x) / d * sp - f.vx) * dt * 2f; f.vy += ((f.ty - f.y) / d * sp - f.vy) * dt * 2f
      f.x += f.vx * dt; f.y += f.vy * dt + sin(t * 4f + f.phase) * 20f * dt; f.flee = max(0f, f.flee - dt * 0.6f)
    }
    for (b in bugs) { b.x += b.dir * w * 0.03f * dt; if (b.x < w * 0.05f || b.x > w * 0.95f) b.dir = -b.dir }
    parts.step(dt)
  }
  fun tap(p: Offset) {
    lastTouch = t; val s = fs()
    flies.firstOrNull { hypot(it.x - p.x, it.y - p.y) < s * 1.8f }?.let { f -> f.flee = 1f; f.tx = f.x + (f.x - p.x) * 4f; f.ty = h * 0.08f; Audio.play(Sfx.flutter); Fx.tap()
      parts.burst(f.x, f.y, 14, listOf(f.color, Color.White, Pal.sun), kind = 1, speed = 400f, size = s * 0.2f); return }
    bugs.firstOrNull { hypot(it.x - p.x, ground() + s * 0.6f - p.y) < s * 1.5f }?.let { b -> b.hop = t; b.dir = -b.dir; Audio.play(Sfx.boing(500.0)); Fx.tap(); parts.burst(b.x, ground(), 8, listOf(Pal.coral, Color.White), kind = 2, speed = 300f, size = s * 0.2f); return }
    flowers.firstOrNull { val hx = it.fx * w; abs(hx - p.x) < s * 1.6f && p.y in ground() - it.hf * h - s * 1.6f..ground() }?.let { fl -> fl.bloom = t
      Audio.play(Sfx.bells[(fl.fx * 9).toInt().coerceIn(0, 9)]); Fx.tap()
      val head = Offset(fl.fx * w, ground() - fl.hf * h); parts.burst(head.x, head.y, 16, listOf(fl.color, fl.color.lighten(0.4f)), kind = 2, speed = 450f, size = s * 0.18f)
      if (flies.size < 9) flies += Fly(head.x, head.y, Pal.vivid.random(), Random.nextFloat() * 6f).also { it.flee = 0.6f; it.ty = h * 0.1f }; return }
    parts.burst(p.x, p.y, 6, listOf(Color.White, Pal.sun), kind = 1, speed = 250f, size = 12f); Audio.play(Sfx.plip, 0.3f)
  }
}

@Composable
fun GardenScreen() {
  val m = remember { GardenModel() }
  val time = rememberTicker { dt, t -> m.t = t; m.step(dt) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }.multiTouch(Unit, onDown = { _, p -> m.tap(p) })) {
    val t = time.value; val mm = min(size.width, size.height); val s = m.fs(); val g = m.ground()
    meshBg(Color(0xFFCDEBFF), Color(0xFFFFF4DE), listOf(Pal.blush to Offset(0.15f, 0.25f), Color(0xFFFFF3C4) to Offset(0.85f, 0.1f)), t)
    for (i in 0 until 3) softCloud(Offset(((t * 9f + i * size.width / 2.2f) % (size.width * 1.5f)) - size.width * 0.25f, size.height * (0.08f + 0.07f * i)), mm * 0.06f, 0.8f)
    hills(Color(0xFFC6EBB8), g - size.height * 0.08f, size.height * 0.08f, 1f)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF9EDB8A), Color(0xFF7CC96B)), g, size.height), Offset(0f, g), Size(size.width, size.height - g))
    for (i in 0 until 40) { val x = size.width * i / 40f; drawLine(Color(0xFF6BBF5B), Offset(x, g + 4f), Offset(x + sin(t * 2f + i) * 6f, g - s * 0.35f), s * 0.07f, StrokeCap.Round) }
    for (fl in m.flowers) { val since = t - fl.bloom; val b = if (since < 0.8f) sin(since / 0.8f * PI.toFloat()) else 0f
      drawFlower(Offset(fl.fx * size.width, g + s * 0.2f), fl.hf * size.height, s, fl.color, sin(t * 1.2f + fl.fx * 10f) * 0.5f + b * 0.3f, b) }
    for (b in m.bugs) { val hop = (t - b.hop).let { if (it < 0.5f) -sin(it / 0.5f * PI.toFloat()) * s * 1.5f else 0f }; val c = Offset(b.x, g + s * 0.55f + hop)
      rotate(if (t - b.hop < 0.5f) (t - b.hop) * 720f else 0f, c) {
        clayCircle(c + Offset(b.dir * s * 0.55f, 0f), s * 0.3f, Pal.ink.lighten(0.15f), false)
        clayCircle(c, s * 0.6f, Pal.coral)
        drawLine(Pal.ink, c - Offset(0f, s * 0.58f), c + Offset(0f, s * 0.58f), s * 0.05f)
        for ((dx, dy) in listOf(-0.3f to -0.25f, 0.3f to -0.2f, -0.25f to 0.25f, 0.28f to 0.3f)) drawCircle(Pal.ink, s * 0.1f, c + Offset(dx * s, dy * s))
        drawCircle(Color.White, s * 0.08f, c + Offset(b.dir * s * 0.65f, -s * 0.1f)); drawCircle(Pal.ink, s * 0.045f, c + Offset(b.dir * s * 0.68f, -s * 0.1f)) } }
    for (f in m.flies) rotate(f.vx / size.width * 40f, Offset(f.x, f.y)) { drawButterfly(Offset(f.x, f.y), s * 0.9f, f.color, sin(t * (10f + f.flee * 20f) + f.phase)) }
    m.parts.draw(this)
    if (t - m.lastTouch > 7f) m.flowers.getOrNull(((t / 3f).toInt()) % m.flowers.size.coerceAtLeast(1))?.let { drawHand(Offset(it.fx * size.width, g - it.hf * size.height), mm * 0.045f, abs(sin(t * 3f))) }
  }
}
