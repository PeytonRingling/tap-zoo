package com.peyton.toddlergame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*
import kotlin.random.Random

val MODE_BG = mapOf(Mode.BALLOONS to (Color(0xFFBDE3FF) to Color(0xFFFFE3EF)), Mode.ANIMALS to (Color(0xFFD7F5C8) to Color(0xFFA8E39A)),
  Mode.AQUARIUM to (Color(0xFF8FE3FF) to Color(0xFF4E8DF0)), Mode.BUBBLES to (Color(0xFFC8F3FF) to Color(0xFF7FD3F5)),
  Mode.VEHICLES to (Color(0xFFFFF0C9) to Color(0xFFFFD58A)), Mode.GARDEN to (Color(0xFFFFE3F1) to Color(0xFFC9EDB9)),
  Mode.DRUMS to (Color(0xFFFFE1D2) to Color(0xFFFFB9A0)), Mode.PEEKABOO to (Color(0xFFFFF3CF) to Color(0xFFFFD9A8)),
  Mode.PAINT to (Color(0xFF3B2A7A) to Color(0xFF1A1345)), Mode.MUSIC to (Color(0xFFF3E1FF) to Color(0xFFD6BCFF)), Mode.SKY to (Color(0xFF34408F) to Color(0xFF151B4D)), Mode.BEDTIME to (Color(0xFF2B2E6E) to Color(0xFF12143A)))

/** Animated picture icon for each mode (no text). */
fun DrawScope.drawModeIcon(mode: Mode, rc: Rect, t: Float, elevation: Float = 1f) {
  val rad = rc.minDimension * 0.24f; val (c1, c2) = MODE_BG.getValue(mode)
  if (elevation > 0f) softShadow(Rect(rc.left + rad * 0.2f, rc.top + rad * 0.4f, rc.right - rad * 0.2f, rc.bottom + rad * 0.25f), rad, rad * 0.45f * elevation, Pal.shadow.copy(alpha = 0.25f))
  clipPath(Path().apply { addRoundRect(RoundRect(rc, CornerRadius(rad))) }) {
    drawRect(Brush.verticalGradient(listOf(c1, c2), rc.top, rc.bottom), rc.topLeft, rc.size)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), rc.topLeft + Offset(rc.width * 0.2f, rc.height * 0.1f), rc.minDimension * 0.7f), rc.minDimension * 0.7f, rc.topLeft + Offset(rc.width * 0.2f, rc.height * 0.1f))
    val c = rc.center; val s = rc.minDimension
    when (mode) {
      Mode.BALLOONS -> { listOf(Triple(-0.22f, 0.05f, Pal.coral), Triple(0.22f, 0f, Pal.sky), Triple(0f, -0.12f, Pal.sun)).forEachIndexed { i, (dx, dy, col) ->
          drawBalloon(c + Offset(dx * s, dy * s + sin(t * 1.8f + i * 2) * s * 0.03f - s * 0.05f), s * 0.15f, col, t, i.toFloat(), if (i == 2) 3 else 0) } }
      Mode.ANIMALS -> { val bl = if ((t % 3.2f) < 0.16f) 1f else 0f; softShadow(Rect(c.x - s * 0.25f, c.y + s * 0.28f, c.x + s * 0.25f, c.y + s * 0.36f), s * 0.05f, s * 0.04f)
        drawCritter(Critter.DOG, c + Offset(0f, s * 0.02f + sin(t * 2f) * s * 0.01f), s * 0.27f, Offset(cos(t), sin(t * 0.7f) * 0.5f), bl, if ((t % 4f) > 3.4f) 0.8f else 0f) }
      Mode.AQUARIUM -> { for (i in 0..3) drawCircle(Color.White.copy(alpha = 0.4f), s * 0.02f, Offset(rc.left + s * (0.2f + i * 0.2f), rc.bottom - ((t * 0.2f * s + i * s * 0.27f) % s)))
        val fx = sin(t * 0.8f) * s * 0.18f; val dir = if (cos(t * 0.8f) >= 0) 1f else -1f
        scale(dir, 1f, c + Offset(fx, 0f)) { drawFish(c + Offset(fx, sin(t * 2f) * s * 0.03f), s * 0.2f, Pal.tangerine, Color.White, sin(t * 8f) * 18f) } }
      Mode.BUBBLES -> for (i in 0 until 4) { val k = ((t * 0.25f + i * 0.25f) % 1f); val bc = Offset(rc.left + s * (0.25f + 0.17f * i) + sin(t * 2 + i) * s * 0.04f, rc.bottom + s * 0.15f - k * s * 1.3f); val br = s * (0.1f + 0.04f * (i % 2))
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.1f), Pal.vivid[i * 2].copy(alpha = 0.5f)), bc, br), br, bc); drawCircle(Color.White.copy(alpha = 0.8f), br, bc, style = Stroke(s * 0.012f)); drawCircle(Color.White, br * 0.2f, bc + Offset(br * 0.35f, -br * 0.4f)) }
      Mode.VEHICLES -> { drawRoundRect(Color(0xFF8E8AAE), Offset(rc.left, c.y + s * 0.18f), Size(rc.width, s * 0.14f))
        var x = rc.left - (t * s * 0.3f) % (s * 0.25f); while (x < rc.right) { drawRect(Color.White.copy(alpha = 0.8f), Offset(x, c.y + s * 0.245f), Size(s * 0.1f, s * 0.015f)); x += s * 0.25f }
        drawVehicle(0, c.x - s * 0.3f, c.y + s * 0.2f - abs(sin(t * 8f)) * s * 0.01f, s * 0.4f, Pal.coral, t, t * 300f) }
      Mode.GARDEN -> { drawFlower(Offset(c.x - s * 0.12f, rc.bottom + s * 0.02f), s * 0.5f, s * 0.12f, Pal.pink, sin(t) * 0.4f, 0f)
        drawButterfly(c + Offset(s * 0.18f + sin(t * 0.9f) * s * 0.06f, -s * 0.18f + cos(t * 1.3f) * s * 0.05f), s * 0.11f, Pal.sky, sin(t * 10f)) }
      Mode.DRUMS -> { for (i in 0 until 4) { val bh = s * (0.4f - i * 0.05f); val bx = rc.left + s * (0.16f + i * 0.18f); val hit = ((t * 3f).toInt() % 4 == i)
          clayRect(Rect(bx, c.y - s * 0.12f - bh / 2, bx + s * 0.13f, c.y - s * 0.12f + bh / 2), s * 0.05f, RAINBOW[i * 2].lighten(if (hit) 0.25f else 0f), 0.6f) }
        val dc = Offset(c.x, c.y + s * 0.3f); drawRoundRect(Brush.horizontalGradient(listOf(Pal.coral.darken(0.1f), Pal.coral.lighten(0.25f), Pal.coral.darken(0.15f)), dc.x - s * 0.25f, dc.x + s * 0.25f), Offset(dc.x - s * 0.25f, dc.y - s * 0.05f), Size(s * 0.5f, s * 0.2f), CornerRadius(s * 0.05f))
        drawOval(Color(0xFFFFFBF2), Offset(dc.x - s * 0.25f, dc.y - s * 0.12f), Size(s * 0.5f, s * 0.14f)) }
      Mode.MUSIC -> { val on = ((t * 3f).toInt()) % 4; for (i in 0 until 4) { val x = rc.left + s * (0.14f + (i % 2) * 0.38f); val y = rc.top + s * (0.14f + (i / 2) * 0.38f)
          val sc = if (i == on) 1.08f else 1f; scale(sc, sc, Offset(x + s * 0.17f, y + s * 0.17f)) { clayRect(Rect(x, y, x + s * 0.34f, y + s * 0.34f), s * 0.08f, RAINBOW[i * 2], 0.5f)
          drawShape(i, Offset(x + s * 0.17f, y + s * 0.17f), s * 0.075f, Color.White.copy(alpha = if (i == on) 1f else 0.8f)) } } }
      Mode.PAINT -> { var prev: Offset? = null; for (j in 0..40) { val x = rc.left + s * (0.12f + 0.76f * j / 40f); val p = Offset(x, c.y + sin(j * 0.35f + t * 3f) * s * 0.18f)
          prev?.let { drawLine(Color.hsv((j * 9f + t * 80f) % 360f, 0.8f, 1f).copy(alpha = 0.35f), it, p, s * 0.09f, StrokeCap.Round); drawLine(Color.hsv((j * 9f + t * 80f) % 360f, 0.5f, 1f), it, p, s * 0.035f, StrokeCap.Round) }; prev = p }
        drawStar(Offset(rc.left + s * 0.88f, c.y + sin(40 * 0.35f + t * 3f) * s * 0.18f), s * 0.06f * (1 + 0.3f * sin(t * 8f)), Color.White) }
      Mode.PEEKABOO -> { val y = (sin(t * 2f) * 0.5f + 0.5f).let { if (it > 0.5f) 1f else it * 2 }; val cover = Rect(rc.left + s * 0.1f, rc.top + s * 0.55f, rc.right - s * 0.1f, rc.bottom)
        clipRect(rc.left, rc.top, rc.right, cover.bottom - s * 0.05f) { drawCritter(Critter.CHICK, Offset(c.x, cover.top + s * 0.25f - y * s * 0.32f), s * 0.2f, Offset(0f, 0.3f), 0f, y) }
        drawCover(0, cover, 0f, t) }
      Mode.BEDTIME -> { for (i in 0 until 6) drawStar(Offset(rc.left + s * ((i * 41 % 100) / 100f), rc.top + s * ((i * 23 % 60) / 100f)), s * 0.018f, Color.White.copy(alpha = 0.4f + 0.3f * sin(t + i)))
        val mc = Offset(rc.left + s * 0.72f, rc.top + s * 0.27f); drawCircle(Color(0xFFFFF1B8), s * 0.13f, mc); drawCircle(MODE_BG.getValue(Mode.BEDTIME).first, s * 0.11f, mc + Offset(s * 0.06f, -s * 0.03f))
        drawCritter(Critter.CAT, Offset(c.x - s * 0.05f, c.y + s * 0.17f), s * 0.2f * (1 + 0.03f * sin(t * 1.5f)), Offset.Zero, 1f, 0f)
        val zt = (t * 0.4f) % 1f; drawStar(Offset(c.x + s * 0.15f + zt * s * 0.05f, c.y - zt * s * 0.15f), s * 0.035f, Color.White.copy(alpha = 1 - zt)) }
      Mode.SKY -> { for (i in 0 until 8) drawStar(Offset(rc.left + s * ((i * 37 % 100) / 100f), rc.top + s * ((i * 61 % 100) / 100f)), s * 0.02f, Color.White.copy(alpha = 0.5f + 0.5f * sin(t * 3 + i)))
        drawCircle(Color(0xFFFFF1A8), s * 0.12f, Offset(rc.left + s * 0.75f, rc.top + s * 0.25f))
        val bx = rc.left + s * (0.5f + 0.3f * sin(t * 1.3f)); val by = rc.bottom - s * 0.17f - abs(sin(t * 3f)) * s * 0.35f
        rotate(t * 200f, Offset(bx, by)) { drawStar(Offset(bx, by), s * 0.13f, Pal.sun) } }
    }
  }
  drawRoundRect(Color.White.copy(alpha = 0.7f), rc.topLeft, rc.size, CornerRadius(rad), style = Stroke(rc.minDimension * 0.02f))
}

private class MenuModel {
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = 0f; var px = 0f; var py = 0f; var tx = 0f; var ty = 0f
  val hit = HashMap<Int, Float>(); val parts = Particles()
  var picking: Mode? = null; var pickAt = 0f
  /** Featured (first) mode gets a big hero card; the rest flow into a grid. */
  fun rects(n: Int): List<Rect> {
    val pad = min(w, h) * 0.06f; val top = pad * 1.4f
    if (n <= 1) return listOf(Rect(w * 0.2f, h * 0.15f, w * 0.8f, h * 0.85f))
    return if (w > h) {
      val hw = min(w * 0.36f, h - top - pad); val hero = Rect(pad * 1.2f, (h - hw) / 2 + top * 0.3f, pad * 1.2f + hw, (h + hw) / 2 + top * 0.3f)
      val rest = Layout.grid(n - 1, w - hero.right - pad, h - top - pad * 0.5f, pad * 0.55f, square = true).map { it.translate(Offset(hero.right + pad * 0.3f, top - pad * 0.25f)) }
      listOf(hero) + rest
    } else {
      val hs = min(w - pad * 2, h * 0.3f); val hero = Rect((w - hs) / 2, top, (w + hs) / 2, top + hs)
      val rest = Layout.grid(n - 1, w - pad * 0.6f, h - hero.bottom - pad * 0.5f, pad * 0.45f, square = true).map { it.translate(Offset(pad * 0.3f, hero.bottom + pad * 0.1f)) }
      listOf(hero) + rest
    }
  }
  fun step(dt: Float) { px += (tx - px) * min(1f, dt * 3f); py += (ty - py) * min(1f, dt * 3f); parts.step(dt) }
}

@Composable
fun MenuScreen(modes: List<Mode>, onPick: (Mode) -> Unit) {
  val m = remember { MenuModel() }
  val pick by rememberUpdatedState(onPick)
  val time = rememberTicker { dt, t -> m.t = t; m.step(dt); val p = m.picking; if (p != null && t - m.pickAt > 0.35f) { m.picking = null; pick(p) } }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }.multiTouch(modes, onDown = { _, p ->
    m.lastTouch = m.t; m.tx = p.x / m.w - 0.5f; m.ty = p.y / m.h - 0.5f
    val rects = m.rects(modes.size); val i = rects.indexOfFirst { it.contains(p) }
    if (i >= 0) { if (m.picking == null) { m.hit[i] = m.t; m.picking = modes[i]; m.pickAt = m.t; Audio.play(Sfx.chime); Fx.tap(); m.parts.burst(p.x, p.y, 24, Pal.vivid, kind = 4, speed = 1000f, size = rects[i].width * 0.035f) } }
    else { Audio.play(Sfx.bells[(p.x / m.w * 9).toInt().coerceIn(0, 9)], 0.35f); m.parts.burst(p.x, p.y, 8, listOf(Color.White, Pal.sun), kind = 1, speed = 400f, size = 14f) }
  })) {
    val t = time.value; val mm = min(size.width, size.height)
    val px = m.px * mm + sin(t * 0.15f) * mm * 0.02f; val py = m.py * mm * 0.5f
    meshBg(Color(0xFFB9E2FF), Color(0xFFFFEEF5), listOf(Pal.lilac to Offset(0.9f, 0.3f), Color(0xFFFFF0C2) to Offset(0.08f, 0.05f), Pal.blush to Offset(0.2f, 0.9f)), t)
    val sunC = Offset(size.width * 0.1f - px * 0.02f, size.height * 0.1f - py * 0.02f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF1B0), Color(0x00FFF1B0)), sunC, mm * 0.32f), mm * 0.32f, sunC)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF6C9), Color(0xFFFFCF5A)), sunC - Offset(mm * 0.03f, mm * 0.03f), mm * 0.13f), mm * 0.1f, sunC)
    for (i in 0 until 3) softCloud(Offset(((t * 8f + i * size.width / 2.3f) % (size.width * 1.5f)) - size.width * 0.25f - px * 0.05f, size.height * (0.08f + 0.06f * i) - py * 0.05f), mm * 0.055f, 0.55f)
    for (i in 0 until 2) softCloud(Offset(((t * 16f + i * size.width / 1.6f + 200f) % (size.width * 1.5f)) - size.width * 0.25f - px * 0.12f, size.height * (0.2f + 0.3f * i) - py * 0.1f), mm * 0.09f, 0.8f)
    translate(-px * 0.08f, -py * 0.05f) { hills(Color(0xFFD5EDCC), size.height * 0.84f, size.height * 0.08f, 0f) }
    translate(-px * 0.16f, -py * 0.08f) { hills(Color(0xFFB2E0A6), size.height * 0.92f, size.height * 0.06f, 1f) }
    val rects = m.rects(modes.size); val idle = t - m.lastTouch > 5f; val attract = ((t / 2.5f).toInt()) % modes.size
    modes.forEachIndexed { i, mode ->
      val rc0 = rects[i]; val since = t - (m.hit[i] ?: -10f)
      val bob = sin(t * 1.6f + i * 1.1f) * rc0.height * (if (i == 0) 0.012f else 0.01f)
      // spring press: quick squash then overshoot
      var s = 1f + (if (since < 0.6f) 0.12f * exp(-since * 7f) * sin(since * 24f) else 0f)
      if (i == 0) s += 0.015f * sin(t * 2.2f)
      if (idle && i == attract) s += 0.05f * abs(sin(t * 5f))
      val rc = rc0.translate(Offset(0f, bob))
      scale(s, s, rc.center) {
        if (i == 0) { drawRoundRect(Brush.radialGradient(listOf(Pal.sun.copy(alpha = 0.45f), Color.Transparent), rc.center, rc.width * 0.75f), rc.topLeft - Offset(rc.width * 0.15f, rc.height * 0.15f), Size(rc.width * 1.3f, rc.height * 1.3f), CornerRadius(rc.width * 0.4f)) }
        drawModeIcon(mode, rc, t)
        if (i == 0) { val bc = Offset(rc.right - rc.width * 0.1f, rc.top + rc.width * 0.1f); clayCircle(bc, rc.width * 0.075f, Pal.sun); drawStar(bc, rc.width * 0.05f, Color.White, t * 30f) }
      }
      if (idle && i == attract) drawHand(rc.center + Offset(rc.width * 0.15f, rc.height * 0.2f), rc.width * (if (i == 0) 0.07f else 0.11f), abs(sin(t * 2.5f)))
    }
    m.parts.draw(this)
  }
}

@Composable
fun SettingsPanel(enabled: Set<Mode>, onToggle: (Mode) -> Unit, onClose: () -> Unit, onExit: () -> Unit, onKidLock: (Boolean) -> Unit = {},
                  onMusicSlots: (Set<MusicSlot>) -> Unit = {}, onMusicVolume: (Int) -> Unit = {}) {
  var slots by remember { mutableStateOf(Prefs.musicSlots) }
  var mvol by remember { mutableIntStateOf(Prefs.musicVolume) }
  var bed by remember { mutableIntStateOf(Prefs.bedtimeMinutes) }
  val ctx = androidx.compose.ui.platform.LocalContext.current
  val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(20)) { uris -> if (uris.isNotEmpty()) Photos.add(ctx, uris) }
  var kl by remember { mutableStateOf(Prefs.kidLock) }
  var vol by remember { mutableIntStateOf(Prefs.volume) }
  var hap by remember { mutableStateOf(Prefs.haptics) }
  var sym by remember { mutableStateOf(Prefs.balloonSymbols) }
  val time = rememberTicker()
  val label = TextStyle(fontSize = 18.sp, color = Color(0xFF333344), fontWeight = FontWeight.SemiBold)
  Box(Modifier.fillMaxSize().background(Color(0xCC101020)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
    Column(Modifier.widthIn(max = 600.dp).padding(28.dp).background(Color.White, RoundedCornerShape(28.dp)).verticalScroll(rememberScrollState()).padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)) {
      BasicText("Parent settings", style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF222233)))
      UpdatesRow(label)
      BasicText("Sound effects volume", style = label)
      VolumeBars(vol, label) { i -> vol = i; Prefs.volume = i; Audio.volume = Prefs.volumeGain(i); Audio.play(Sfx.chime) }
      BasicText("Music volume", style = label)
      VolumeBars(mvol, label) { i -> mvol = i; Prefs.musicVolume = i; BgMusic.musicGain = Prefs.volumeGain(i); BgMusic.refresh(); onMusicVolume(i) }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText("Vibration on taps", style = label)
        Box(Modifier.size(64.dp, 36.dp).background(if (hap) Color(0xFF6BCB77) else Color(0xFFCCCCD6), RoundedCornerShape(18.dp)).clickable { hap = !hap; Prefs.haptics = hap; Fx.haptics = hap; Fx.tap() },
          contentAlignment = if (hap) Alignment.CenterEnd else Alignment.CenterStart) { Box(Modifier.padding(4.dp).size(28.dp).background(Color.White, RoundedCornerShape(14.dp))) }
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText("Music in", style = label)
        for (sl in MusicSlot.entries) { val on = sl in slots
          Box(Modifier.background(if (on) Color(0xFF6BCB77) else Color(0xFFE4E2F2), RoundedCornerShape(14.dp))
            .clickable { slots = if (on) slots - sl else slots + sl; Prefs.musicSlots = slots; onMusicSlots(slots) }.padding(horizontal = 14.dp, vertical = 8.dp)) {
            BasicText((if (on) "♪ " else "") + sl.label, style = label.copy(color = if (on) Color.White else Color(0xFF333344))) }
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText("Bedtime timer", style = label)
        for (mn in listOf(10, 15, 20)) Box(Modifier.background(if (bed == mn) Color(0xFF5B5A9C) else Color(0xFFE4E2F2), RoundedCornerShape(14.dp)).clickable { bed = mn; Prefs.bedtimeMinutes = mn }.padding(horizontal = 14.dp, vertical = 8.dp)) {
          BasicText("$mn min", style = label.copy(color = if (bed == mn) Color.White else Color(0xFF333344))) }
      }
      BasicText("Family photos (stay on this phone; float out of balloons)", style = label)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Box(Modifier.size(72.dp).background(Color(0xFFE4F1FF), RoundedCornerShape(16.dp)).clickable { picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
          BasicText("+ Add", style = label.copy(color = Color(0xFF4D7CFE))) }
        Photos.images.toList().forEach { (f, img) ->
          Box(Modifier.size(72.dp)) {
            Canvas(Modifier.fillMaxSize()) { drawFramedPhoto(img, center, size.minDimension * 0.95f, 0f) }
            Box(Modifier.align(Alignment.TopEnd).size(26.dp).background(Color(0xFFFF5D5D), RoundedCornerShape(13.dp)).clickable { Photos.remove(f) }, contentAlignment = Alignment.Center) {
              BasicText("✕", style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)) }
          }
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText("Kid lock (pin app to screen)", style = label)
        Box(Modifier.size(64.dp, 36.dp).background(if (kl) Color(0xFF6BCB77) else Color(0xFFCCCCD6), RoundedCornerShape(18.dp)).clickable { kl = !kl; onKidLock(kl) },
          contentAlignment = if (kl) Alignment.CenterEnd else Alignment.CenterStart) { Box(Modifier.padding(4.dp).size(28.dp).background(Color.White, RoundedCornerShape(14.dp))) }
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText("Shapes & letters on balloons", style = label)
        Box(Modifier.size(64.dp, 36.dp).background(if (sym) Color(0xFF6BCB77) else Color(0xFFCCCCD6), RoundedCornerShape(18.dp)).clickable { sym = !sym; Prefs.balloonSymbols = sym },
          contentAlignment = if (sym) Alignment.CenterEnd else Alignment.CenterStart) { Box(Modifier.padding(4.dp).size(28.dp).background(Color.White, RoundedCornerShape(14.dp))) }
      }
      BasicText("Games shown on the menu (tap to hide/show)", style = label)
      Mode.entries.chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          row.forEach { mode -> val on = mode in enabled
            Canvas(Modifier.size(80.dp).clickable { onToggle(mode) }) {
              drawModeIcon(mode, Rect(Offset.Zero, size), time.value, 0f)
              if (!on) { drawRoundRect(Color.White.copy(alpha = 0.7f), cornerRadius = CornerRadius(size.width * 0.2f)); drawLine(Color(0xFFFF5D5D), Offset(size.width * 0.2f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.8f), 10f, StrokeCap.Round) }
              else drawCircle(Color(0xFF6BCB77), size.width * 0.12f, Offset(size.width * 0.85f, size.height * 0.15f))
            }
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.background(Color(0xFF4D96FF), RoundedCornerShape(16.dp)).clickable { onClose() }.padding(horizontal = 22.dp, vertical = 14.dp)) { BasicText("Back to play", style = label.copy(color = Color.White)) }
        Box(Modifier.background(Color(0xFFFF5D5D), RoundedCornerShape(16.dp)).clickable { onExit() }.padding(horizontal = 22.dp, vertical = 14.dp)) { BasicText("Exit app", style = label.copy(color = Color.White)) }
      }
      BasicText("Credits", style = label)
      BasicText(CREDITS, style = TextStyle(fontSize = 13.sp, color = Color(0xFF666677)))
      BasicText("Hold the house 1.5 s to go back to the menu; hold the gear 3 s for this panel. Kid lock pins Tap Zoo to the screen: first time, Android asks to confirm. Turn on Settings › Security & privacy › More security & privacy › App pinning, and 'Ask for PIN before unpinning'. Exit app here unpins it. No ads, no data collected; the internet is used only when you tap Check for updates here.",
        style = TextStyle(fontSize = 13.sp, color = Color(0xFF666677)))
    }
  }
}

/** Shown in Parent settings › Credits. The music lines are incompetech's required attribution, verbatim (CC BY 4.0). */
internal val CREDITS = listOf("Monkeys Spinning Monkeys", "Beachfront Celebration").joinToString("\n\n") {
  "\"$it\" Kevin MacLeod (incompetech.com)\nLicensed under Creative Commons: By Attribution 4.0 License\nhttp://creativecommons.org/licenses/by/4.0/\n" +
    "Edited: trimmed/looped and re-encoded for Tap Zoo."
} + "\n\nBalloon pop: \"Pop 01\" by LilMati and \"Balloon Pop\" by Breviceps (freesound.org, CC0)." +
  "\nAnimal sounds made with elevenlabs.io. Other music and sounds are original to Tap Zoo."

/** Six bars, 0 = muted (red). */
@Composable
private fun VolumeBars(value: Int, label: TextStyle, onPick: (Int) -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
    for (i in 0..5) Box(Modifier.size(44.dp, (18 + i * 9).dp).background(if (i == 0) (if (value == 0) Color(0xFFFF5D5D) else Color(0xFFF2C4C4)) else if (i <= value) Color(0xFF4D96FF) else Color(0xFFDDDDE6), RoundedCornerShape(10.dp))
      .clickable { onPick(i) })
    BasicText(if (value == 0) "Muted" else "${value * 20}%", style = label)
  }
}

/** Parent-only "App updates" row (this panel is behind the 3-second gear hold). Nothing here runs on its own. */
@Composable
fun UpdatesRow(label: TextStyle) {
  val act = androidx.compose.ui.platform.LocalContext.current as? MainActivity
  val u = Updater.ui
  val rel = u.release
  val (button, onClick) = when (u.phase) {
    UpdPhase.AVAILABLE -> "Install ${rel?.versionName}" to { act?.installUpdate() }
    UpdPhase.NEEDS_PERMISSION -> "Allow installs" to { act?.allowInstalls() }
    UpdPhase.CHECKING, UpdPhase.DOWNLOADING, UpdPhase.INSTALLING, UpdPhase.CONFIRM -> null to {}
    else -> "Check for updates" to { act?.checkForUpdates() }
  }
  val tint = when (u.phase) {
    UpdPhase.ERROR -> Color(0xFFD64545); UpdPhase.AVAILABLE, UpdPhase.DONE -> Color(0xFF2E9E4F); UpdPhase.NEEDS_PERMISSION -> Color(0xFFB7791F); else -> Color(0xFF666677)
  }
  val installed = remember(act) { act?.let { runCatching { it.packageManager.getPackageInfo(it.packageName, 0).versionName }.getOrNull() } }
  Column(Modifier.fillMaxWidth().background(Color(0xFFF2F4FA), RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        BasicText("App updates", style = label)
        BasicText(u.message.ifEmpty { "Installed: version ${installed ?: "?"}. Updates come from Tap Zoo's GitHub releases." },
          style = TextStyle(fontSize = 13.sp, color = tint), maxLines = 3)
      }
      if (button != null) Box(Modifier.padding(start = 10.dp).background(if (u.phase == UpdPhase.AVAILABLE) Color(0xFF2E9E4F) else Color(0xFF4D96FF), RoundedCornerShape(14.dp))
        .clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp)) { BasicText(button, style = label.copy(color = Color.White, fontSize = 15.sp)) }
    }
    if (u.phase == UpdPhase.DOWNLOADING || u.phase == UpdPhase.CHECKING || u.phase == UpdPhase.INSTALLING) {
      val p = u.progress
      Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0xFFDDDDE6), RoundedCornerShape(3.dp))) {
        if (p != null) Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0f, 1f)).background(Color(0xFF4D96FF), RoundedCornerShape(3.dp)))
      }
    }
  }
}
