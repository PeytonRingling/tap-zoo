package com.peyton.toddlergame

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.io.File
import java.util.UUID
import kotlin.math.*
import kotlin.random.Random

/** Looping background music (original, composed in tools/music.py). Ducks under tap sounds. */
object BgMusic {
  private var mp: MediaPlayer? = null; private var cur = 0
  private var base = 0.25f; private var duckK = 1f; private var fadeK = 1f
  private val h by lazy { Handler(Looper.getMainLooper()) }
  private val unduck = Runnable { duckK = 1f; apply() }
  fun play(c: Context, res: Int, level: Float) {
    if (cur == res && mp != null) { base = level; apply(); if (mp?.isPlaying == false) mp?.start(); return }
    stop(); fadeK = 1f
    mp = try { MediaPlayer.create(c, res)?.apply { isLooping = true } } catch (_: Exception) { null }
    cur = res; base = level; apply(); try { mp?.start() } catch (_: Exception) {}
  }
  fun stop() { try { mp?.release() } catch (_: Exception) {}; mp = null; cur = 0 }
  fun pause() { try { mp?.pause() } catch (_: Exception) {} }
  fun resume() { try { if (cur != 0) mp?.start() } catch (_: Exception) {} }
  fun setFade(f: Float) { fadeK = f; apply() }
  var duckDepth = 0.35f
  fun duck() { h.post { duckK = duckDepth; apply(); h.removeCallbacks(unduck); h.postDelayed(unduck, 800) } }
  fun refresh() = apply()
  private fun apply() { val v = (base * duckK * fadeK * Audio.volume).coerceIn(0f, 1f); try { mp?.setVolume(v, v) } catch (_: Exception) {} }
}

/** Family photos chosen by a parent via the system Photo Picker; copied into app-private storage, never uploaded. */
object Photos {
  val images = mutableStateListOf<Pair<File, ImageBitmap>>()
  private fun dir(c: Context) = File(c.filesDir, "photos").apply { mkdirs() }
  fun load(c: Context) { if (images.isNotEmpty()) return; dir(c).listFiles()?.sortedBy { it.lastModified() }?.forEach { f -> decode(f)?.let { images += f to it } } }
  private fun decode(f: File): ImageBitmap? = try { BitmapFactory.decodeFile(f.path)?.asImageBitmap() } catch (_: Exception) { null }
  fun add(c: Context, uris: List<Uri>) {
    Thread {
      val added = uris.take(20).mapNotNull { u ->
        try {
          val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          c.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
          var s = 1; while (max(bounds.outWidth, bounds.outHeight) / (s * 2) >= 700) s *= 2
          val bmp = c.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return@mapNotNull null
          val f = File(dir(c), UUID.randomUUID().toString() + ".jpg"); f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
          f to bmp.asImageBitmap()
        } catch (_: Exception) { null }
      }
      Handler(Looper.getMainLooper()).post { images += added }
    }.start()
  }
  fun remove(f: File) { f.delete(); images.removeAll { it.first == f } }
}

/** Center-cropped photo in a rounded white frame. */
fun DrawScope.drawFramedPhoto(img: ImageBitmap, c: Offset, s: Float, tilt: Float) = rotate(tilt, c) {
  val outer = Rect(c.x - s / 2, c.y - s / 2, c.x + s / 2, c.y + s / 2)
  clayRect(outer, s * 0.1f, Color.White, 0.8f)
  val inner = outer.deflate(s * 0.06f)
  val side = min(img.width, img.height); val src = IntOffset((img.width - side) / 2, (img.height - side) / 2)
  clipPath(Path().apply { addRoundRect(RoundRect(inner, CornerRadius(s * 0.07f))) }) {
    drawImage(img, src, IntSize(side, side), IntOffset(inner.left.toInt(), inner.top.toInt()), IntSize(inner.width.toInt(), inner.height.toInt()))
  }
  drawHeart(Offset(outer.right - s * 0.1f, outer.bottom - s * 0.1f), s * 0.07f, Pal.pink)
}

/** Reward: after lots of taps an animal parade marches across. */
object Parade {
  var taps = 0; var pending by mutableStateOf(false)
  fun onTap() { taps++; if (taps >= 45) { taps = 0; pending = true } }
}

@Composable
fun ParadeOverlay() {
  var start by remember { mutableFloatStateOf(-100f) }
  val parts = remember { Particles(300) }
  val time = rememberTicker { dt, t -> parts.step(dt); if (Parade.pending) { Parade.pending = false; start = t; Audio.play(Sfx.march, 0.6f) } }
  val t = time.value; val p = t - start
  if (p < 0f || p > 8f) return
  Canvas(Modifier.fillMaxSize()) {
    val mm = min(size.width, size.height); val r = mm * 0.075f; val base = size.height - r * 1.6f
    val crits = Critter.entries
    val lead = -r * 2 + (size.width + crits.size * r * 2.6f + r * 4) * (p / 8f)
    crits.forEachIndexed { i, k ->
      val x = lead - i * r * 2.6f; if (x < -r * 2 || x > size.width + r * 2) return@forEachIndexed
      val hop = -abs(sin(p * 7f + i)) * r * 0.5f; val c = Offset(x, base + hop)
      softShadow(Rect(x - r * 0.7f, base + r * 0.95f, x + r * 0.7f, base + r * 1.15f), r * 0.2f, r * 0.12f)
      drawLine(Color(0xFF9A93B8), c + Offset(r * 0.6f, 0f), c + Offset(r * 0.8f, -r * 1.8f), r * 0.06f)
      drawPath(Path().apply { moveTo(c.x + r * 0.8f, c.y - r * 1.8f); lineTo(c.x + r * 1.5f, c.y - r * 1.6f); lineTo(c.x + r * 0.8f, c.y - r * 1.35f); close() }, Pal.vivid[i % 9])
      rotate(sin(p * 7f + i) * 8f, c) { drawCritter(k, c, r, Offset(1f, 0f), 0f, if (sin(p * 3f + i) > 0.5f) 0.6f else 0f) }
    }
    if (Random.nextFloat() < 0.3f) parts.burst(lead, base - r, 2, Pal.vivid, kind = 4, speed = 400f, size = r * 0.12f, up = 300f, decay = 0.6f)
    parts.draw(this)
  }
}

private class Twinkle(val x: Float, val y: Float, val t0: Float)

@Composable
fun BedtimeScreen() {
  val ctx = LocalContext.current; val act = ctx as? Activity
  val minutes = Prefs.bedtimeMinutes
  val twinkles = remember { ArrayList<Twinkle>() }
  val stars = remember { List(70) { Offset(Random.nextFloat(), Random.nextFloat() * 0.75f) } }
  var asleep by remember { mutableStateOf(false) }
  val time = rememberTicker { _, t ->
    val total = minutes * 60f; val fade = ((total - t) / 60f).coerceIn(0f, 1f)
    BgMusic.setFade(fade)
    if (t >= total && !asleep) { asleep = true; BgMusic.stop()
      act?.window?.let { w -> w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); w.attributes = w.attributes.apply { screenBrightness = 0.02f } } }
    twinkles.removeAll { t - it.t0 > 2.5f }
  }
  DisposableEffect(Unit) { onDispose { act?.window?.let { w -> w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }; BgMusic.setFade(1f) } }
  var w by remember { mutableFloatStateOf(1f) }
  Canvas(Modifier.fillMaxSize().onSizeChanged { w = it.width.toFloat() }.multiTouch(asleep, onDown = { _, p ->
    if (!asleep) { twinkles += Twinkle(p.x, p.y, time.value); Audio.play(Sfx.bells[(p.x / w * 6).toInt().coerceIn(0, 6) + 3], 0.15f) } })) {
    val t = time.value; val mm = min(size.width, size.height)
    val total = minutes * 60f; val dim = if (asleep) 0.25f else (0.55f + 0.45f * ((total - t) / 60f).coerceIn(0f, 1f))
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1030), Color(0xFF1C1F4A), Color(0xFF2B2660))))
    stars.forEachIndexed { i, s -> drawStar(Offset(s.x * size.width, s.y * size.height), 2.5f + (i % 3) * 1.5f, Color.White.copy(alpha = (0.25f + 0.25f * sin(t * 0.6f + i)) * dim)) }
    val mc = Offset(size.width * 0.78f, size.height * 0.2f); val mr = mm * 0.11f
    drawCircle(Brush.radialGradient(listOf(Color(0x55FFF1B0), Color(0x00FFF1B0)), mc, mr * 2.4f), mr * 2.4f, mc)
    drawCircle(Color(0xFFFFF1B8).copy(alpha = dim), mr, mc)
    drawArc(Color(0xFF6A5A2A).copy(alpha = dim), 20f, 140f, false, mc + Offset(-mr * 0.45f, -mr * 0.25f), Size(mr * 0.3f, mr * 0.2f), style = androidx.compose.ui.graphics.drawscope.Stroke(mr * 0.06f))
    drawArc(Color(0xFF6A5A2A).copy(alpha = dim), 20f, 140f, false, mc + Offset(mr * 0.15f, -mr * 0.25f), Size(mr * 0.3f, mr * 0.2f), style = androidx.compose.ui.graphics.drawscope.Stroke(mr * 0.06f))
    // cloud bed with sleeping friends, breathing slowly
    val bedY = size.height * 0.72f
    for (i in 0 until 5) drawCircle(Color(0xFF5B5A9C).copy(alpha = 0.9f * dim), mm * 0.16f, Offset(size.width * (0.1f + i * 0.2f), bedY + mm * 0.12f))
    drawRect(Color(0xFF5B5A9C).copy(alpha = 0.9f * dim), Offset(0f, bedY + mm * 0.12f), Size(size.width, size.height))
    listOf(Critter.CAT, Critter.DOG, Critter.DUCK).forEachIndexed { i, k ->
      val c = Offset(size.width * (0.28f + i * 0.22f), bedY - mm * 0.02f); val br = 1f + 0.03f * sin(t * 1.2f + i)
      drawCritter(k, c, mm * 0.085f * br, Offset.Zero, 1f, 0f)
      val zt = ((t * 0.3f + i * 0.33f) % 1f)
      drawStar(c + Offset(mm * 0.08f + zt * mm * 0.05f, -mm * 0.12f - zt * mm * 0.15f), mm * 0.015f, Color.White.copy(alpha = (1 - zt) * 0.6f * dim))
    }
    for (tw in twinkles) { val k = (t - tw.t0) / 2.5f; drawStar(Offset(tw.x, tw.y - k * mm * 0.05f), mm * 0.03f * (1 - k * 0.5f), Pal.sun.copy(alpha = (1 - k) * 0.8f), k * 90f) }
    if (asleep) drawRect(Color.Black.copy(alpha = 0.55f))
  }
}
