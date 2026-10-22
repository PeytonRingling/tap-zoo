package com.peyton.toddlergame

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import kotlin.math.*
import kotlin.random.Random

private class Body(var x: Float, var y: Float, var vx: Float, var vy: Float, val rf: Float, val kind: Int, val color: Color) { var rot = 0f; var hit = -10f; var r = 0f }
private class Rocket(val x: Float, val y0: Float, val ty: Float, val t0: Float, val color: Color)

private class SkyModel {
  val bodies = List(9) { Body(Random.nextFloat(), Random.nextFloat() * 0.5f, 0f, 0f, 0.05f + Random.nextFloat() * 0.035f, it % 3, RAINBOW[it % 8]) }
  val rockets = ArrayList<Rocket>(); val parts = Particles(700).apply { gravity = 250f }
  val stars = List(90) { Offset(Random.nextFloat(), Random.nextFloat()) }
  var w = 1f; var h = 1f; var t = 0f; var gx = 0f; var gy = 9.8f; var init = false; var lastBonk = 0f; var lastTouch = 0f
  fun step(dt: Float) {
    if (w < 2f) return
    val m = min(w, h)
    if (!init) { init = true; bodies.forEach { it.x *= w; it.y *= h } }
    val G = h * 1.6f / 9.8f
    for (b in bodies) {
      b.r = b.rf * m
      b.vx += gx * G * dt; b.vy += gy * G * dt; b.vx *= (1 - 0.3f * dt); b.vy *= (1 - 0.3f * dt)
      b.x += b.vx * dt; b.y += b.vy * dt; b.rot += b.vx * dt / b.r * 57f
      var impact = 0f
      if (b.x < b.r) { impact = max(impact, -b.vx); b.x = b.r; b.vx = -b.vx * 0.7f }
      if (b.x > w - b.r) { impact = max(impact, b.vx); b.x = w - b.r; b.vx = -b.vx * 0.7f }
      if (b.y < b.r) { impact = max(impact, -b.vy); b.y = b.r; b.vy = -b.vy * 0.7f }
      if (b.y > h - b.r) { impact = max(impact, b.vy); b.y = h - b.r; b.vy = -b.vy * 0.7f }
      if (impact > h * 0.35f && t - lastBonk > 0.09f) { lastBonk = t; Audio.play(Sfx.plink(Sfx.PENTA[(b.kind * 3 + bodies.indexOf(b)) % 10] * 2, 0.4), (impact / h).coerceIn(0.15f, 0.5f)) }
    }
    for (i in bodies.indices) for (j in i + 1 until bodies.size) {
      val a = bodies[i]; val b = bodies[j]; val dx = b.x - a.x; val dy = b.y - a.y; val d = hypot(dx, dy); val md = a.r + b.r
      if (d in 0.01f..md) { val nx = dx / d; val ny = dy / d; val o = (md - d) / 2; a.x -= nx * o; a.y -= ny * o; b.x += nx * o; b.y += ny * o
        val rv = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny; if (rv < 0) { val imp = -rv * 0.9f; a.vx -= imp * nx; a.vy -= imp * ny; b.vx += imp * nx; b.vy += imp * ny } }
    }
    val done = rockets.filter { t - it.t0 > 0.6f }; rockets.removeAll(done.toSet())
    for (r in done) { Audio.play(Sfx.boom, 0.55f); Fx.tap(true)
      parts.burst(r.x, r.ty, 60, listOf(r.color, Color.White, RAINBOW.random()), kind = if (Random.nextBoolean()) 1 else 0, speed = m * 0.9f, size = m * 0.014f, up = 0f, decay = 0.7f) }
    parts.step(dt)
  }
  fun tap(p: Offset) {
    lastTouch = t
    val b = bodies.firstOrNull { hypot(it.x - p.x, it.y - p.y) < it.r * 1.4f }
    if (b != null) { b.hit = t; b.vy = -h * 1.3f; b.vx += (Random.nextFloat() - 0.5f) * w; Audio.play(Sfx.boing(400.0 + b.kind * 150)); Fx.tap(); parts.burst(b.x, b.y, 12, kind = 1, speed = 500f, size = b.r * 0.25f) }
    else if (rockets.size < 5) { rockets += Rocket(p.x, h, p.y, t, RAINBOW.random()); Audio.play(Sfx.whoosh, 0.4f) }
  }
}

@Composable
fun SkyScreen() {
  val m = remember { SkyModel() }
  val ctx = LocalContext.current
  DisposableEffect(Unit) {
    val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    @Suppress("DEPRECATION") val disp = (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    val l = object : SensorEventListener {
      override fun onSensorChanged(e: SensorEvent) {
        val ax = e.values[0]; val ay = e.values[1]
        when (disp.rotation) { 1 -> { m.gx = ay; m.gy = ax }; 2 -> { m.gx = ax; m.gy = -ay }; 3 -> { m.gx = -ay; m.gy = -ax }; else -> { m.gx = -ax; m.gy = ay } }
      }
      override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }
    sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_GAME) }
    onDispose { sm.unregisterListener(l) }
  }
  val time = rememberTicker { dt, t -> m.t = t; m.step(dt) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }.multiTouch(Unit, onDown = { _, p -> m.tap(p) })) {
    val t = time.value; m.w = size.width; m.h = size.height; val mm = min(size.width, size.height)
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0A1440), Color(0xFF243B8A), Color(0xFF5B4BA8))))
    m.stars.forEachIndexed { i, s -> drawStar(Offset(s.x * size.width, s.y * size.height), 3f + (i % 4) * 2f, Color.White.copy(alpha = 0.35f + 0.35f * sin(t * (1 + i % 3) + i))) }
    val mc = Offset(size.width * 0.8f, size.height * 0.14f); val mr = mm * 0.09f
    drawCircle(Color(0xFFFFF4B8).copy(alpha = 0.18f), mr * 1.6f, mc); drawCircle(Color(0xFFFFF1A8), mr, mc)
    drawCircle(Color(0xFF6A5A2A), mr * 0.08f, mc + Offset(-mr * 0.3f, -mr * 0.1f)); drawCircle(Color(0xFF6A5A2A), mr * 0.08f, mc + Offset(mr * 0.3f, -mr * 0.1f))
    drawArc(Color(0xFF6A5A2A), 20f, 140f, false, mc + Offset(-mr * 0.3f, -mr * 0.05f), androidx.compose.ui.geometry.Size(mr * 0.6f, mr * 0.45f), style = Stroke(mr * 0.07f))
    for (r in m.rockets) { val k = (t - r.t0) / 0.6f; val y = r.y0 + (r.ty - r.y0) * (1 - (1 - k) * (1 - k))
      drawCircle(r.color, mm * 0.012f, Offset(r.x, y)); for (j in 1..6) drawCircle(Color(0xFFFFE680).copy(alpha = 0.5f - j * 0.07f), mm * 0.008f, Offset(r.x, y + j * mm * 0.02f)) }
    for (b in m.bodies) { if (b.r <= 0f) continue; val c = Offset(b.x, b.y); val glow = (1 - (t - b.hit)).coerceIn(0f, 1f)
      if (glow > 0) drawCircle(b.color.copy(alpha = 0.4f * glow), b.r * 1.6f, c)
      when (b.kind) {
        0 -> rotate(b.rot, c) { drawStar(c, b.r * 1.15f, Color(0xFFFFD23F)); drawCircle(Color(0xFF5A3A00), b.r * 0.08f, c + Offset(-b.r * 0.2f, 0f)); drawCircle(Color(0xFF5A3A00), b.r * 0.08f, c + Offset(b.r * 0.2f, 0f)) }
        1 -> rotate(b.rot, c) { drawCircle(b.color, b.r, c); drawArc(Color.White, -30f, 60f, true, c - Offset(b.r, b.r), androidx.compose.ui.geometry.Size(b.r * 2, b.r * 2)); drawArc(Color.White, 150f, 60f, true, c - Offset(b.r, b.r), androidx.compose.ui.geometry.Size(b.r * 2, b.r * 2)) }
        else -> { drawCircle(Brush.radialGradient(listOf(Color.White, b.color), c - Offset(b.r * 0.4f, b.r * 0.4f), b.r * 1.5f), b.r, c) }
      }
    }
    m.parts.draw(this)
    if (t - m.lastTouch > 7f) drawHand(Offset(size.width * 0.4f, size.height * 0.3f), mm * 0.05f, abs(sin(t * 3f)))
  }
}
