# AGENTS.md: Tap Zoo (com.peyton.toddlergame)

Kotlin + Jetpack Compose toddler game (ages ~1–3). Targets a Pixel Fold (inner and outer screens). Current
release label: **v8** (classic Balloons mode, see BALLOONS_CLASSIC_NOTES.md; audio credits in SOUND_CREDITS.md).

## Checkout: the sounds submodule
Animal voice clips (ElevenLabs, `animal_*.ogg`) live in the **private** repo `PeytonRingling/tap-zoo-sounds`, mounted as a git
submodule at `sounds/` (`sounds/res/raw/`), which Gradle adds as an extra res dir. Clone with
`git clone --recurse-submodules https://github.com/PeytonRingling/tap-zoo.git` (or run `git submodule update --init` after a plain clone).
Without it (public clones, no access) the app still builds and plays the synthesized animal voices. Shipped APKs must be built
WITH the submodule: check `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -c animal_` prints 8.
Never copy the `.ogg` files into this repo or make tap-zoo-sounds public (ElevenLabs terms: no standalone distribution).
Load sounds by name (`getIdentifier`), never `R.raw.animal_*`, so the build works without them.

## Build / test
```bash
./gradlew test             # GameTest (logic) + ScreensTest (Robolectric renders -> screens/, git-ignored)
./gradlew assembleDebug    # the shipped APK -> app/build/outputs/apk/debug/app-debug.apk
```
Needs JDK 17+ and an Android SDK with platform 34 (`local.properties` `sdk.dir=…` or `ANDROID_HOME`).

## Hard rules
* **Never run the Android emulator.** It crashes the build box. Verify UI with the Robolectric renders.
* **Signing:** must stay `~/.android/debug.keystore`, cert SHA-256 `65f0d4cb5041d1d60f2fbbb44d0bc73174de9ea1668be0038129630a6a4d113f`.
  On a new machine, restore that keystore before the first build (see RELEASING.md). Never create or commit a key.
* Versions: v1–v8 were all versionCode 1; v9 = versionCode 2 / versionName "9" (gradle defaults). Bump with `-Pvc/-Pvn`, only upward.
* Kid-safe: no ads, no links out, nothing that leaves the app without the parent lock. The only network use is Parent settings ›
  App updates (GitHub Releases), started by a parent tap. Never add automatic checks or any update UI outside that panel.
* Never commit APKs, keystores or `local.properties`.
* The repo is public: never commit personal data (family names/photos, the child's age, location, device serials, Drive links).

## Delivery
* Preferred: GitHub Releases via `tools/release.sh <vc> <vn> [--publish]`: tag `v<vn>`, asset `tap-zoo-v<vn>-vc<vc>.apk` (the app
  parses this name). The phone installs it from Parent settings › App updates. See RELEASING.md.
* Fallback: upload the APK to Peyton's Google Drive. Publishing, uploading or sending anything needs his approval.
* Updater code: `GitHubReleases.kt` (same file as the TV Launcher's), `Updater.kt`, `UpdatesRow` in Menu.kt.
  Tests: `GitHubReleasesTest`, `UpdaterTest`, `ScreensTest.upd*`.
