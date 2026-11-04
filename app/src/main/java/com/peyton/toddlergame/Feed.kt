package com.peyton.toddlergame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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

internal enum class FoodKind { APPLE, BANANA, LEMON, CARROT, CAKE }
/** How the monster reacts to a bite: lemon = sour pucker, carrot = crunch, cake = happy wiggle, the rest a plain yum. */
internal enum class Reaction { NONE, YUM, PUCKER, CRUNCH, WIGGLE }
internal enum class FoodState { IDLE, HELD, FLYING, EATEN }

internal val LEMON = Color(0xFFFFE94D)

/** Moves [a] a share [k] toward [b] around the colour wheel (not straight through grey), keeping it bright and saturated. */
internal fun hueToward(a: Color, b: Color, k: Float): Color {
  fun hsv(c: Color): FloatArray { val mx = max(c.red, max(c.green, c.blue)); val mn = min(c.red, min(c.green, c.blue)); val d = mx - mn
    val h = when { d == 0f -> 0f; mx == c.red -> 60f * (((c.green - c.blue) / d) % 6f); mx == c.green -> 60f * ((c.blue - c.red) / d + 2f); else -> 60f * ((c.red - c.green) / d + 4f) }
    return floatArrayOf((h + 360f) % 360f, if (mx == 0f) 0f else d / mx, mx) }
  val x = hsv(a); val y = hsv(b); var dh = y[0] - x[0]; if (dh > 180f) dh -= 360f; if (dh < -180f) dh += 360f
  val h = (x[0] + dh * k + 360f) % 360f; val sat = max(0.45f, x[1] + (y[1] - x[1]) * k); val v = max(0.75f, x[2] + (y[2] - x[2]) * k)
  val c = v * sat; val hp = h / 60f; val xx = c * (1 - abs(hp % 2f - 1)); val m0 = v - c
  val (r, g, bb) = when (hp.toInt()) { 0 -> Triple(c, xx, 0f); 1 -> Triple(xx, c, 0f); 2 -> Triple(0f, c, xx); 3 -> Triple(0f, xx, c); 4 -> Triple(xx, 0f, c); else -> Triple(c, 0f, xx) }
  return Color((r + m0).coerceIn(0f, 1f), (g + m0).coerceIn(0f, 1f), (bb + m0).coerceIn(0f, 1f))
}

internal class Food(val kind: FoodKind, val slot: Int) {
  var x = 0f; var y = 0f; var state = FoodState.IDLE; var finger = -1L
  var flyT0 = 0f; var fromX = 0f; var fromY = 0f; var eatenAt = -10f; var bornAt = -10f
}

/** Feed the monster, for an 18-month-old: five big foods float in the top half and a friendly monster waits below. Tapping a food
 *  sends it flying into the mouth; dragging carries it and letting go anywhere hops it in. Tapping empty space feeds the nearest
 *  food, so every touch feeds. The mouth opens while a finger is down. No fail, no score. */
internal class FeedModel(private val sink: SoundSink = GameSound, private val rnd: Random = Random.Default) {
  companion object {
    const val FLY = 0.45f; const val RESPAWN = 1.0f; const val BURP_EVERY = 5; const val FULL = 10
    const val CHOMP_GAP = 0.12f; const val IDLE_HINT = 7f
    const val REACT = 0.9f; const val DANCE = 3f; const val TINT = 0.2f
    /** Within this many monster radii the mouth opens extra wide and the monster leans toward the food. */
    const val NEAR = 1.6f
    val BODY = Color(0xFF8E6CF0)
  }
  val foods = FoodKind.entries.mapIndexed { i, k -> Food(k, i) }
  val parts = Particles(300).apply { gravity = 1200f }
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = -100f
  var bites = 0; private set
  var eaten = 0; private set
  var giggleAt = -10f; private set
  var chompAt = -10f; private set
  private val fingers = HashSet<Long>()
  private var lastChomp = -10f; private var placed = false
  var reaction = Reaction.NONE; private set
  var reactAt = -10f; private set
  /** Body colour: each bite moves it [TINT] of the way toward the food's colour; back to purple after the dance. */
  var tint = BODY; private set
  /** The tummy patch shows the colour of the last food straight away (null = plain). */
  var tummyColor: Color? = null; private set
  /** -1..1: leaning left/right toward food that's coming. */
  var lean = 0f; private set
  var danceAt = -10f; private set
  private var danceDone = true
  fun dancing() = t - danceAt in 0f..DANCE
  fun reacting(r: Reaction) = reaction == r && t - reactAt in 0f..REACT
  /** 0 flat .. 1 round belly, growing with each bite toward the 10th; full during the dance. */
  val belly get() = if (dancing()) 1f else (bites % FULL) / (FULL - 1f)

  val m get() = min(w, h)
  val foodR get() = min(w * 0.105f, h * 0.09f)
  val monsterR get() = m * 0.27f
  val monster get() = Offset(w * 0.5f, h - monsterR * 1.0f)
  val mouth get() = monster + Offset(0f, monsterR * 0.25f)
  /** 0 closed .. 1 open while any finger is down or food is on its way .. 1.4 extra wide when the food is close. */
  var mouthOpen = 0f; private set
  val tummy get() = 1f + 0.1f * belly

  fun home(slot: Int) = Offset(w * (0.12f + 0.76f * slot / 4f), h * (if (slot % 2 == 0) 0.17f else 0.3f))
  private fun ok(p: Offset) = p.x.isFinite() && p.y.isFinite() && w >= 2f && h >= 2f

  private fun place() { if (placed || w < 2f) return; placed = true; foods.forEach { val p = home(it.slot); it.x = p.x; it.y = p.y } }

  fun down(id: Long, p: Offset) {
    if (!ok(p)) return
    place(); lastTouch = t; fingers += id
    val f = foods.filter { it.state == FoodState.IDLE && hypot(it.x - p.x, it.y - p.y) < foodR * 1.5f }.minByOrNull { hypot(it.x - p.x, it.y - p.y) }
    if (f != null) { f.state = FoodState.HELD; f.finger = id; sink.play(Sfx.plip, 0.5f); sink.haptic(); return }
    if (hypot(p.x - monster.x, p.y - monster.y) < monsterR) { giggleAt = t; sink.play(Sfx.giggle, 0.6f); sink.haptic(true)
      parts.burst(p.x, p.y, 10, listOf(Pal.pink, Color.White), kind = 2, speed = m * 0.8f, size = m * 0.02f, up = m * 0.4f); return }
    parts.burst(p.x, p.y, 8, listOf(Color.White, Pal.sun), kind = 1, speed = m * 0.6f, size = m * 0.018f, up = 100f)
    foods.filter { it.state == FoodState.IDLE }.minByOrNull { hypot(it.x - p.x, it.y - p.y) }?.let { fly(it) } ?: sink.play(Sfx.plip, 0.4f)
  }
  fun move(id: Long, p: Offset) { if (!ok(p)) return; lastTouch = t; foods.firstOrNull { it.state == FoodState.HELD && it.finger == id }?.let { it.x = p.x; it.y = p.y } }
  fun up(id: Long) { fingers -= id; foods.firstOrNull { it.state == FoodState.HELD && it.finger == id }?.let { fly(it) } }

  private fun fly(f: Food) {
    f.finger = -1
    if (hypot(f.x - mouth.x, f.y - mouth.y) < monsterR * 0.7f) { eat(f); return }
    f.state = FoodState.FLYING; f.flyT0 = t; f.fromX = f.x; f.fromY = f.y; sink.play(Sfx.wheee, 0.25f)
  }

  private fun eat(f: Food) {
    f.state = FoodState.EATEN; f.eatenAt = t; f.x = mouth.x; f.y = mouth.y
    bites++; eaten++; chompAt = t
    reaction = when (f.kind) { FoodKind.LEMON -> Reaction.PUCKER; FoodKind.CARROT -> Reaction.CRUNCH; FoodKind.CAKE -> Reaction.WIGGLE; else -> Reaction.YUM }
    reactAt = t
    if (t - lastChomp >= CHOMP_GAP) { lastChomp = t
      when (reaction) {
        Reaction.CRUNCH -> sink.play(Sfx.crunch, 0.8f)
        Reaction.PUCKER -> { sink.play(Sfx.chomp, 0.6f); sink.play(Sfx.pucker, 0.55f) }
        Reaction.WIGGLE -> { sink.play(Sfx.chomp, 0.6f); sink.play(Sfx.giggle, 0.5f); sink.play(Sfx.chime, 0.35f) }
        else -> sink.play(Sfx.chomp, 0.8f)
      } }
    sink.haptic(true)
    tint = hueToward(tint, foodColors(f.kind)[0], TINT); tummyColor = foodColors(f.kind)[0]
    parts.burst(mouth.x, mouth.y, if (reaction == Reaction.CRUNCH) 24 else 12, foodColors(f.kind), kind = 0, speed = m * 0.9f, size = m * 0.015f, up = m * 0.6f, decay = 1.4f)
    if (reaction == Reaction.WIGGLE) parts.burst(monster.x, monster.y - monsterR * 1.1f, 6, listOf(Pal.pink, Pal.coral), kind = 3, speed = m * 0.4f, size = m * 0.03f, up = m * 0.5f, decay = 1f)
    if (bites % BURP_EVERY == 0 && bites % FULL != 0) { sink.play(Sfx.burp, 0.7f); sink.play(Sfx.giggle, 0.45f); giggleAt = t + 0.35f }
    if (bites % FULL == 0) { danceAt = t; danceDone = false; sink.play(Sfx.burp, 0.6f); sink.play(Sfx.march, 0.45f)
      parts.burst(monster.x, monster.y - monsterR, 30, Pal.vivid, kind = 4, speed = m * 1.2f, size = m * 0.02f, up = m * 0.8f, decay = 0.7f) }
  }

  fun step(dtIn: Float) {
    if (w < 2f || h < 2f) return
    place()
    val dt = if (dtIn.isNaN()) 0f else dtIn.coerceIn(0f, 0.1f)
    for (f in foods) when (f.state) {
      FoodState.FLYING -> { val k = ((t - f.flyT0) / FLY).coerceIn(0f, 1f); val e = k * k * (3 - 2 * k)
        f.x = f.fromX + (mouth.x - f.fromX) * e; f.y = f.fromY + (mouth.y - f.fromY) * e - sin(k * PI.toFloat()) * m * 0.15f
        if (k >= 1f) eat(f) }
      FoodState.EATEN -> if (t - f.eatenAt >= RESPAWN) { val p = home(f.slot); f.x = p.x; f.y = p.y; f.state = FoodState.IDLE; f.bornAt = t }
      else -> {}
    }
    val coming = foods.filter { it.state == FoodState.FLYING || it.state == FoodState.HELD }
    val near = coming.minByOrNull { hypot(it.x - mouth.x, it.y - mouth.y) }?.takeIf { hypot(it.x - mouth.x, it.y - mouth.y) < monsterR * NEAR }
    val target = if (near != null) 1.4f else if (fingers.isNotEmpty() || coming.isNotEmpty()) 1f else 0f
    mouthOpen += (target - mouthOpen) * min(1f, dt * 10f)
    val leanTo = near?.let { ((it.x - monster.x) / (monsterR * 1.5f)).coerceIn(-1f, 1f) } ?: 0f
    lean += (leanTo - lean) * min(1f, dt * 8f)
    if (!danceDone && !dancing()) { danceDone = true; tint = BODY; tummyColor = null }
    parts.step(dt)
  }
  fun foodColors(k: FoodKind) = when (k) { FoodKind.APPLE -> listOf(Pal.coral, Color.White); FoodKind.BANANA -> listOf(Pal.sun, Color.White)
    FoodKind.LEMON -> listOf(LEMON, Pal.lime); FoodKind.CARROT -> listOf(Pal.tangerine, Pal.lime); FoodKind.CAKE -> listOf(Pal.pink, Color.White) }
}

internal fun DrawScope.drawFood(k: FoodKind, c: Offset, r: Float, rot: Float = 0f) = rotate(rot, c) {
  softShadow(Rect(c.x - r * 0.7f, c.y + r * 0.8f, c.x + r * 0.7f, c.y + r * 1.05f), r * 0.3f, r * 0.15f)
  when (k) {
    FoodKind.APPLE -> { drawCircle(Pal.coral, r * 0.85f, c + Offset(0f, r * 0.08f)); drawCircle(Color.White.copy(alpha = 0.5f), r * 0.2f, c + Offset(-r * 0.35f, -r * 0.2f))
      drawLine(Color(0xFF6B3E26), c + Offset(0f, -r * 0.6f), c + Offset(r * 0.08f, -r * 1.0f), r * 0.12f, StrokeCap.Round)
      drawOval(Pal.lime, c + Offset(r * 0.1f, -r * 1.05f), Size(r * 0.5f, r * 0.28f)) }
    FoodKind.BANANA -> { drawArc(Pal.sun, 20f, 140f, false, c - Offset(r * 0.9f, r * 1.2f), Size(r * 1.8f, r * 1.8f), style = Stroke(r * 0.55f, cap = StrokeCap.Round))
      drawCircle(Color(0xFF6B3E26), r * 0.1f, c + Offset(-r * 0.83f, -r * 0.05f)) }
    FoodKind.LEMON -> { drawOval(LEMON, c - Offset(r * 0.95f, r * 0.68f), Size(r * 1.9f, r * 1.36f))
      for (s in listOf(-1f, 1f)) drawCircle(LEMON, r * 0.16f, c + Offset(s * r * 0.92f, 0f))
      drawCircle(Color.White.copy(alpha = 0.5f), r * 0.18f, c + Offset(-r * 0.35f, -r * 0.28f))
      drawOval(Pal.lime, c + Offset(-r * 0.05f, -r * 0.95f), Size(r * 0.5f, r * 0.28f)) }
    FoodKind.CARROT -> { drawPath(Path().apply { moveTo(c.x - r * 0.45f, c.y - r * 0.55f); lineTo(c.x + r * 0.45f, c.y - r * 0.55f); lineTo(c.x, c.y + r * 1.0f); close() }, Pal.tangerine)
      for (i in -1..1) drawLine(Pal.lime, c + Offset(i * r * 0.15f, -r * 0.55f), c + Offset(i * r * 0.35f, -r * 1.05f), r * 0.16f, StrokeCap.Round) }
    FoodKind.CAKE -> { drawPath(Path().apply { moveTo(c.x - r * 0.7f, c.y); lineTo(c.x + r * 0.7f, c.y); lineTo(c.x + r * 0.5f, c.y + r * 0.9f); lineTo(c.x - r * 0.5f, c.y + r * 0.9f); close() }, Pal.sky)
      for (i in -1..1) drawLine(Color.White.copy(alpha = 0.6f), Offset(c.x + i * r * 0.3f, c.y + r * 0.08f), Offset(c.x + i * r * 0.22f, c.y + r * 0.82f), r * 0.07f)
      drawCircle(Pal.pink, r * 0.45f, c + Offset(-r * 0.35f, -r * 0.1f)); drawCircle(Pal.pink, r * 0.45f, c + Offset(r * 0.35f, -r * 0.1f)); drawCircle(Pal.pink, r * 0.5f, c + Offset(0f, -r * 0.35f))
      drawCircle(Color.White.copy(alpha = 0.45f), r * 0.14f, c + Offset(-r * 0.25f, -r * 0.45f))
      drawCircle(Pal.coral, r * 0.18f, c + Offset(0f, -r * 0.88f)) }
  }
}

/** Big round friendly monster; [open] 0..1.4 mouth, [look] pupils direction, [wob] squish, [tint] body colour,
 *  [belly] 0..1 how round the tummy patch is, [pucker] 0..1 sour face (squinty eyes, little "o" mouth). */
internal fun DrawScope.drawMonster(c: Offset, r: Float, open: Float, look: Offset, wob: Float, tummy: Float, t: Float,
                                   tint: Color = FeedModel.BODY, belly: Float = 0f, pucker: Float = 0f, tummyColor: Color? = null) {
  val body = tint
  softShadow(Rect(c.x - r, c.y + r * 0.75f, c.x + r, c.y + r * 1.05f), r * 0.5f, r * 0.2f)
  scale(tummy * (1 + wob * 0.06f), tummy * (1 - wob * 0.05f), c + Offset(0f, r)) {
    for (s in listOf(-1f, 1f)) drawPath(Path().apply { moveTo(c.x + s * r * 0.45f, c.y - r * 0.75f); lineTo(c.x + s * r * 0.75f, c.y - r * 1.15f); lineTo(c.x + s * r * 0.75f, c.y - r * 0.6f); close() }, Pal.sun)
    drawCircle(body, r, c); drawCircle(tummyColor?.let { androidx.compose.ui.graphics.lerp(body.lighten(0.25f), it.lighten(0.2f), 0.75f) } ?: body.lighten(0.25f), r * (0.55f + 0.17f * belly), c + Offset(0f, r * (0.35f + 0.08f * belly)))
    drawCircle(Color.White.copy(alpha = 0.25f), r * 0.25f, c + Offset(-r * 0.5f, -r * 0.5f))
    for (s in listOf(-1f, 1f)) { val e = c + Offset(s * r * 0.36f, -r * 0.3f)
      if (pucker > 0.5f) { drawLine(Pal.ink, e + Offset(-r * 0.16f, -s * r * 0.0f - r * 0.06f), e + Offset(r * 0.16f * s, 0f), r * 0.06f, StrokeCap.Round)
        drawLine(Pal.ink, e + Offset(r * 0.16f * s, 0f), e + Offset(-r * 0.16f * s, r * 0.08f), r * 0.06f, StrokeCap.Round); continue }
      drawCircle(Color.White, r * 0.22f, e); drawCircle(Pal.ink, r * 0.11f, e + Offset(look.x * r * 0.08f, look.y * r * 0.08f))
      drawCircle(Color.White, r * 0.035f, e + Offset(look.x * r * 0.08f - r * 0.03f, look.y * r * 0.08f - r * 0.03f)) }
    if (pucker > 0.5f) { val mc = c + Offset(0f, r * 0.3f)
      drawCircle(Color(0xFF3A1F5C), r * 0.1f, mc); drawCircle(Pal.pink, r * 0.1f, mc, style = Stroke(r * 0.05f))
      for (s in listOf(-1f, 1f)) drawCircle(Pal.pink.copy(alpha = 0.4f), r * 0.1f, c + Offset(s * r * 0.62f, r * 0.05f))
      return@scale }
    val mc = c + Offset(0f, r * 0.25f); val mw = r * (0.75f + 0.1f * max(0f, open - 1f)); val mh = r * (0.12f + 0.45f * open)
    drawOval(Color(0xFF3A1F5C), mc - Offset(mw / 2, mh / 2), Size(mw, mh))
    if (open > 0.3f) drawOval(Pal.pink, mc + Offset(-mw * 0.25f, mh * 0.05f), Size(mw * 0.5f, mh * 0.4f))
    for (s in listOf(-1f, 1f)) drawPath(Path().apply { moveTo(mc.x + s * mw * 0.2f, mc.y - mh / 2); lineTo(mc.x + s * mw * 0.32f, mc.y - mh / 2); lineTo(mc.x + s * mw * 0.26f, mc.y - mh / 2 + r * 0.1f); close() }, Color.White)
    for (s in listOf(-1f, 1f)) drawCircle(Pal.pink.copy(alpha = 0.4f), r * 0.1f, c + Offset(s * r * 0.62f, r * 0.05f))
  }
}

@Composable
fun FeedScreen() { FeedScreenFor(remember { FeedModel() }) }

@Composable
internal fun FeedScreenFor(m: FeedModel) {
  val time = rememberTicker { dt, t -> m.t = t; m.step(dt) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }
    .multiTouch(Unit, onDown = { id, p -> m.down(id, p) }, onMove = { id, p -> m.move(id, p) }, onUp = { m.up(it) })) {
    val t = time.value; m.w = size.width; m.h = size.height; val mm = min(size.width, size.height)
    meshBg(Color(0xFFFFF1C9), Color(0xFFFFD6E8), listOf(Pal.sky.copy(alpha = 0.5f) to Offset(0.85f, 0.15f), Pal.mint.copy(alpha = 0.4f) to Offset(0.1f, 0.8f)), t)
    drawRect(Color(0xFFB8E68C), Offset(0f, size.height * 0.88f), Size(size.width, size.height * 0.12f))
    val target = m.foods.firstOrNull { it.state == FoodState.FLYING || it.state == FoodState.HELD }
    val look = target?.let { val d = Offset(it.x - m.monster.x, it.y - m.monster.y); d / max(1f, d.getDistance()) } ?: Offset(sin(t * 0.7f) * 0.6f, -0.3f)
    val g = t - m.giggleAt; val ch = t - m.chompAt
    val wob = (if (g in 0f..0.8f) sin(g * 30f) * (1 - g / 0.8f) else 0f) + (if (ch in 0f..0.3f) sin(ch * 40f) * (1 - ch / 0.3f) else 0f) + 0.15f * sin(t * 2f)
    val dk = (t - m.danceAt) / FeedModel.DANCE
    val dancing = m.dancing(); val rk = t - m.reactAt
    val shiver = if (m.reacting(Reaction.PUCKER)) sin(rk * 60f) * 3f * (1 - rk / FeedModel.REACT) else 0f
    val happy = if (m.reacting(Reaction.WIGGLE)) sin(rk * 18f) * 14f * (1 - rk / FeedModel.REACT) else 0f
    val chompFast = if (m.reacting(Reaction.CRUNCH) && rk < 0.6f) abs(sin(rk * 35f)) * 0.8f else 0f
    val hop = if (dancing) -abs(sin(dk * PI.toFloat() * 6f)) * m.monsterR * 0.18f else 0f
    val sway = if (dancing) sin(dk * PI.toFloat() * 6f) * 14f else 0f
    val mc = m.monster + Offset(shiver, hop)
    rotate(m.lean * 12f + sway + happy, mc + Offset(0f, m.monsterR)) {
      drawMonster(mc, m.monsterR, max(max(m.mouthOpen, chompFast), if (ch in 0f..0.3f) abs(sin(ch * 20f)) * 0.7f else 0f), look, wob, m.tummy, t,
        m.tint, m.belly, if (m.reacting(Reaction.PUCKER) && rk < 0.7f) 1f else 0f, m.tummyColor)
    }
    if (dancing) for (i in 0 until 4) { val a = t * 1.5f + i * 1.57f; val k = (dk * 3f + i * 0.25f) % 1f
      drawNote(m.monster + Offset(cos(a) * m.monsterR * 1.35f, -m.monsterR * (0.6f + k * 0.8f)), mm * 0.025f, Pal.vivid[i * 2 % Pal.vivid.size].copy(alpha = 1f - k)) }
    if (m.reacting(Reaction.WIGGLE)) for (i in 0 until 3) drawHeart(m.monster + Offset((i - 1) * m.monsterR * 0.6f, -m.monsterR * (1.2f + rk * 0.5f)), mm * 0.025f, Pal.pink.copy(alpha = 1f - rk / FeedModel.REACT))
    for (f in m.foods) {
      if (f.state == FoodState.EATEN) continue
      val born = ((t - f.bornAt) / 0.35f).coerceIn(0f, 1f); val s = if (f.bornAt < 0f) 1f else born
      val bob = if (f.state == FoodState.IDLE) sin(t * 2f + f.slot) * mm * 0.012f else 0f
      val rr = m.foodR * s * (if (f.state == FoodState.HELD) 1.15f else 1f) * (if (f.state == FoodState.FLYING) 1f - 0.3f * ((t - f.flyT0) / FeedModel.FLY).coerceIn(0f, 1f) else 1f)
      drawFood(f.kind, Offset(f.x, f.y + bob), rr, if (f.state == FoodState.FLYING) (t - f.flyT0) * 500f else sin(t * 1.5f + f.slot) * 6f)
    }
    m.parts.draw(this)
    if (t - m.lastTouch > FeedModel.IDLE_HINT) m.foods.firstOrNull { it.state == FoodState.IDLE }?.let { drawHand(Offset(it.x, it.y), mm * 0.05f, abs(sin(t * 3f))) }
  }
}
