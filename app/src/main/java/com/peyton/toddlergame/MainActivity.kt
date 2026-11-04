package com.peyton.toddlergame

import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    Prefs.init(this); AnimalSounds.init(this)
    val app = applicationContext
    Thread { runCatching { Samples.load(app) }; runCatching { Sfx.prewarm() } }.apply { name = "sfx-prewarm"; isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    Audio.volume = Prefs.volumeGain(Prefs.volume); BgMusic.musicGain = Prefs.volumeGain(Prefs.musicVolume); Fx.haptics = Prefs.haptics
    @Suppress("DEPRECATION") run { Fx.vib = getSystemService(Vibrator::class.java) }
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    // Draw under the camera cutout on every edge (outer + inner Pixel Fold screens).
    if (Build.VERSION.SDK_INT >= 30) window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    else if (Build.VERSION.SDK_INT >= 28) window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} })
    hideBars()
    val start = intent?.getStringExtra("screen")
    setContent { App(start, onExit = { unlock(); finishAndRemoveTask() }, onLock = { on -> if (on) lock() else unlock() }) }
  }
  override fun onResume() { super.onResume(); Audio.start(); BgMusic.resume(); hideBars()
    if (Prefs.kidLock && !askedThisSession) { askedThisSession = true; window.decorView.post { lock() } }
    Updater.onResume(this, lifecycleScope) }

  // ------------------------------------------------------------ app updates (Parent settings only)
  // Screen pinning blocks Android's install prompt and the "install unknown apps" page, so updates unpin first;
  // "Back to play" pins again.
  fun checkForUpdates() = Updater.checkNow(this, lifecycleScope)
  fun installUpdate() { unlock(); Updater.downloadAndInstall(this, lifecycleScope) }
  fun allowInstalls() { unlock(); try { startActivity(Updater.permissionIntent(this)) } catch (_: Exception) {} }
  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    if (intent.action == Updater.ACTION_STATUS) Updater.onStatus(this, intent)?.let { confirm ->
      unlock()
      try { startActivity(confirm) } catch (_: Exception) { Updater.ui = Updater.ui.copy(phase = UpdPhase.ERROR, message = "Android didn't show the install prompt.") }
    }
  }
  private var askedThisSession = false
  /** Screen pinning (lock task). Without device-owner, Android shows a one-time confirm; unpinning then needs the PIN if enabled. */
  fun lock() { if (!Prefs.kidLock || isLocked()) return; try { startLockTask() } catch (_: Exception) {} }
  fun unlock() { if (!isLocked()) return; try { stopLockTask() } catch (_: Exception) {} }
  private fun isLocked() = (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
  override fun onPause() { super.onPause(); Audio.stop(); BgMusic.pause() }
  override fun onDestroy() { super.onDestroy(); if (isFinishing) AnimalSounds.release() }
  override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideBars() }
  private fun hideBars() {
    WindowInsetsControllerCompat(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
  }
}

@Composable
fun App(start: String?, onExit: () -> Unit, onLock: (Boolean) -> Unit = {}) {
  var screen by remember { mutableStateOf(start?.let { runCatching { Mode.valueOf(it) }.getOrNull() }) }
  var settings by remember { mutableStateOf(start == "SETTINGS") }
  var enabled by remember { mutableStateOf(Prefs.enabled) }
  var musicSlots by remember { mutableStateOf(Prefs.musicSlots) }
  var musicVol by remember { mutableIntStateOf(Prefs.musicVolume) }
  val ctx = androidx.compose.ui.platform.LocalContext.current
  LaunchedEffect(Unit) { Photos.load(ctx) }
  LaunchedEffect(screen, musicSlots, musicVol) {
    val pick = MusicDirector.trackFor(screen, musicSlots, musicVol) { BgMusic.has(ctx, it) }
    if (pick != null) BgMusic.play(ctx, pick) else BgMusic.stop()
  }
  Box(Modifier.fillMaxSize()) {
    AnimatedContent(screen, transitionSpec = {
      (fadeIn(tween(260)) + scaleIn(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow), initialScale = 0.88f)) togetherWith
        (fadeOut(tween(200)) + scaleOut(tween(220), targetScale = 1.06f))
    }, label = "screens") { scr ->
      when (scr) {
        null -> MenuScreen(Mode.TILES.filter { it in enabled }) { screen = it; onLock(true) }
        Mode.BALLOONS -> BalloonsScreen()
        Mode.AQUARIUM -> AquariumScreen()
        Mode.VEHICLES -> VehiclesScreen()
        Mode.DRUMS -> DrumsScreen()
        Mode.BEDTIME -> BedtimeScreen()
        Mode.ANIMALS -> AnimalsScreen()
        Mode.BUBBLES -> BubblesScreen()
        Mode.PAINT -> PaintScreen()
        Mode.PEEKABOO -> PeekabooScreen()
      }
    }
    if (screen != null && screen != Mode.BEDTIME) ParadeOverlay()
    // Block accidental edge back-swipes (Android caps exclusion at ~200dp per edge, so cover the middle band).
    for (al in listOf(Alignment.CenterStart, Alignment.CenterEnd)) Box(Modifier.align(al).width(28.dp).height(200.dp).systemGestureExclusion())
    if (screen == null) HoldButton(Modifier.align(Alignment.TopEnd), gear = true) { settings = true }
    else HoldButton(Modifier.align(Alignment.TopStart), gear = false) { screen = null }
    if (settings) SettingsPanel(enabled, onToggle = { mo ->
      val n = if (mo in enabled) enabled - mo else enabled + mo
      if (n.isNotEmpty()) { enabled = n; Prefs.enabled = n }
    }, onClose = { settings = false; onLock(true) }, onExit = onExit, onKidLock = { on -> Prefs.kidLock = on; onLock(on) }, onMusicSlots = { musicSlots = it }, onMusicVolume = { musicVol = it },
      onBedtime = { settings = false; screen = Mode.BEDTIME; onLock(true) })
  }
}
