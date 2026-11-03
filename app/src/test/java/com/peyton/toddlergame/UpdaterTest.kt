package com.peyton.toddlergame

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Verification rules + the parent-triggered flow (check → download → verify → PackageInstaller) against a fake GitHub. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdaterTest {
  private val key = Updater.RELEASE_CERT
  private val other = "0".repeat(64)
  private val installed = ApkMeta("com.peyton.toddlergame", 1, "1.0", setOf(key))   // what v8 phones have
  private val rel = GhRelease("v9", "9", 2, "tap-zoo-v9-vc2.apk", "https://example.invalid/x.apk", 1)
  private fun meta(pkg: String = "com.peyton.toddlergame", vc: Long = 2, certs: Set<String> = setOf(key)) = ApkMeta(pkg, vc, vc.toString(), certs)

  @After fun reset() { Updater.inspector = null; Updater.installedProvider = null; GitHubReleases.apiBase = "https://api.github.com"; Updater.ui = UpdUi() }

  @Test fun acceptsValidUpdate() = assertNull(Updater.check(meta(), installed, rel))
  @Test fun rejectsWrongPackage() = assertTrue(Updater.check(meta(pkg = "com.peyton.tvlauncher"), installed, rel)!!.contains("different app"))
  @Test fun rejectsWrongCert() = assertTrue(Updater.check(meta(certs = setOf(other)), installed, rel)!!.contains("different key"))
  @Test fun rejectsExtraSigner() = assertTrue(Updater.check(meta(certs = setOf(key, other)), installed, rel)!!.contains("release key"))
  @Test fun rejectsSameOrLowerVersion() {
    assertTrue(Updater.check(meta(vc = 1), installed, rel.copy(versionCode = 1))!!.contains("already has build 1"))
    assertTrue(Updater.check(meta(vc = 1), installed.copy(versionCode = 2), rel.copy(versionCode = 1))!!.contains("already has build 2"))
  }
  @Test fun rejectsAssetNameMismatch() = assertTrue(Updater.check(meta(vc = 3), installed, rel)!!.contains("release says build 2"))
  @Test fun rejectsUnreadable() = assertTrue(Updater.check(null, installed, rel)!!.contains("isn't a valid"))

  private fun idleUntil(timeoutMs: Long = 20_000, cond: () -> Boolean) {
    val end = System.currentTimeMillis() + timeoutMs
    while (!cond()) { shadowOf(Looper.getMainLooper()).idle(); check(System.currentTimeMillis() < end) { "timeout: ${Updater.ui}" }; Thread.sleep(20) }
  }

  @Test fun flowAgainstFakeGitHub() {
    var served = meta()
    Updater.inspector = { _, _ -> served }
    Updater.installedProvider = { installed }
    FakeGitHub().use { gh ->
      GitHubReleases.apiBase = gh.base
      val a = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
      val pm = shadowOf(a.packageManager); pm.setCanRequestPackageInstalls(false)
      gh.apk = ByteArray(300_000) { 7 }
      gh.json = """{"tag_name":"v9","assets":[{"name":"tap-zoo-v9-vc2.apk","browser_download_url":"${gh.base}/dl/tap-zoo-v9-vc2.apk","size":${gh.apk.size}}]}"""

      // no network
      GitHubReleases.apiBase = "http://127.0.0.1:1"
      Updater.checkNow(a, a.lifecycleScope); idleUntil { Updater.ui.phase == UpdPhase.ERROR }
      assertTrue(Updater.ui.message.contains("No internet"))
      GitHubReleases.apiBase = gh.base

      // wrong cert → rejected after download, nothing handed to PackageInstaller
      served = meta(certs = setOf(other))
      Updater.checkNow(a, a.lifecycleScope); idleUntil { Updater.ui.phase == UpdPhase.AVAILABLE }
      Updater.downloadAndInstall(a, a.lifecycleScope); idleUntil { Updater.ui.phase == UpdPhase.ERROR }
      assertTrue(Updater.ui.message, Updater.ui.message.contains("different key"))
      assertTrue(a.packageManager.packageInstaller.allSessions.isEmpty())

      // wrong package
      served = meta(pkg = "com.example.other")
      Updater.checkNow(a, a.lifecycleScope); idleUntil { Updater.ui.phase == UpdPhase.AVAILABLE }
      Updater.downloadAndInstall(a, a.lifecycleScope); idleUntil { Updater.ui.phase == UpdPhase.ERROR }
      assertTrue(Updater.ui.message.contains("different app"))

      // valid → asks for "install unknown apps", then a PackageInstaller session once allowed
      served = meta()
      Updater.checkNow(a, a.lifecycleScope); idleUntil { Updater.ui.phase == UpdPhase.AVAILABLE }
      Updater.downloadAndInstall(a, a.lifecycleScope); idleUntil { Updater.ui.phase == UpdPhase.NEEDS_PERMISSION }
      assertEquals("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Updater.permissionIntent(a).action)
      pm.setCanRequestPackageInstalls(true)
      Updater.onResume(a, a.lifecycleScope)
      idleUntil { a.packageManager.packageInstaller.allSessions.isNotEmpty() }
      assertEquals("com.peyton.toddlergame", a.packageManager.packageInstaller.allSessions.last().appPackageName)

      // already up to date
      Updater.ui = UpdUi(); Updater.installedProvider = { installed.copy(versionCode = 2) }
      Updater.checkNow(a, a.lifecycleScope); idleUntil { Updater.ui.phase == UpdPhase.UP_TO_DATE }
    }
  }
}
