package com.peyton.toddlergame

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/** Design system: refined pastel + vibrant palette and soft-3D ("clay") drawing helpers. */
object Pal {
  val ink = Color(0xFF2D2A4A); val shadow = Color(0xFF3A2D6B)
  val coral = Color(0xFFFF6B6B); val tangerine = Color(0xFFFF9F43); val sun = Color(0xFFFFD43B); val lime = Color(0xFF94D82D)
  val mint = Color(0xFF38D9A9); val sky = Color(0xFF4CC9F0); val blue = Color(0xFF5B7CFA); val grape = Color(0xFF9B5DE5); val pink = Color(0xFFF15BB5)
  val vivid = listOf(coral, tangerine, sun, lime, mint, sky, blue, grape, pink)
  val cream = Color(0xFFFFF7EE); val lilac = Color(0xFFEDE7FF); val blush = Color(0xFFFFE5F1); val ice = Color(0xFFDDF3FF)
}

fun Color.lighten(k: Float) = Color(red + (1 - red) * k, green + (1 - green) * k, blue + (1 - blue) * k, alpha)
fun Color.darken(k: Float) = Color(red * (1 - k), green * (1 - k), blue * (1 - k), alpha)

private val blurPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
private val blurCache = HashMap<Int, BlurMaskFilter>()

/** Real gaussian soft shadow (BlurMaskFilter) behind a rounded rect. */
fun DrawScope.softShadow(rect: Rect, radius: Float, blur: Float, color: Color = Pal.shadow.copy(alpha = 0.22f)) {
  val b = blur.toInt().coerceIn(1, 200)
  blurPaint.color = color.toArgb(); blurPaint.maskFilter = blurCache.getOrPut(b) { BlurMaskFilter(b.toFloat(), BlurMaskFilter.Blur.NORMAL) }
  drawContext.canvas.nativeCanvas.drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, radius, radius, blurPaint)
}

/** Glossy soft-3D sphere. */
fun DrawScope.clayCircle(c: Offset, r: Float, color: Color, shadow: Boolean = true) {
  if (shadow) softShadow(Rect(c.x - r * 0.85f, c.y - r * 0.6f, c.x + r * 0.85f, c.y + r * 1.15f), r, r * 0.28f)
  drawCircle(Brush.radialGradient(listOf(color.lighten(0.45f), color, color.darken(0.18f)), c + Offset(-r * 0.35f, -r * 0.45f), r * 1.6f), r, c)
  drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, color.darken(0.25f).copy(alpha = 0.35f)), c, r), r, c)
  rotate(-30f, c + Offset(-r * 0.38f, -r * 0.45f)) { drawOval(Color.White.copy(alpha = 0.42f), c + Offset(-r * 0.62f, -r * 0.62f), Size(r * 0.5f, r * 0.32f)) }
}

/** Soft-3D rounded card: drop shadow, top light, bottom shade, inner sheen. */
fun DrawScope.clayRect(rect: Rect, radius: Float, color: Color, elevation: Float = 1f) {
  if (elevation > 0f) softShadow(Rect(rect.left + radius * 0.15f, rect.top + radius * 0.35f * elevation, rect.right - radius * 0.15f, rect.bottom + radius * 0.3f * elevation), radius, radius * 0.5f * elevation)
  val cr = CornerRadius(radius)
  drawRoundRect(Brush.verticalGradient(listOf(color.lighten(0.22f), color, color.darken(0.1f)), rect.top, rect.bottom), rect.topLeft, rect.size, cr)
  val inset = radius * 0.14f
  drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0f)), rect.top, rect.top + rect.height * 0.5f),
    Offset(rect.left + inset, rect.top + inset * 0.6f), Size(rect.width - inset * 2, rect.height * 0.45f), CornerRadius(radius * 0.8f))
  drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, color.darken(0.3f).copy(alpha = 0.25f)), rect.bottom - rect.height * 0.25f, rect.bottom), rect.topLeft, rect.size, cr)
}

/** Modern mesh-like background: vertical gradient plus large soft colour blobs. */
fun DrawScope.meshBg(top: Color, bottom: Color, blobs: List<Pair<Color, Offset>>, t: Float = 0f) {
  drawRect(Brush.verticalGradient(listOf(top, bottom)))
  val m = maxOf(size.width, size.height)
  blobs.forEachIndexed { i, (c, f) ->
    val p = Offset(size.width * f.x + kotlin.math.sin(t * 0.2f + i) * m * 0.03f, size.height * f.y + kotlin.math.cos(t * 0.17f + i) * m * 0.03f)
    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.55f), c.copy(alpha = 0f)), p, m * 0.45f), m * 0.45f, p)
  }
}

/** Puffy soft cloud with gentle shading. */
fun DrawScope.softCloud(c: Offset, s: Float, alpha: Float = 0.95f) {
  val col = Color.White.copy(alpha = alpha)
  softShadow(Rect(c.x - s * 1.0f, c.y - s * 0.1f, c.x + s * 1.0f, c.y + s * 0.55f), s * 0.3f, s * 0.25f, Pal.shadow.copy(alpha = 0.08f * alpha))
  drawCircle(col, s * 0.45f, c + Offset(-s * 0.55f, s * 0.08f)); drawCircle(col, s * 0.62f, c + Offset(-s * 0.05f, -s * 0.18f))
  drawCircle(col, s * 0.48f, c + Offset(s * 0.55f, s * 0.05f)); drawRoundRect(col, c + Offset(-s * 1.0f, -s * 0.05f), Size(s * 2f, s * 0.5f), CornerRadius(s * 0.25f))
}

fun DrawScope.hills(color: Color, baseY: Float, amp: Float, phase: Float) {
  val p = Path(); p.moveTo(0f, size.height); p.lineTo(0f, baseY)
  val n = 6; val w = size.width
  for (i in 0 until n) { val x0 = w * i / n; val x1 = w * (i + 1) / n; p.quadraticBezierTo((x0 + x1) / 2, baseY - amp * (if ((i + phase.toInt()) % 2 == 0) 1f else 0.4f), x1, baseY) }
  p.lineTo(w, size.height); p.close()
  drawPath(p, Brush.verticalGradient(listOf(color.lighten(0.15f), color.darken(0.05f)), baseY - amp, size.height))
}
