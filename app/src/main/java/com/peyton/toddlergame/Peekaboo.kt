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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.*
import kotlin.random.Random

private class Spot(var critter: Critter, val cover: Int) { var revealAt = -10f; var peekAt = -10f; var giggleAt = -10f; var swapped = true }

/** 0 bush, 1 gift box, 2 blanket, 3 tree stump. [open] 0..1 how far the cover moved aside. */
fun DrawScope.drawCover(kind: Int, r: Rect, open: Float, t: Float) {
  val w = r.width; val h = r.height
  when (kind) {
    0 -> rotate(sin(t * 30f) * 6f * open, Offset(r.center.x, r.bottom)) {
      val g = Color(0xFF3FAE4A); val d = Color(0xFF2E8B3A)
      for ((x, y, s) in listOf(Triple(0.2f, 0.55f, 0.3f), Triple(0.8f, 0.55f, 0.3f), Triple(0.5f, 0.35f, 0.36f), Triple(0.35f, 0.7f, 0.32f), Triple(0.65f, 0.7f, 0.32f)))
        drawCircle(if (s > 0.33f) g else d, w * s * 0.75f, Offset(r.left + w * x, r.top + h * y + h * 0.25f * open))
      for (i in 0 until 4) drawCircle(Color(0xFFFF6B9A), w * 0.035f, Offset(r.left + w * (0.25f + i * 0.17f), r.top + h * (0.4f + (i % 2) * 0.25f) + h * 0.25f * open))
    }
    1 -> {
      val body = Rect(r.left + w * 0.1f, r.top + h * 0.3f, r.right - w * 0.1f, r.bottom)
      drawRoundRect(Color(0xFFFF5D8F), body.topLeft, body.size, CornerRadius(w * 0.04f))
      drawRect(Color(0xFFFFD23F), Offset(body.center.x - w * 0.06f, body.top), Size(w * 0.12f, body.height))
      rotate(-70f * open, Offset(body.left, body.top)) {
        val lidY = body.top - h * 0.12f - h * 0.1f * open
        drawRoundRect(Color(0xFFFF7FA8), Offset(body.left - w * 0.04f, lidY), Size(body.width + w * 0.08f, h * 0.14f), CornerRadius(w * 0.03f))
        drawRect(Color(0xFFFFD23F), Offset(body.center.x - w * 0.06f, lidY), Size(w * 0.12f, h * 0.14f))
        drawCircle(Color(0xFFFFD23F), w * 0.07f, Offset(body.center.x - w * 0.07f, lidY - h * 0.02f)); drawCircle(Color(0xFFFFD23F), w * 0.07f, Offset(body.center.x + w * 0.07f, lidY - h * 0.02f))
      }
    }
    2 -> {
      val top = r.top + h * 0.25f + h * 0.15f * open
      drawRoundRect(Color(0xFF8C7BFF), Offset(r.left + w * 0.05f, top), Size(w * 0.9f, r.bottom - top), CornerRadius(w * 0.15f))
      for (i in 0 until 6) drawCircle(Color.White.copy(alpha = 0.7f), w * 0.04f, Offset(r.left + w * (0.2f + (i % 3) * 0.3f), top + h * (0.15f + (i / 3) * 0.25f)))
    }
    else -> {
      val top = r.top + h * 0.3f
      drawRoundRect(Color(0xFF9C6B3E), Offset(r.left + w * 0.15f, top), Size(w * 0.7f, r.bottom - top), CornerRadius(w * 0.05f))
      drawOval(Color(0xFFE2B27A), Offset(r.left + w * 0.15f, top - h * 0.08f), Size(w * 0.7f, h * 0.16f))
      drawOval(Color(0xFFB98552), Offset(r.left + w * 0.3f, top - h * 0.04f), Size(w * 0.4f, h * 0.08f))
      rotate(-40f * open, Offset(r.left + w * 0.85f, top)) { drawCircle(Color(0xFF6BCB77), w * 0.08f, Offset(r.left + w * 0.9f, top - h * 0.05f)) }
    }
  }
}

private class PeekModel {
  val spots = List(4) { Spot(Critter.entries[it * 2 % Critter.entries.size], it) }
  val parts = Particles()
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = 0f; var nextTease = 3f
  fun rects() = Layout.grid(spots.size, w, h, min(w, h) * 0.05f)
  fun tap(p: Offset) {
    lastTouch = t
    val i = rects().indexOfFirst { it.contains(p) }; if (i < 0) return
    val s = spots[i]; val rc = rects()[i]
    if (t - s.revealAt > 3.2f) {
      s.revealAt = t; s.swapped = false; Audio.play(Sfx.peekaboo); AnimalSounds.play(s.critter, 0.8f, delayMs = 650); Fx.tap(true)
      parts.burst(rc.center.x, rc.top + rc.height * 0.3f, 30, kind = 1, speed = 1000f, size = min(rc.width, rc.height) * 0.04f)
    } else if (t - s.giggleAt > 0.5f) {
      s.giggleAt = t; s.revealAt = max(s.revealAt, t - 0.5f); Audio.play(Sfx.giggle, 0.4f); AnimalSounds.play(s.critter); Fx.tap()
      parts.burst(p.x, p.y, 8, kind = 2, speed = 500f, size = min(rc.width, rc.height) * 0.04f)
    }
  }
  fun step(dt: Float) {
    for (s in spots) if (!s.swapped && t - s.revealAt > 3.3f) { s.swapped = true; s.critter = Critter.entries.filter { it != s.critter }.random() }
    if (t > nextTease) { nextTease = t + 3f + Random.nextFloat() * 2f; spots.filter { t - it.revealAt > 3.3f }.randomOrNull()?.peekAt = t }
    parts.step(dt)
  }
}

private fun easeOutBack(x: Float): Float { val c1 = 1.7f; val c3 = c1 + 1; val y = x - 1; return 1 + c3 * y * y * y + c1 * y * y }

@Composable
fun PeekabooScreen() {
  val m = remember { PeekModel() }
  val time = rememberTicker { dt, t -> m.t = t; m.step(dt) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }.multiTouch(Unit, onDown = { _, p -> m.tap(p) })) {
    val t = time.value; m.w = size.width; m.h = size.height
    meshBg(Color(0xFFFFF1D6), Color(0xFFE2F6D6), listOf(Pal.blush to Offset(0.85f, 0.3f), Pal.lilac to Offset(0.1f, 0.7f)), t)
    for (i in 0 until 3) softCloud(Offset(((t * 15f + i * size.width / 2.5f) % (size.width * 1.4f)) - size.width * 0.2f, size.height * 0.07f), size.minDimension * 0.06f, 0.8f)
    m.rects().forEachIndexed { i, rc ->
      val s = m.spots[i]; val p = t - s.revealAt
      val rise = when { p < 0.4f -> easeOutBack(p / 0.4f); p < 2.9f -> 1f; p < 3.3f -> 1 - (p - 2.9f) / 0.4f; else -> 0f }
      val pk = t - s.peekAt; val tease = if (pk in 0f..1.4f) 0.28f * sin(pk / 1.4f * PI.toFloat()) else 0f
      val y = max(rise, tease)
      val coverRect = Rect(rc.left, rc.top + rc.height * 0.45f, rc.right, rc.bottom)
      val r = min(rc.width * 0.3f, rc.height * 0.22f)
      val hiddenY = coverRect.top + coverRect.height * 0.32f + r * 1.15f; val shownY = coverRect.top - r * 0.5f
      val cy = hiddenY + (shownY - hiddenY) * y
      softShadow(Rect(rc.left + rc.width * 0.1f, rc.bottom - rc.height * 0.06f, rc.right - rc.width * 0.1f, rc.bottom + rc.height * 0.02f), rc.height * 0.04f, rc.height * 0.03f)
      clipRect(rc.left - rc.width, rc.top - rc.height, rc.right + rc.width, coverRect.bottom - rc.height * 0.05f) {
        val g = t - s.giggleAt; val wob = if (g < 0.6f) sin(g * 30f) * 10f * (1 - g / 0.6f) else sin(t * 3f) * 3f * rise
        rotate(wob, Offset(rc.center.x, cy)) {
          drawCritter(s.critter, Offset(rc.center.x, cy), r, Offset(0f, 0.3f), if (g < 0.6f) 0.8f else if (rise > 0.5f) 0f else 0.2f, if (p < 1.2f) rise else if (g < 0.6f) 1f else 0f)
        }
      }
      drawCover(s.cover, coverRect, rise, t)
      if (t - m.lastTouch > 7f && tease > 0.05f) drawHand(coverRect.center, r * 0.4f, abs(sin(t * 3f)))
    }
    m.parts.draw(this)
  }
}
