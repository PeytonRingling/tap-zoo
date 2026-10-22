package com.peyton.toddlergame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.*
import kotlin.random.Random

private class Bub(var x: Float, var y: Float, val r: Float, val color: Color, val phase: Float, val golden: Boolean, val speed: Float)
private class Ring(val x: Float, val y: Float, val r: Float, val t0: Float, val color: Color)
private class Pending(val b: Bub, val at: Float, val level: Int)

private class BubbleModel {
  val bubs = ArrayList<Bub>(); val rings = ArrayList<Ring>(); val pending = ArrayList<Pending>()
  val parts = Particles().apply { gravity = 600f }
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = 0f
  fun spawn(atBottom: Boolean = true, x: Float? = null, y: Float? = null, rr: Float? = null) {
    val m = min(w, h); val golden = rr == null && Random.nextFloat() < 0.07f
    val r = rr ?: (if (golden) m * 0.13f else m * (0.05f + Random.nextFloat() * Random.nextFloat() * 0.13f))
    bubs += Bub(x ?: (r + Random.nextFloat() * (w - 2 * r)), y ?: (if (atBottom) h + r + Random.nextFloat() * h * 0.3f else Random.nextFloat() * h),
      r, if (golden) Color(0xFFFFC83D) else RAINBOW.random(), Random.nextFloat() * 6f, golden, h * (0.06f + 0.1f * (1 - r / (m * 0.18f))))
  }
  fun step(dt: Float) {
    if (w < 2f) return
    val target = (w * h / (min(w, h) * min(w, h) * 0.08f)).toInt().coerceIn(9, 24)
    if (bubs.isEmpty()) repeat(target) { spawn(atBottom = false) }
    while (bubs.size < target) spawn()
    for (b in bubs) { b.y -= b.speed * dt; b.x += sin(t * 1.3f + b.phase) * 25f * dt }
    bubs.removeAll { it.y < -it.r * 2 }
    val due = pending.filter { it.at <= t }; pending.removeAll(due.toSet()); due.forEach { pop(it.b, it.level) }
    rings.removeAll { t - it.t0 > 0.4f }
    parts.step(dt)
  }
  fun pop(b: Bub, level: Int) {
    if (!bubs.remove(b)) return
    rings += Ring(b.x, b.y, b.r, t, b.color)
    parts.burst(b.x, b.y, (8 + b.r / 8).toInt().coerceAtMost(30), listOf(b.color, Color.White), speed = b.r * 6f, size = b.r * 0.12f, up = 100f, decay = 1.8f)
    val m = min(w, h)
    Audio.play(Sfx.pop(1500.0 * sqrt(m * 0.08 / b.r) * 1.12.pow(level)), 0.6f)
    if (b.golden) {
      Audio.play(Sfx.chime, 0.5f); parts.burst(b.x, b.y, 20, kind = 1, speed = 900f, size = m * 0.025f)
      repeat(7) { val a = it * 2 * PI / 7; spawn(x = b.x + (cos(a) * b.r).toFloat(), y = b.y + (sin(a) * b.r).toFloat(), rr = m * 0.045f) }
    }
    if (level < 6) bubs.filter { hypot(it.x - b.x, it.y - b.y) < (it.r + b.r) * 1.15f && pending.none { p -> p.b === it } }
      .forEach { pending += Pending(it, t + 0.11f, level + 1) }
  }
  fun tap(p: Offset) {
    lastTouch = t
    val b = bubs.lastOrNull { hypot(it.x - p.x, it.y - p.y) < it.r * 1.25f + 20f }
    if (b != null) { Fx.tap(); pop(b, 0) } else parts.burst(p.x, p.y, 5, listOf(Color.White), speed = 200f, size = 8f)
  }
}

@Composable
fun BubblesScreen() {
  val m = remember { BubbleModel() }
  val time = rememberTicker { dt, t -> m.t = t; m.step(dt) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }.multiTouch(Unit, onDown = { _, p -> m.tap(p) }, onMove = { _, p -> m.bubs.lastOrNull { hypot(it.x - p.x, it.y - p.y) < it.r }?.let { m.pop(it, 0) } })) {
    val t = time.value; m.w = size.width; m.h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF7FE0F5), Color(0xFF4FB3E8), Color(0xFF3A7BD5))))
    for (i in 0 until 5) { val x = size.width * (0.1f + i * 0.2f) + sin(t * 0.3f + i) * 40f
      drawLine(Color.White.copy(alpha = 0.07f), Offset(x, 0f), Offset(x - size.height * 0.3f, size.height), size.width * 0.06f) }
    for (b in m.bubs) {
      val pend = m.pending.any { it.b === b }
      val wob = 1f + 0.04f * sin(t * 3f + b.phase) + (if (pend) 0.1f else 0f); val r = b.r * wob; val c = Offset(b.x, b.y)
      drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.05f), b.color.copy(alpha = 0.18f), b.color.copy(alpha = 0.55f)), c, r), r, c)
      drawCircle(b.color.copy(alpha = 0.9f), r, c, style = Stroke(max(3f, r * 0.06f)))
      drawArc(Color.White.copy(alpha = 0.85f), 200f, 50f, false, Offset(c.x - r * 0.7f, c.y - r * 0.7f), androidx.compose.ui.geometry.Size(r * 1.4f, r * 1.4f), style = Stroke(r * 0.1f))
      drawCircle(Color.White.copy(alpha = 0.9f), r * 0.1f, c + Offset(r * 0.35f, -r * 0.45f))
      if (b.golden) drawStar(c, r * 0.5f, Color(0xFFFFE680), t * 40f)
    }
    for (rg in m.rings) { val k = (t - rg.t0) / 0.4f; drawCircle(rg.color.copy(alpha = 1 - k), rg.r * (1 + k * 0.8f), Offset(rg.x, rg.y), style = Stroke(rg.r * 0.12f * (1 - k) + 2f)) }
    m.parts.draw(this)
    if (t - m.lastTouch > 8f) m.bubs.maxByOrNull { it.r }?.let { drawHand(Offset(it.x, it.y), min(size.width, size.height) * 0.05f, abs(sin(t * 3f))) }
  }
}
