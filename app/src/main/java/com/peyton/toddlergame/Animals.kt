package com.peyton.toddlergame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
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
import kotlin.math.*
import kotlin.random.Random

enum class Critter(val body: Color) {
  DOG(Color(0xFFC68642)), CAT(Color(0xFFFFA94D)), PIG(Color(0xFFFFB3C6)), COW(Color(0xFFF7F7F7)),
  FROG(Color(0xFF7BD66B)), LION(Color(0xFFFFD166)), CHICK(Color(0xFFFFE45E)), DUCK(Color(0xFFFFFFFF))
}

private val INK = Color(0xFF3B2A20)

private fun lighter(c: Color) = Color(min(1f, c.red + 0.15f), min(1f, c.green + 0.15f), min(1f, c.blue + 0.15f))
private fun darker(c: Color, k: Float = 0.75f) = Color(c.red * k, c.green * k, c.blue * k)
private fun DrawScope.tri(a: Offset, b: Offset, c: Offset, color: Color) { drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }, color) }

/** Hand-drawn critter face. look: -1..1 direction pupils point, blink 0..1, mouth 0..1 open. */
fun DrawScope.drawCritter(k: Critter, c: Offset, r: Float, look: Offset, blink: Float, mouth: Float) {
  val body = k.body
  fun o(x: Float, y: Float) = c + Offset(x * r, y * r)
  // ears / mane behind head
  when (k) {
    Critter.DOG -> for (s in listOf(-1f, 1f)) rotate(s * -18f, o(s * 0.85f, -0.5f)) { drawOval(darker(body, 0.6f), o(s * 0.85f - 0.28f, -0.75f), Size(r * 0.56f, r * 1.25f)) }
    Critter.CAT -> for (s in listOf(-1f, 1f)) { tri(o(s * 0.85f, -0.35f), o(s * 0.85f, -1.2f), o(s * 0.2f, -0.85f), body); tri(o(s * 0.75f, -0.5f), o(s * 0.78f, -1.0f), o(s * 0.38f, -0.8f), Color(0xFFFF9EB5)) }
    Critter.PIG -> for (s in listOf(-1f, 1f)) tri(o(s * 0.9f, -0.4f), o(s * 0.95f, -1.1f), o(s * 0.3f, -0.85f), darker(body, 0.9f))
    Critter.COW -> for (s in listOf(-1f, 1f)) { drawOval(Color(0xFFE8D2A6), o(s * 0.55f - 0.12f, -1.15f), Size(r * 0.24f, r * 0.5f)); drawOval(Color(0xFF4A4A4A), o(if (s < 0) -1.45f else 0.85f, -0.55f), Size(r * 0.6f, r * 0.32f)) }
    Critter.FROG -> for (s in listOf(-1f, 1f)) drawCircle(body, r * 0.34f, o(s * 0.42f, -0.75f))
    Critter.LION -> { drawCircle(Color(0xFFD9741A), r * 1.3f, c); for (i in 0 until 14) { val a = i * 2 * PI / 14; drawCircle(Color(0xFFE88A2A), r * 0.36f, o((cos(a) * 1.2).toFloat(), (sin(a) * 1.2).toFloat())) }
      for (s in listOf(-1f, 1f)) drawCircle(darker(body, 0.85f), r * 0.24f, o(s * 0.72f, -0.78f)) }
    Critter.DUCK -> drawOval(Color(0xFFEDEDF5), o(-0.12f, -1.25f), Size(r * 0.24f, r * 0.4f))
    Critter.CHICK -> for (i in -1..1) rotate(i * 25f, o(0f, -0.95f)) { drawOval(Color(0xFFFFC300), o(-0.08f, -1.35f), Size(r * 0.16f, r * 0.45f)) }
  }
  // head with soft shading
  drawCircle(Brush.radialGradient(listOf(lighter(body), body, darker(body, 0.9f)), center = o(-0.3f, -0.35f), radius = r * 1.6f), r, c)
  if (k == Critter.COW) clipPath(Path().apply { addOval(Rect(c, r)) }) {
    drawCircle(Color(0xFF3A3A3A), r * 0.35f, o(0.7f, -0.55f)); drawCircle(Color(0xFF3A3A3A), r * 0.25f, o(-0.85f, 0.15f))
  }
  // cheeks
  for (s in listOf(-1f, 1f)) drawCircle(Color(0xFFFF6F91).copy(alpha = 0.35f), r * 0.15f, o(s * 0.62f, 0.25f))
  // eyes
  val eyeY = if (k == Critter.FROG) -0.75f else -0.15f; val er = r * (if (k == Critter.FROG) 0.24f else 0.2f)
  for (s in listOf(-1f, 1f)) {
    val p = o(s * (if (k == Critter.FROG) 0.42f else 0.35f), eyeY)
    val h = er * 2 * (1 - blink).coerceAtLeast(0.08f)
    if (blink < 0.6f) {
      drawOval(Color.White, Offset(p.x - er, p.y - h / 2), Size(er * 2, h))
      val pr = er * 0.58f; val lp = p + Offset(look.x * er * 0.42f, look.y * er * 0.42f * (1 - blink))
      drawCircle(Color(0xFF1E1E2A), pr * (1 - blink * 0.5f), lp); drawCircle(Color.White, pr * 0.35f, lp + Offset(-pr * 0.3f, -pr * 0.35f))
    } else drawArc(INK, 200f, 140f, false, Offset(p.x - er, p.y - er * 0.5f), Size(er * 2, er * 1.3f), style = Stroke(er * 0.3f, cap = StrokeCap.Round))
  }
  // snout / nose
  var mouthY = 0.55f
  when (k) {
    Critter.DOG -> { drawOval(Color(0xFFF2D3A9), o(-0.42f, 0.08f), Size(r * 0.84f, r * 0.62f)); drawOval(INK, o(-0.17f, 0.12f), Size(r * 0.34f, r * 0.22f)); mouthY = 0.52f }
    Critter.CAT -> { tri(o(-0.12f, 0.18f), o(0.12f, 0.18f), o(0f, 0.32f), Color(0xFFFF7FA0))
      for (s in listOf(-1f, 1f)) for (j in 0..1) drawLine(INK.copy(alpha = 0.6f), o(s * 0.3f, 0.3f + j * 0.1f), o(s * 0.95f, 0.22f + j * 0.2f), r * 0.03f, StrokeCap.Round); mouthY = 0.45f }
    Critter.PIG -> { drawOval(darker(body, 0.88f), o(-0.36f, 0.12f), Size(r * 0.72f, r * 0.46f)); for (s in listOf(-1f, 1f)) drawOval(Color(0xFFB0506E), o(s * 0.14f - 0.06f, 0.24f), Size(r * 0.12f, r * 0.2f)); mouthY = 0.7f }
    Critter.COW -> { drawOval(Color(0xFFFFC2CF), o(-0.6f, 0.22f), Size(r * 1.2f, r * 0.62f)); for (s in listOf(-1f, 1f)) drawOval(Color(0xFFB0506E), o(s * 0.28f - 0.07f, 0.35f), Size(r * 0.14f, r * 0.18f)); mouthY = 0.66f }
    Critter.FROG -> mouthY = 0.35f
    Critter.LION -> { drawOval(Color(0xFFFFF0C8), o(-0.38f, 0.12f), Size(r * 0.76f, r * 0.55f)); tri(o(-0.15f, 0.14f), o(0.15f, 0.14f), o(0f, 0.3f), Color(0xFF7A4A2A)); mouthY = 0.5f }
    Critter.DUCK -> { drawOval(Color(0xFFFF9F1C), o(-0.4f, 0.1f), Size(r * 0.8f, r * 0.34f)); drawOval(Color(0xFFE8850A), o(-0.32f, 0.3f), Size(r * 0.64f, r * 0.18f)); for (sd in listOf(-1f, 1f)) drawCircle(Color(0xFFB0600A), r * 0.035f, o(sd * 0.1f, 0.18f)); mouthY = 0.62f }
    Critter.CHICK -> { tri(o(-0.2f, 0.12f), o(0.2f, 0.12f), o(0f, 0.4f), Color(0xFFFF8C1A)); mouthY = 0.48f }
  }
  val mw = if (k == Critter.FROG) 0.9f else 0.46f
  if (mouth < 0.1f) drawArc(INK, 20f, 140f, false, o(-mw / 2, mouthY - 0.2f), Size(r * mw, r * 0.28f), style = Stroke(r * 0.06f, cap = StrokeCap.Round))
  else {
    val mh = r * (0.1f + 0.32f * mouth)
    drawOval(Color(0xFF8B1E3F), o(-mw * 0.38f, mouthY - 0.06f), Size(r * mw * 0.76f, mh))
    drawOval(Color(0xFFFF7A8A), Offset(c.x - r * mw * 0.2f, c.y + r * (mouthY - 0.06f) + mh * 0.55f), Size(r * mw * 0.4f, mh * 0.4f))
  }
}

private class Friend(val k: Critter) { var hit = -10f; var nextBlink = 1f + Random.nextFloat() * 3f; var blinkAt = -10f }

private class AnimalsModel {
  val friends = Critter.entries.map { Friend(it) }
  val parts = Particles()
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = 0f
  val touches = HashMap<Long, Offset>()
  fun rects() = Layout.grid(friends.size, w, h, min(w, h) * 0.04f, square = true)
  fun tap(p: Offset) {
    lastTouch = t
    val i = rects().indexOfFirst { it.contains(p) }
    if (i < 0) { parts.burst(p.x, p.y, 6, kind = 1, speed = 300f, size = 12f); return }
    val f = friends[i]; val r = rects()[i]
    if (t - f.hit > 0.25f) { AnimalSounds.play(f.k); Fx.tap() }
    f.hit = t
    parts.burst(r.center.x, r.center.y - r.height * 0.2f, 18, kind = if (i % 2 == 0) 2 else 1, speed = 900f, size = r.width * 0.04f)
  }
}

@Composable
fun AnimalsScreen() {
  val m = remember { AnimalsModel() }
  val time = rememberTicker { dt, t -> m.t = t; m.parts.step(dt) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }.multiTouch(Unit, onDown = { id, p -> m.touches[id] = p; m.tap(p) }, onMove = { id, p -> m.touches[id] = p; m.lastTouch = m.t }, onUp = { m.touches.remove(it) })) {
    val t = time.value; m.w = size.width; m.h = size.height
    meshBg(Color(0xFFC4E8FF), Color(0xFFE6F8DA), listOf(Pal.blush to Offset(0.1f, 0.2f), Color(0xFFFFF3C4) to Offset(0.9f, 0.05f)), t)
    for (i in 0 until 3) softCloud(Offset(((t * 12f + i * size.width / 2.3f) % (size.width * 1.5f)) - size.width * 0.25f, size.height * (0.06f + 0.05f * i)), size.minDimension * 0.06f, 0.7f)
    hills(Color(0xFFC6EBB8), size.height * 0.62f, size.height * 0.06f, 0f)
    drawRect(Brush.verticalGradient(listOf(Color(0xFFB4E3A0), Color(0xFF94D486)), size.height * 0.62f, size.height), Offset(0f, size.height * 0.62f), Size(size.width, size.height * 0.38f))
    val finger = m.touches.values.lastOrNull()
    val rects = m.rects(); val idle = t - m.lastTouch > 7f; val attract = ((t / 2.5f).toInt()) % m.friends.size
    m.friends.forEachIndexed { i, f ->
      val rc = rects[i]; val r = rc.width * 0.34f; var c = Offset(rc.center.x, rc.center.y + rc.height * 0.04f)
      if (t > f.nextBlink) { f.blinkAt = t; f.nextBlink = t + 2f + Random.nextFloat() * 4f }
      val bt = t - f.blinkAt; val blink = if (bt < 0.18f) sin(bt / 0.18f * PI.toFloat()) else 0f
      val since = t - f.hit; val react = (1 - since / 0.9f).coerceIn(0f, 1f)
      val hop = if (since < 0.9f) -abs(sin(since * PI.toFloat() / 0.45f)) * r * 0.35f * react else 0f
      val sq = if (since < 0.9f) 0.15f * sin(since * 22f) * react else 0.025f * sin(t * 2.2f + i)
      val wig = if (idle && i == attract) 8f * sin(t * 14f) else 0f
      val target = finger ?: Offset(c.x + cos(t * 0.6f + i) * r * 3, c.y + sin(t * 0.8f + i * 2) * r * 2)
      val d = target - c; val len = max(d.getDistance(), r)
      softShadow(Rect(c.x - r * 0.75f * (1 + hop / r * 0.5f), c.y + r * 0.98f, c.x + r * 0.75f * (1 + hop / r * 0.5f), c.y + r * 1.22f), r * 0.2f, r * 0.12f, Pal.shadow.copy(alpha = 0.2f))
      c += Offset(0f, hop)
      if (react > 0f) drawCircle(Brush.radialGradient(listOf(Pal.vivid[i].copy(alpha = 0.45f * react), Color.Transparent), c, r * 1.6f), r * 1.6f, c)
      rotate(wig, c) { scale(1 + sq, 1 - sq, c) {
        drawCritter(f.k, c, r, Offset(d.x / len, d.y / len), if (since < 0.6f) 0.75f else blink, if (since < 0.8f) react else 0f)
      } }
      if (idle && i == attract) drawHand(c + Offset(r * 0.4f, r * 0.4f), r * 0.35f, abs(sin(t * 3f)))
    }
    m.parts.draw(this)
  }
}
