package com.peyton.toddlergame

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** What we need to know about an APK (or the installed app) to decide whether it's a safe self-update. */
data class ApkMeta(val pkg: String, val versionCode: Long, val versionName: String?, val certs: Set<String>)

enum class UpdPhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, NEEDS_PERMISSION, INSTALLING, CONFIRM, DONE, ERROR }
data class UpdUi(val phase: UpdPhase = UpdPhase.IDLE, val message: String = "", val release: GhRelease? = null, val progress: Float? = null)

/**
 * Parent-only self-update from GitHub Releases (Parent settings › App updates; never runs on its own).
 * Verification is the TV Launcher's updater logic: same package, shared signer, newer build, plus the pinned release cert
 * and the versionCode promised by the asset name. Only then does PackageInstaller (session API) get the APK, and Android
 * shows its own install prompt.
 */
object Updater {
  const val ACTION_STATUS = "com.peyton.toddlergame.INSTALL_STATUS"
  const val RELEASE_CERT = "65f0d4cb5041d1d60f2fbbb44d0bc73174de9ea1668be0038129630a6a4d113f"
  const val GH_REPO = "PeytonRingling/tap-zoo"
  const val GH_ASSET_PREFIX = "tap-zoo"

  var ui by mutableStateOf(UpdUi())

  // ------------------------------------------------------------ APK inspection + rules (from the TV Launcher)
  fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
  @Suppress("DEPRECATION")
  private val sigFlag get() = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
  @Suppress("DEPRECATION")
  private fun certsOf(pi: PackageInfo): Set<String> {
    val sigs = if (Build.VERSION.SDK_INT >= 28) {
      val si = pi.signingInfo ?: return emptySet()
      if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
    } else pi.signatures
    return sigs?.map { sha256(it.toByteArray()) }?.toSet() ?: emptySet()
  }
  @Suppress("DEPRECATION")
  fun versionCodeOf(pi: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()

  /** Test seams (Robolectric can't parse APK archives or signatures); production uses PackageManager. */
  @Volatile internal var inspector: ((Context, File) -> ApkMeta?)? = null
  @Volatile internal var installedProvider: ((Context) -> ApkMeta)? = null

  fun installed(ctx: Context): ApkMeta = installedProvider?.invoke(ctx) ?: ctx.packageManager.getPackageInfo(ctx.packageName, sigFlag).let {
    ApkMeta(it.packageName, versionCodeOf(it), it.versionName, certsOf(it)) }
  fun inspect(ctx: Context, f: File): ApkMeta? = inspector?.invoke(ctx, f) ?: try {
    ctx.packageManager.getPackageArchiveInfo(f.path, sigFlag)?.let { ApkMeta(it.packageName, versionCodeOf(it), it.versionName, certsOf(it)) }
  } catch (_: Throwable) { null }

  /** null = OK to install; otherwise a reason for the parent. */
  fun check(new: ApkMeta?, cur: ApkMeta, rel: GhRelease): String? = when {
    new == null -> "The download isn't a valid Android app (.apk)."
    new.pkg != cur.pkg -> "The download is a different app (${new.pkg}), not Tap Zoo."
    new.certs.isEmpty() -> "The download isn't signed."
    new.certs.intersect(cur.certs).isEmpty() -> "The download is signed with a different key, so it can't update Tap Zoo."
    new.certs != setOf(RELEASE_CERT) -> "The download isn't signed with the Tap Zoo release key."
    new.versionCode <= cur.versionCode -> "The download is build ${new.versionCode}; this phone already has build ${cur.versionCode}."
    new.versionCode != rel.versionCode -> "The download is build ${new.versionCode}, but the release says build ${rel.versionCode}."
    else -> null
  }

  fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()
  fun downloadFile(ctx: Context) = File(File(ctx.cacheDir, "updates").apply { mkdirs() }, "github.apk")
  fun pendingFile(ctx: Context) = File(File(ctx.cacheDir, "updates").apply { mkdirs() }, "verified.apk")
  private fun installedVc(ctx: Context) = runCatching { installed(ctx).versionCode }.getOrDefault(Long.MAX_VALUE)

  // ------------------------------------------------------------ flow
  fun checkNow(ctx: Context, scope: CoroutineScope) {
    if (ui.phase in setOf(UpdPhase.CHECKING, UpdPhase.DOWNLOADING, UpdPhase.INSTALLING)) return
    ui = UpdUi(UpdPhase.CHECKING, "Checking for updates…")
    scope.launch {
      val vc = installedVc(ctx)
      ui = when (val r = withContext(Dispatchers.IO) { GitHubReleases.fetchLatest(GH_REPO, GH_ASSET_PREFIX, vc) }) {
        is GhCheck.Available -> UpdUi(UpdPhase.AVAILABLE, "Version ${r.release.versionName} is available.", r.release)
        is GhCheck.UpToDate -> UpdUi(UpdPhase.UP_TO_DATE, "Tap Zoo is up to date (version ${r.latest.versionName}).", r.latest)
        is GhCheck.Failed -> UpdUi(UpdPhase.ERROR, r.reason)
      }
    }
  }

  /** Download → verify → stage → install (or ask for the "install unknown apps" permission first). */
  fun downloadAndInstall(act: Activity, scope: CoroutineScope) {
    val rel = ui.release?.takeIf { ui.phase == UpdPhase.AVAILABLE } ?: return checkNow(act, scope)
    ui = UpdUi(UpdPhase.DOWNLOADING, "Downloading version ${rel.versionName}…", rel, 0f)
    scope.launch {
      val why = withContext(Dispatchers.IO) {
        val f = downloadFile(act)
        try {
          GitHubReleases.download(rel, f) { read, total -> if (total > 0) act.runOnUiThread { if (ui.phase == UpdPhase.DOWNLOADING) ui = ui.copy(progress = read / total.toFloat()) } }
          check(inspect(act, f), installed(act), rel)?.also { f.delete() } ?: run {
            val dest = pendingFile(act); dest.delete(); if (!f.renameTo(dest)) { f.copyTo(dest, true); f.delete() }; null
          }
        } catch (e: IOException) { f.delete(); e.message ?: "Download failed." }
      }
      if (why != null) { ui = UpdUi(UpdPhase.ERROR, why, rel); return@launch }
      if (canInstall(act)) installPending(act, scope)
      else ui = UpdUi(UpdPhase.NEEDS_PERMISSION, "Version ${rel.versionName} is ready. Allow Tap Zoo to install updates, then come back.", rel)
    }
  }

  fun permissionIntent(ctx: Context) = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  /** Called from onResume: picks the flow back up after the parent allowed installs. */
  fun onResume(act: Activity, scope: CoroutineScope) {
    if (ui.phase == UpdPhase.NEEDS_PERMISSION && canInstall(act)) installPending(act, scope)
  }

  fun installPending(act: Activity, scope: CoroutineScope) {
    val f = pendingFile(act)
    if (!f.exists()) { ui = UpdUi(UpdPhase.ERROR, "The update file is gone. Check for updates again."); return }
    ui = ui.copy(phase = UpdPhase.INSTALLING, message = "Preparing the install…", progress = null)
    scope.launch {
      val err = try { commit(act, f); null } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { e.message ?: e.javaClass.simpleName }
      if (err != null) ui = ui.copy(phase = UpdPhase.ERROR, message = "Couldn't start the install: $err")
    }
  }

  private suspend fun commit(ctx: Context, f: File): Int {
    val pi = ctx.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
      setAppPackageName(ctx.packageName); setSize(f.length())
    }
    val id = withContext(Dispatchers.Main) { pi.createSession(params) }
    try {
      val session = withContext(Dispatchers.Main) { pi.openSession(id) }
      session.use {
        withContext(Dispatchers.IO) { session.openWrite("base.apk", 0, f.length()).use { out -> f.inputStream().use { it.copyTo(out, 256 * 1024) }; runCatching { session.fsync(out) } } }
        withContext(Dispatchers.Main) {
          val intent = Intent(ctx, MainActivity::class.java).setAction(ACTION_STATUS).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
          val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
          session.commit(PendingIntent.getActivity(ctx, id, intent, flags).intentSender)
        }
      }
    } catch (t: Throwable) {
      withContext(NonCancellable + Dispatchers.Main) { runCatching { pi.abandonSession(id) } }
      throw t
    }
    return id
  }

  /** PackageInstaller result (MainActivity.onNewIntent). Returns the system confirm intent to start, if any. */
  fun onStatus(ctx: Context, intent: Intent): Intent? {
    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
    when (status) {
      PackageInstaller.STATUS_PENDING_USER_ACTION -> {
        ui = ui.copy(phase = UpdPhase.CONFIRM, message = "Confirm the update in Android's prompt.")
        @Suppress("DEPRECATION") return intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      PackageInstaller.STATUS_SUCCESS -> { pendingFile(ctx).delete(); ui = UpdUi(UpdPhase.DONE, "Updated! Tap Zoo restarts on the new version.") }
      PackageInstaller.STATUS_FAILURE_ABORTED -> ui = ui.copy(phase = UpdPhase.ERROR, message = "The install was cancelled.")
      else -> ui = ui.copy(phase = UpdPhase.ERROR, message = "Install failed: ${msg ?: "status $status"}")
    }
    return null
  }
}
