package com.peyton.toddlergame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.*
import kotlin.random.Random

internal class Pt(val x: Float, val y: Float, val t: Float, val hue: Float)
internal class Trail(var hue: Float) { val pts = ArrayList<Pt>(); var lastNoteT = -1f; var noteX = 0f; var noteY = 0f; var done = false }

internal class PaintModel(private val sink: SoundSink = GameSound, private val rnd: Random = Random.Default) {
  val trails = ArrayList<Trail>(); val active = HashMap<Long, Trail>()
  val parts = Particles().apply { gravity = -60f }
  val stars = List(70) { Offset(rnd.nextFloat(), rnd.nextFloat()) }
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = 0f; var hueSeed = 0f
  fun note(tr: Trail, p: Offset) {
    val idx = ((1 - p.y / h) * Sfx.PENTA.size).toInt().coerceIn(0, Sfx.PENTA.size - 1)
    sink.play(Sfx.bells[idx], 0.35f); tr.lastNoteT = t; tr.noteX = p.x; tr.noteY = p.y
  }
  fun down(id: Long, p: Offset) {
    lastTouch = t; hueSeed = (hueSeed + 47f) % 360f
    val tr = Trail(hueSeed); tr.pts += Pt(p.x, p.y, t, tr.hue); trails += tr; active[id] = tr
    note(tr, p); sink.haptic(); parts.burst(p.x, p.y, 10, kind = 1, speed = 350f, size = 14f, up = 0f, decay = 1.2f)
  }
  fun move(id: Long, p: Offset) {
    lastTouch = t
    val tr = active[id] ?: return; val last = tr.pts.lastOrNull() ?: return
    val d = hypot(p.x - last.x, p.y - last.y); if (d < 5f) return
    tr.hue = (tr.hue + d * 0.3f) % 360f; tr.pts += Pt(p.x, p.y, t, tr.hue)
    if (hypot(p.x - tr.noteX, p.y - tr.noteY) > min(w, h) * 0.08f && t - tr.lastNoteT > 0.11f) note(tr, p)
    if (rnd.nextFloat() < 0.35f) parts.burst(p.x, p.y, 1, listOf(Color.hsv(tr.hue, 0.5f, 1f), Color.White), kind = 1, speed = 120f, size = 10f, up = 0f, decay = 1.3f)
  }
  fun up(id: Long) { active.remove(id)?.done = true }
  fun step(dt: Float) {
    for (tr in trails) while (tr.pts.isNotEmpty() && t - tr.pts[0].t > 4f) tr.pts.removeAt(0)
    trails.removeAll { it.done && it.pts.isEmpty() }
    var total = trails.sumOf { it.pts.size }
    while (total > 1800) { val tr = trails.first { it.pts.isNotEmpty() }; tr.pts.removeAt(0); total-- }
    parts.step(dt)
  }
}

@Composable
fun PaintScreen() {
  val m = remember { PaintModel() }
  val time = rememberTicker { dt, t -> m.t = t; m.step(dt) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }.multiTouch(Unit, onDown = m::down, onMove = m::move, onUp = m::up)) {
    val t = time.value; m.w = size.width; m.h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0B2E), Color(0xFF241050), Color(0xFF3A0F4F))))
    m.stars.forEachIndexed { i, s -> drawCircle(Color.White.copy(alpha = 0.25f + 0.25f * sin(t * 2f + i)), 2.5f + (i % 3), Offset(s.x * size.width, s.y * size.height)) }
    val base = min(size.width, size.height) * 0.035f
    for (tr in m.trails) {
      val pts = tr.pts
      for (i in 1 until pts.size) {
        val a = pts[i - 1]; val b = pts[i]; val age = t - b.t
        val al = (1f - (age - 2.5f) / 1.5f).coerceIn(0f, 1f); val col = Color.hsv(b.hue, 0.85f, 1f)
        val s = Offset(a.x, a.y); val e = Offset(b.x, b.y)
        drawLine(col.copy(alpha = 0.12f * al), s, e, base * 2.6f, StrokeCap.Round)
        drawLine(col.copy(alpha = 0.4f * al), s, e, base * 1.3f, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.9f * al), s, e, base * 0.4f, StrokeCap.Round)
      }
      if (pts.size == 1) drawCircle(Color.hsv(pts[0].hue, 0.85f, 1f).copy(alpha = 0.5f), base * 1.3f, Offset(pts[0].x, pts[0].y))
    }
    m.parts.draw(this)
    if (t - m.lastTouch > 6f) { val c = Offset(size.width / 2 + cos(t * 1.5f) * base * 6, size.height / 2 + sin(t * 3f) * base * 3)
      drawCircle(Color.hsv((t * 60f) % 360f, 0.7f, 1f).copy(alpha = 0.5f), base, c); drawHand(c, base * 1.6f, 1f) }
  }
}
