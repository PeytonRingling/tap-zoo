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

internal enum class StickerKind { ANIMAL, STAR, CAR, HEART, SUN }
internal enum class StickerScene { FARM, OCEAN, SKY }
internal class Sticker(val kind: StickerKind, val critter: Critter, var x: Float, var y: Float, val bornAt: Float, val rot: Float) {
  var pokedAt = -10f; var fadeAt = -1f
  val live get() = fadeAt < 0f
}
internal class Peel(val kind: StickerKind, val critter: Critter, var x: Float, var y: Float)

/** Stickers for an 18-month-old: a friendly scene with a tray of 5 giant stickers along the bottom. Tapping the scene slaps the
 *  glowing "current" sticker there (then the next one glows), so tapping alone fills the page. Dragging a tray sticker peels it and
 *  letting go sticks it. Tapping a placed sticker makes it do its thing. At most [MAX] stay; the oldest shrinks away. Two fingers held
 *  still for [CLEAR_HOLD] s sweep the page clean (a parent gesture; palms are 3+ fingers and don't count). */
internal class StickersModel(private val sink: SoundSink = GameSound, private val rnd: Random = Random.Default) {
  companion object {
    const val MAX = 20; const val FADE = 0.45f; const val CLEAR_HOLD = 2f; const val THWACK_GAP = 0.06f; const val IDLE_HINT = 7f
    val CRITTERS = listOf(Critter.DOG, Critter.CAT, Critter.DUCK, Critter.PIG, Critter.COW, Critter.FROG, Critter.LION, Critter.CHICK)
    val TRAY = StickerKind.entries
  }
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = -100f
  val scene = StickerScene.entries[rnd.nextInt(StickerScene.entries.size)]
  val stickers = ArrayList<Sticker>()
  val peels = HashMap<Long, Peel>()
  val parts = Particles(200)
  var current = 0; private set
  var critterIdx = rnd.nextInt(CRITTERS.size); private set
  var clearedAt = -10f; private set
  private val downAt = HashMap<Long, Offset>()
  private var twoSince = -1f
  private var lastThwack = -10f

  val trayR get() = min(w / 12f, h * 0.085f)
  val trayY get() = h - trayR * 1.4f
  val stickerR get() = trayR * 1.15f
  fun slot(i: Int) = Offset(w * (i + 0.5f) / TRAY.size, trayY)
  val nextCritter get() = CRITTERS[critterIdx]
  /** Lowest a placed sticker's centre may sit, so it never hides behind the tray (its bar starts 1.25 trayR above [trayY]). */
  val floorY get() = trayY - trayR * 1.25f - stickerR * 1.08f

  private fun ok(p: Offset) = p.x.isFinite() && p.y.isFinite() && w >= 2f && h >= 2f
  private fun trayAt(p: Offset) = TRAY.indices.firstOrNull { (slot(it) - p).getDistance() < trayR * 1.3f }
  fun stickerAt(p: Offset) = stickers.lastOrNull { it.live && hypot(it.x - p.x, it.y - p.y) < stickerR * 1.05f }

  fun down(id: Long, p: Offset) {
    if (!ok(p)) return
    lastTouch = t; downAt[id] = p
    val i = trayAt(p)
    when {
      i != null -> { current = i; peels[id] = Peel(TRAY[i], nextCritter, p.x, p.y); sink.play(Sfx.peel, 0.5f); sink.haptic() }
      else -> stickerAt(p)?.let { poke(it) } ?: run { stamp(TRAY[current], p); current = (current + 1) % TRAY.size }
    }
    twoFingers()
  }
  fun move(id: Long, p: Offset) {
    if (!ok(p)) return
    lastTouch = t
    peels[id]?.let { it.x = p.x; it.y = p.y }
    val d = downAt[id] ?: return
    if ((p - d).getDistance() > trayR * 0.6f) { downAt[id] = p; if (twoSince >= 0f) twoSince = t }   // a wiggle restarts the hold
  }
  fun up(id: Long) {
    peels.remove(id)?.let { stamp(it.kind, Offset(it.x, it.y), it.critter) }
    downAt.remove(id); twoFingers()
  }
  private fun twoFingers() { twoSince = if (downAt.size == 2 && peels.isEmpty()) t else -1f }

  private fun stamp(kind: StickerKind, p: Offset, critter: Critter = nextCritter) {
    val r = stickerR
    val s = Sticker(kind, critter, p.x.coerceIn(r, max(r, w - r)), p.y.coerceIn(r, max(r, floorY)), t, (rnd.nextFloat() - 0.5f) * 24f)
    stickers += s
    if (kind == StickerKind.ANIMAL) critterIdx = (CRITTERS.indexOf(critter) + 1) % CRITTERS.size
    if (t - lastThwack >= THWACK_GAP) { lastThwack = t; sink.play(Sfx.thwack, 0.6f) }
    sink.haptic()
    val live = stickers.filter { it.live }
    if (live.size > MAX) live.take(live.size - MAX).forEach { it.fadeAt = t }
  }

  fun poke(s: Sticker) {
    s.pokedAt = t; sink.haptic()
    when (s.kind) {
      StickerKind.ANIMAL -> sink.animal(s.critter, 0.9f, 0)
      StickerKind.CAR -> sink.play(Sfx.honks[0], 0.6f)
      else -> { sink.play(Sfx.bells[rnd.nextInt(Sfx.bells.size)], 0.5f)
        parts.burst(s.x, s.y, 14, listOf(Pal.sun, Pal.pink, Color.White, Pal.sky), kind = 1, speed = stickerR * 3f, size = stickerR * 0.12f, up = stickerR, decay = 1.1f) }
    }
  }

  fun step(dtIn: Float) {
    if (w < 2f || h < 2f) return
    val dt = if (dtIn.isNaN()) 0f else dtIn.coerceIn(0f, 0.1f)
    if (twoSince >= 0f && t - twoSince >= CLEAR_HOLD) {
      twoSince = -1f
      if (stickers.any { it.live }) { clearedAt = t; stickers.forEach { if (it.live) it.fadeAt = t }; sink.play(Sfx.whoosh, 0.5f); sink.haptic(true) }
    }
    stickers.removeAll { !it.live && t - it.fadeAt > FADE }
    parts.step(dt)
  }
}

private val STICKER_INK = Color(0xFF2D2A4A)

/** One die-cut sticker: a white backing disk with the picture on top. */
internal fun DrawScope.drawSticker(kind: StickerKind, critter: Critter, c: Offset, r: Float, t: Float) {
  drawCircle(Pal.shadow.copy(alpha = 0.18f), r * 1.08f, c + Offset(r * 0.05f, r * 0.1f))
  drawCircle(Color.White, r * 1.08f, c)
  when (kind) {
    StickerKind.ANIMAL -> drawCritter(critter, c + Offset(0f, r * 0.05f), r * 0.72f, Offset.Zero, if ((t % 4f) < 0.12f) 1f else 0f, 0.6f)
    StickerKind.STAR -> { drawStar(c + Offset(0f, r * 0.06f), r * 0.95f, Color(0xFFFFB703)); drawStar(c + Offset(0f, r * 0.06f), r * 0.78f, Pal.sun)
      for (d in listOf(-1f, 1f)) drawCircle(STICKER_INK, r * 0.07f, c + Offset(d * r * 0.17f, 0f)) }
    StickerKind.HEART -> { drawHeart(c + Offset(0f, r * 0.08f), r * 0.6f, Pal.coral); drawCircle(Color.White.copy(alpha = 0.6f), r * 0.1f, c + Offset(-r * 0.3f, -r * 0.18f)) }
    StickerKind.SUN -> { for (i in 0 until 10) { val a = i * PI.toFloat() / 5 + t * 0.4f
        drawLine(Pal.tangerine, c + Offset(cos(a), sin(a)) * (r * 0.58f), c + Offset(cos(a), sin(a)) * (r * 0.88f), r * 0.13f, StrokeCap.Round) }
      drawCircle(Pal.sun, r * 0.5f, c)
      for (d in listOf(-1f, 1f)) drawCircle(STICKER_INK, r * 0.065f, c + Offset(d * r * 0.17f, -r * 0.06f))
      drawArc(STICKER_INK, 20f, 140f, false, c + Offset(-r * 0.2f, -r * 0.05f), Size(r * 0.4f, r * 0.3f), style = Stroke(r * 0.05f, cap = StrokeCap.Round)) }
    StickerKind.CAR -> { val L = r * 0.95f; drawVehicle(0, c.x - L * 0.75f, c.y + r * 0.42f, L, Pal.coral, t, 0f) }
  }
}

private fun DrawScope.drawScene(scene: StickerScene, t: Float, mm: Float) {
  val W = size.width; val H = size.height
  when (scene) {
    StickerScene.FARM -> {
      drawRect(Brush.verticalGradient(listOf(Color(0xFF9EDCFF), Color(0xFFE3F6FF)), 0f, H * 0.6f))
      drawCircle(Pal.sun, mm * 0.09f, Offset(W * 0.85f, H * 0.14f))
      drawCloud(Offset(W * 0.25f + sin(t * 0.1f) * 20f, H * 0.14f), mm * 0.12f, Color.White)
      drawCircle(Color(0xFF9BDB6B), W * 0.6f, Offset(W * 0.2f, H * 0.55f + W * 0.6f))
      drawCircle(Color(0xFF7FCC55), W * 0.7f, Offset(W * 0.9f, H * 0.6f + W * 0.7f))
      val fy = H * 0.66f; for (i in 0..(W / (mm * 0.08f)).toInt()) drawRoundRect(Color(0xFFFFFFFF).copy(alpha = 0.85f), Offset(i * mm * 0.08f, fy), Size(mm * 0.025f, mm * 0.09f), CornerRadius(mm * 0.01f))
      drawRect(Color.White.copy(alpha = 0.85f), Offset(0f, fy + mm * 0.025f), Size(W, mm * 0.018f))
      drawRect(Color(0xFF6CC04A), Offset(0f, fy + mm * 0.09f), Size(W, H))
    }
    StickerScene.OCEAN -> {
      drawRect(Brush.verticalGradient(listOf(Color(0xFFBDEBFF), Color(0xFFE9F9FF)), 0f, H * 0.3f))
      drawCircle(Pal.sun, mm * 0.08f, Offset(W * 0.82f, H * 0.12f))
      drawRect(Brush.verticalGradient(listOf(Color(0xFF4CC9F0), Color(0xFF2A7DE1)), H * 0.28f, H), Offset(0f, H * 0.28f), Size(W, H))
      for (row in 0 until 4) { val y = H * (0.34f + row * 0.13f); var x = -mm * 0.1f + (t * 20f * (if (row % 2 == 0) 1 else -1)) % (mm * 0.2f)
        while (x < W + mm * 0.2f) { drawArc(Color.White.copy(alpha = 0.35f), 200f, 140f, false, Offset(x, y), Size(mm * 0.16f, mm * 0.06f), style = Stroke(mm * 0.012f, cap = StrokeCap.Round)); x += mm * 0.2f } }
      drawCircle(Color(0xFFFFE3A3), W * 0.8f, Offset(W * 0.5f, H * 0.9f + W * 0.8f - mm * 0.05f))
    }
    StickerScene.SKY -> {
      drawRect(Brush.verticalGradient(listOf(Color(0xFFB9A8FF), Color(0xFFFFD6EC)), 0f, H))
      val rc = Offset(W * 0.5f, H * 0.75f); val rr = min(W, H * 1.2f) * 0.55f
      listOf(Pal.coral, Pal.tangerine, Pal.sun, Pal.lime, Pal.sky, Pal.grape).forEachIndexed { i, col ->
        drawArc(col.copy(alpha = 0.55f), 180f, 180f, false, rc - Offset(rr - i * mm * 0.03f, rr - i * mm * 0.03f), Size((rr - i * mm * 0.03f) * 2, (rr - i * mm * 0.03f) * 2), style = Stroke(mm * 0.03f)) }
      for (d in listOf(-1f, 1f)) drawCloud(rc + Offset(d * (rr - mm * 0.075f), mm * 0.02f), mm * 0.11f, Color.White)   // rainbow feet
      for (i in 0 until 4) drawCloud(Offset((W * (0.15f + i * 0.27f) + t * 8f * (i + 1)) % (W + mm * 0.3f), H * (0.15f + (i % 2) * 0.35f)), mm * 0.1f, Color.White.copy(alpha = 0.9f))
    }
  }
}

@Composable
fun StickersScreen() { StickersScreenFor(remember { StickersModel() }) }

@Composable
internal fun StickersScreenFor(m: StickersModel) {
  val time = rememberTicker { dt, t -> m.t = t; m.step(dt) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }
    .multiTouch(Unit, onDown = { id, p -> m.down(id, p) }, onMove = { id, p -> m.move(id, p) }, onUp = { m.up(it) })) {
    val t = time.value; m.w = size.width; m.h = size.height; val mm = min(size.width, size.height)
    drawScene(m.scene, t, mm)
    val r = m.stickerR
    for (s in m.stickers) {
      val b = t - s.bornAt; val p = t - s.pokedAt
      val sc = (if (b < 0.35f) 1f + 0.35f * (1 - b / 0.35f) - 0.15f * sin(b / 0.35f * PI.toFloat()) else 1f) *
        (if (s.live) 1f else (1f - (t - s.fadeAt) / StickersModel.FADE).coerceIn(0f, 1f))
      val wob = if (p in 0f..0.7f) sin(p * 25f) * 14f * (1 - p / 0.7f) else 0f
      val c = Offset(s.x, s.y)
      rotate(s.rot + wob, c) { scale(sc, c) { drawSticker(s.kind, s.critter, c, r, t) } }
    }
    m.parts.draw(this)
    // tray
    val tr = m.trayR
    val bar = Rect(tr * 0.3f, m.trayY - tr * 1.25f, size.width - tr * 0.3f, size.height - tr * 0.1f)
    softShadow(bar, tr * 0.5f, tr * 0.25f)
    drawRoundRect(Color.White.copy(alpha = 0.92f), bar.topLeft, bar.size, CornerRadius(tr * 0.5f))
    val held = m.peels.values.map { it.kind }.toSet()
    for ((i, k) in StickersModel.TRAY.withIndex()) {
      val c = m.slot(i); val cur = i == m.current
      if (cur) drawCircle(Pal.sun.copy(alpha = 0.45f + 0.2f * sin(t * 4f)), tr * 1.12f, c)
      val sc = if (k in held) 0.7f else if (cur) 1f + 0.06f * sin(t * 4f) else 0.88f
      scale(sc, c) { drawSticker(k, m.nextCritter, c, tr, t) }
    }
    for (pl in m.peels.values) { val c = Offset(pl.x, pl.y); rotate(-8f, c) { scale(1.15f, c) { drawSticker(pl.kind, pl.critter, c, r, t) } } }
    if (t - m.lastTouch > StickersModel.IDLE_HINT && m.peels.isEmpty()) drawHand(m.slot(m.current) - Offset(0f, tr * 0.2f), mm * 0.05f, abs(sin(t * 3f)))
  }
}
