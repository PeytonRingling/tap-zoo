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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.*
import kotlin.random.Random

/** A ring spreading on the water. [big] 1 = a tap's splash ring, smaller = a wake ripple from a dragging finger. */
internal class Ripple(val x: Float, val y: Float, val t0: Float, val big: Float)
/** A lily pad at a screen fraction; [bobAt] = last time it was tapped. */
internal class LilyPad(val fx: Float, val fy: Float, val flower: Boolean) { var bobAt = -10f }
internal enum class PondKind { DUCK, FROG }
/** A friend who popped up: ducks paddle on the water, frogs sit on a lily pad ([pad] index). Tapping one makes it call. */
internal class PondFriend(val kind: PondKind, var x: Float, var y: Float, val t0: Float, val pad: Int, val dir: Float) { var reactAt = -10f }
/** A fish leaping out of the water from [x0] to [x1]; splashes back in after [PondModel.FISH_T]. */
internal class FishJump(val x0: Float, val y0: Float, val x1: Float, val t0: Float, val color: Color) { var landed = false }
internal enum class Surprise { FROG_JUMP, DUCK_FAMILY }

/** Splash pond, for an 18-month-old: the whole screen is water. Every tap makes ripples and a plop; ducks and frogs pop up
 *  near taps and quack/ribbit when touched; lily pads bob; a dragging finger leaves a wake. Now and then a fish jumps, and
 *  every [SURPRISE_EVERY] taps (or after a quiet spell) a big frog leaps across or a duck family paddles by. No fail, no text. */
internal class PondModel(private val sink: SoundSink = GameSound, private val rnd: Random = Random.Default) {
  companion object {
    const val MAX_RIPPLES = 48; const val RIPPLE_LIFE = 1.6f; const val WAKE_STEP = 0.06f; const val WAKE_SOUND_GAP = 0.18f
    const val PLOP_GAP = 0.05f; const val MAX_FRIENDS = 4; const val FRIEND_LIFE = 9f; const val FRIEND_EVERY = 2
    const val FISH_EVERY = 5; const val FISH_T = 0.9f; const val MAX_FISH = 2; const val SURPRISE_EVERY = 12; const val IDLE = 9f
    const val FROG_JUMP_T = 1.8f; const val FAMILY_T = 7f; const val CALL_GAP = 0.35f
    val PADS = listOf(LilyPad(0.17f, 0.27f, true), LilyPad(0.8f, 0.2f, false), LilyPad(0.22f, 0.78f, false), LilyPad(0.82f, 0.74f, true))
    val FISH = listOf(Color(0xFFFF9F43), Color(0xFFFFD166), Color(0xFFFF6B9A))
  }
  var w = 1f; var h = 1f; var t = 0f; var lastTouch = 0f
  val pads = PADS.map { LilyPad(it.fx, it.fy, it.flower) }
  val ripples = ArrayList<Ripple>(); val friends = ArrayList<PondFriend>(); val fish = ArrayList<FishJump>()
  val parts = Particles(300).apply { gravity = 1600f }
  var taps = 0; private set
  var surprise: Surprise? = null; private set
  var surpriseAt = -100f; private set
  private var surprises = 0; private var friendsMade = 0
  private var lastPlop = -10f; private var lastSwish = -10f; private var lastWake = 0f
  private val wakeFrom = HashMap<Long, Offset>()

  val m get() = min(w, h)
  val padR get() = m * 0.12f
  val friendR get() = m * 0.1f
  fun padAt(i: Int) = Offset(pads[i].fx * w, pads[i].fy * h)
  /** Where a friend is drawn (frogs ride their pad). */
  fun friendAt(f: PondFriend) = if (f.pad >= 0) padAt(f.pad) + Offset(0f, -padR * 0.25f) else Offset(f.x, f.y)
  fun fishAt(f: FishJump): Offset { val k = ((t - f.t0) / FISH_T).coerceIn(0f, 1f)
    return Offset(f.x0 + (f.x1 - f.x0) * k, f.y0 - sin(k * PI.toFloat()) * m * 0.28f) }
  /** The big surprise frog's position along its leap (left → right), or the duck family's leader. */
  fun surprisePos(): Offset? { val s = surprise ?: return null; val k = t - surpriseAt
    return when (s) {
      Surprise.FROG_JUMP -> { val p = (k / FROG_JUMP_T).coerceIn(0f, 1f); Offset(-m * 0.2f + (w + m * 0.4f) * p, h * 0.62f - sin(p * PI.toFloat()) * h * 0.45f) }
      Surprise.DUCK_FAMILY -> Offset(-m * 0.15f + (w + m * 0.9f) * (k / FAMILY_T).coerceIn(0f, 1f), h * 0.5f + sin(k * 2f) * m * 0.015f)
    } }

  private fun ripple(x: Float, y: Float, big: Float) {
    if (ripples.size >= MAX_RIPPLES) ripples.removeAt(0)
    ripples += Ripple(x, y, t, big)
  }
  private fun droplets(x: Float, y: Float, n: Int) = parts.burst(x, y, n, listOf(Color(0xFFBFEFFF), Color.White, Color(0xFF8FD8F8)),
    speed = m * 0.9f, size = m * 0.012f, up = m * 0.8f, decay = 1.6f)
  private fun plop(x: Float, y: Float, gain: Float = 0.7f) {
    ripple(x, y, 1f); droplets(x, y, 10)
    if (t - lastPlop >= PLOP_GAP) { lastPlop = t; sink.play(Sfx.plops[rnd.nextInt(Sfx.plops.size)], gain) }
  }
  private fun call(f: PondFriend) {
    if (t - f.reactAt < CALL_GAP) return
    f.reactAt = t; sink.animal(if (f.kind == PondKind.DUCK) Critter.DUCK else Critter.FROG, 0.9f)
    val p = friendAt(f); parts.burst(p.x, p.y - friendR, 4, listOf(Color(0xFFFF6B9A), Color(0xFFFFD166)), kind = 2, speed = m * 0.4f, size = m * 0.025f, up = m * 0.5f)
  }
  private fun freePad(near: Offset): Int? = pads.indices.filter { i -> friends.none { it.pad == i } }.minByOrNull { (padAt(it) - near).getDistance() }

  /** A friend pops up near [p]: alternately a duck on the water and a frog on the closest free lily pad. */
  private fun popFriend(p: Offset) {
    if (friends.size >= MAX_FRIENDS) friends.removeAt(0)
    val frog = friendsMade % 2 == 1; friendsMade++
    val pad = if (frog) freePad(p) else null
    val f = if (pad != null) PondFriend(PondKind.FROG, 0f, 0f, t, pad, 1f)
      else { val a = rnd.nextFloat() * 2 * PI.toFloat(); val d = m * 0.2f
        PondFriend(PondKind.DUCK, (p.x + cos(a) * d).coerceIn(friendR, w - friendR), (p.y + sin(a) * d).coerceIn(friendR * 1.5f, h - friendR), t, -1, if (rnd.nextBoolean()) 1f else -1f) }
    friends += f; val q = friendAt(f); ripple(q.x, q.y, 0.8f); call(f)
  }

  fun startSurprise(s: Surprise = if (surprises % 2 == 0) Surprise.FROG_JUMP else Surprise.DUCK_FAMILY) {
    surprise = s; surpriseAt = t; surprises++
    when (s) {
      Surprise.FROG_JUMP -> { sink.animal(Critter.FROG, 1f); sink.play(Sfx.wheee, 0.6f) }
      Surprise.DUCK_FAMILY -> { sink.animal(Critter.DUCK, 1f); sink.animal(Critter.CHICK, 0.7f, 450) }
    }
  }

  fun down(id: Long, p: Offset) {
    lastTouch = t; sink.haptic(); wakeFrom[id] = p
    friends.lastOrNull { (friendAt(it) - p).getDistance() <= friendR * 1.5f }?.let { f -> call(f); ripple(p.x, p.y, 0.6f); return }
    pads.indices.firstOrNull { (padAt(it) - p).getDistance() <= padR * 1.15f }?.let { i ->
      pads[i].bobAt = t; ripple(padAt(i).x, padAt(i).y, 0.9f); sink.play(Sfx.plip, 0.6f)
      if (friends.none { it.pad == i }) { if (friends.size >= MAX_FRIENDS) friends.removeAt(0)
        val f = PondFriend(PondKind.FROG, 0f, 0f, t, i, 1f); friends += f; call(f) }
      return }
    taps++; plop(p.x, p.y)
    if (taps % FRIEND_EVERY == 0) popFriend(p)
    if (taps % FISH_EVERY == 0 && fish.size < MAX_FISH) {
      val dx = (if (p.x > w / 2) -1f else 1f) * m * 0.3f
      fish += FishJump(p.x, p.y, p.x + dx, t, FISH[rnd.nextInt(FISH.size)]); sink.play(Sfx.blub, 0.6f)
    }
    if (taps % SURPRISE_EVERY == 0 && surprise == null) startSurprise()
  }

  /** Dragging leaves a wake: a small ripple every [WAKE_STEP] of the short side, with a soft swish now and then. */
  fun move(id: Long, p: Offset) {
    lastTouch = t
    val from = wakeFrom[id] ?: run { wakeFrom[id] = p; return }
    if ((p - from).getDistance() < m * WAKE_STEP) return
    wakeFrom[id] = p; lastWake = t; ripple(p.x, p.y, 0.45f); droplets(p.x, p.y, 2)
    if (t - lastSwish >= WAKE_SOUND_GAP) { lastSwish = t; sink.play(Sfx.swish, 0.5f) }
  }
  fun up(id: Long) { wakeFrom.remove(id) }

  fun step(dt: Float) {
    parts.step(dt)
    ripples.removeAll { t - it.t0 > RIPPLE_LIFE }
    friends.removeAll { t - it.t0 > FRIEND_LIFE }
    for (f in friends) if (f.pad < 0) { f.x += f.dir * m * 0.04f * dt
      if (f.x < friendR || f.x > w - friendR) { f.x = f.x.coerceIn(friendR, w - friendR) } }
    for (f in fish) if (!f.landed && t - f.t0 >= FISH_T) { f.landed = true; plop(f.x1, f.y0, 0.6f) }
    fish.removeAll { it.landed && t - it.t0 > FISH_T + 0.1f }
    surprise?.let { s -> val k = t - surpriseAt
      when (s) {
        Surprise.FROG_JUMP -> if (k >= FROG_JUMP_T) { surprise = null; val x = w * 0.85f; ripple(x, h * 0.62f, 1f); droplets(x, h * 0.62f, 24); sink.play(Sfx.splash, 0.7f) }
        Surprise.DUCK_FAMILY -> { if (((k - dt) / 0.35f).toInt() != (k / 0.35f).toInt()) surprisePos()?.let { ripple(it.x - m * 0.12f, it.y + m * 0.05f, 0.4f) }
          if (k >= FAMILY_T) surprise = null }
      } }
    if (surprise == null && t - lastTouch > IDLE && t - surpriseAt > IDLE) startSurprise()
  }
}

@Composable
fun PondScreen() { PondScreenFor(remember { PondModel() }) }

private val WATER_TOP = Color(0xFF5CC8E8); private val WATER_BOT = Color(0xFF2F8FC4)
private val PAD = Color(0xFF5DBB63); private val PAD_DARK = Color(0xFF3E9A4C)

@Composable
internal fun PondScreenFor(m: PondModel) {
  val time = rememberTicker { dt, t -> m.t = t; m.step(dt) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { m.w = it.width.toFloat(); m.h = it.height.toFloat() }
    .multiTouch(Unit, onDown = { id, p -> m.down(id, p) }, onMove = { id, p -> m.move(id, p) }, onUp = { m.up(it) })) {
    val t = time.value; m.w = size.width; m.h = size.height; val mm = m.m
    drawRect(Brush.verticalGradient(listOf(WATER_TOP, WATER_BOT)))
    for (i in 0 until 9) { val y = size.height * (i + 0.5f) / 9; val x = (sin(t * 0.4f + i * 1.7f) * 0.5f + 0.5f) * size.width   // gentle sparkle streaks
      drawLine(Color.White.copy(alpha = 0.18f), Offset(x - mm * 0.08f, y), Offset(x + mm * 0.08f, y), mm * 0.008f) }
    for (r in m.ripples) { val k = ((t - r.t0) / PondModel.RIPPLE_LIFE).coerceIn(0f, 1f); val rad = mm * (0.03f + 0.22f * r.big * k)
      for (j in 0 until if (r.big >= 0.8f) 3 else 1) { val rr = rad * (1f - j * 0.28f); if (rr <= 0f) continue
        drawOval(Color.White.copy(alpha = 0.7f * (1 - k) * (if (r.big >= 0.8f) 1f else 0.7f)), Offset(r.x - rr, r.y - rr * 0.7f), Size(rr * 2, rr * 1.4f),
          style = Stroke(mm * 0.008f * (1.5f - k))) } }
    m.pads.forEachIndexed { i, p -> val b = t - p.bobAt; val bob = if (b in 0f..0.8f) sin(b * 18f) * (1 - b / 0.8f) else 0f
      drawLilyPad(m.padAt(i) + Offset(0f, bob * mm * 0.012f), m.padR * (1 + bob * 0.06f), i * 50f + sin(t * 0.5f + i) * 4f, p.flower) }
    for (f in m.friends) { val age = t - f.t0; val pop = (age / 0.3f).coerceIn(0f, 1f); val dive = ((PondModel.FRIEND_LIFE - age) / 0.5f).coerceIn(0f, 1f)
      val react = t - f.reactAt; val hop = if (react in 0f..0.5f) sin(react / 0.5f * PI.toFloat()) else 0f
      val p = m.friendAt(f) + Offset(0f, -hop * mm * 0.08f + sin(t * 2f + f.t0) * mm * 0.006f); val r = m.friendR * pop * dive
      if (r <= 1f) continue
      when (f.kind) {
        PondKind.DUCK -> drawDuck(p, r, f.dir, if (react in 0f..0.5f) 1f else 0.3f)
        PondKind.FROG -> drawCritter(Critter.FROG, p, r, Offset(0f, 0.2f), if ((t + f.t0) % 3f < 0.12f) 1f else 0f, if (react in 0f..0.5f) 1f else 0.4f)
      } }
    for (f in m.fish) if (!f.landed) drawFish(m.fishAt(f), mm * 0.06f, f.color, if (f.x1 > f.x0) 1f else -1f, ((t - f.t0) / PondModel.FISH_T - 0.5f) * 70f)
    m.surprisePos()?.let { p -> when (m.surprise) {
      Surprise.FROG_JUMP -> drawCritter(Critter.FROG, p, mm * 0.17f, Offset(0.6f, 0f), 0f, 1f)
      Surprise.DUCK_FAMILY -> { drawDuck(p, mm * 0.11f, 1f, 0.5f)
        for (j in 1..3) { val q = p - Offset(mm * 0.2f * j, sin(t * 6f + j) * mm * 0.01f); drawDuck(q, mm * 0.065f, 1f, 0.3f, Critter.CHICK.body) } }
      null -> {}
    } }
    m.parts.draw(this)
  }
}

private fun DrawScope.drawLilyPad(c: Offset, r: Float, rot: Float, flower: Boolean) {
  rotate(rot, c) {
    drawArc(Color.Black.copy(alpha = 0.12f), 20f, 320f, true, Offset(c.x - r * 0.94f, c.y - r * 0.9f), Size(r * 2, r * 2))
    drawArc(PAD, 20f, 320f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))   // the notch
    for (a in listOf(60f, 140f, 220f, 300f)) { val rad = Math.toRadians(a.toDouble()).toFloat()
      drawLine(PAD_DARK, c, c + Offset(cos(rad) * r * 0.85f, sin(rad) * r * 0.85f), r * 0.04f) }
  }
  if (flower) { val f = c + Offset(-r * 0.35f, -r * 0.3f)
    for (i in 0 until 6) { val a = i * PI.toFloat() / 3; drawCircle(Color(0xFFFFB3D1), r * 0.16f, f + Offset(cos(a) * r * 0.15f, sin(a) * r * 0.15f)) }
    drawCircle(Color(0xFFFFD166), r * 0.1f, f) }
}

/** A floating duck seen from the side: body, tail, head with beak. [dir] 1 = facing right. */
private fun DrawScope.drawDuck(c: Offset, r: Float, dir: Float, mouth: Float, body: Color = Color.White) {
  drawOval(Color.Black.copy(alpha = 0.12f), Offset(c.x - r * 1.1f, c.y + r * 0.2f), Size(r * 2.2f, r * 0.6f))
  drawOval(body, Offset(c.x - r, c.y - r * 0.45f), Size(r * 2f, r * 1.0f))
  val tail = Path().apply { moveTo(c.x - dir * r * 0.8f, c.y - r * 0.2f); lineTo(c.x - dir * r * 1.25f, c.y - r * 0.6f); lineTo(c.x - dir * r * 0.6f, c.y + r * 0.1f); close() }
  drawPath(tail, body)
  drawOval(Color.Black.copy(alpha = 0.06f), Offset(c.x - r * 0.5f, c.y - r * 0.2f), Size(r * 1.0f, r * 0.5f))   // wing
  val head = c + Offset(dir * r * 0.55f, -r * 0.7f)
  drawCircle(body, r * 0.48f, head)
  val bk = head + Offset(dir * r * 0.4f, r * 0.08f); val open = r * 0.12f * mouth
  drawPath(Path().apply { moveTo(bk.x, bk.y - r * 0.12f); lineTo(bk.x + dir * r * 0.38f, bk.y - open); lineTo(bk.x, bk.y + r * 0.02f); close() }, Color(0xFFFF9F43))
  drawPath(Path().apply { moveTo(bk.x, bk.y + r * 0.02f); lineTo(bk.x + dir * r * 0.32f, bk.y + open + r * 0.04f); lineTo(bk.x, bk.y + r * 0.14f); close() }, Color(0xFFF08A2C))
  drawCircle(Color(0xFF222222), r * 0.09f, head + Offset(dir * r * 0.12f, -r * 0.1f))
  drawCircle(Color.White, r * 0.03f, head + Offset(dir * r * 0.15f, -r * 0.13f))
  drawCircle(Color(0xFFFFB3C6).copy(alpha = 0.6f), r * 0.1f, head + Offset(dir * r * 0.05f, r * 0.15f))
}

private fun DrawScope.drawFish(c: Offset, r: Float, color: Color, dir: Float, tilt: Float) {
  rotate(tilt * dir, c) {
    drawOval(color, Offset(c.x - r, c.y - r * 0.55f), Size(r * 2, r * 1.1f))
    drawPath(Path().apply { moveTo(c.x - dir * r * 0.8f, c.y); lineTo(c.x - dir * r * 1.5f, c.y - r * 0.55f); lineTo(c.x - dir * r * 1.5f, c.y + r * 0.55f); close() }, color)
    drawCircle(Color.White, r * 0.2f, c + Offset(dir * r * 0.5f, -r * 0.12f)); drawCircle(Color(0xFF222222), r * 0.1f, c + Offset(dir * r * 0.55f, -r * 0.12f))
  }
}
