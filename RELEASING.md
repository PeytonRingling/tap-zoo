# Releasing Tap Zoo (com.peyton.toddlergame)

## Signing
Tap Zoo ships as the **debug** build, which AGP signs with the standard debug keystore:

| | |
|---|---|
| Keystore | `~/.android/debug.keystore` (alias `androiddebugkey`, passwords `android`) |
| Cert SHA-256 | `65f0d4cb5041d1d60f2fbbb44d0bc73174de9ea1668be0038129630a6a4d113f` (`C=US, O=Android, CN=Android Debug`) |

This is the same key as the TV Launcher. Keep using it, or the phone refuses to install updates over the existing app
(the kid's settings would be lost on reinstall). The keystore is **not** in the repo and must never be committed.
There's no `signingConfigs` block, so on a fresh machine AGP would silently create a *different* debug keystore. Before the
first Gradle build anywhere else, decode the real one from a secret (documented only):
```bash
mkdir -p ~/.android && echo "$TVL_KEYSTORE_B64" | base64 -d > ~/.android/debug.keystore && chmod 600 ~/.android/debug.keystore
```

## Build + verify
Release builds need the private animal sounds: `git submodule update --init` (the `sounds/` submodule,
`PeytonRingling/tap-zoo-sounds`). Without it the APK builds fine but only has synthesized animal voices.
```bash
git submodule update --init    # private sounds -> sounds/res/raw (needs access to PeytonRingling/tap-zoo-sounds)
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep -c 'res/raw/animal_'   # must print 8
BT=$ANDROID_HOME/build-tools/34.0.0
$BT/apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk | grep SHA-256   # 65f0d4cb...4a4d113f
```

## Version numbers
versionName is the release label and versionCode only goes up. Every build up to and including v8 is versionCode 1 / "1.0".
**v9 = versionCode 2 / versionName "9"** (first build with the GitHub updater); those are now the defaults in
`app/build.gradle.kts`. Override with `-Pvc/-Pvn` and update the defaults in the same commit. History: v1–v8 = 1, v9 = 2.

## Publishing to GitHub Releases (how phones get updates)
Parent settings (hold the gear 3 s) › **App updates** › Check for updates calls
`https://api.github.com/repos/PeytonRingling/tap-zoo/releases/latest` (public, no token). Nothing checks on its own.

**Naming convention (the app depends on it):** tag `v<versionName>` (e.g. `v9`) with exactly one APK asset named
`tap-zoo-v<versionName>-vc<versionCode>.apk` (e.g. `tap-zoo-v9-vc2.apk`). The app reads the versionCode from the name, and after
downloading checks package `com.peyton.toddlergame`, signer exactly `65f0d4cb…4a4d113f`, versionCode above the installed one and
equal to the name's, before handing it to PackageInstaller. Android then shows its install prompt (and, the first time, the
"Install unknown apps" switch for Tap Zoo). Kid lock (screen pinning) is turned off for the install; "Back to play" re-pins.
```bash
tools/release.sh 2 9              # submodule + build + verify version/cert/8 sounds + refuse non-increasing versionCode; dry run
tools/release.sh 2 9 --publish    # same, then `gh release create v9 release/tap-zoo-v9-vc2.apk ...` (Peyton's OK first)
```
Release APKs must include the 8 animal sounds (the script fails otherwise). Shipping them inside the app is what the
ElevenLabs terms allow; never attach the raw `.ogg` files to a release.

## Testing: never run the emulator
`./gradlew test` runs logic tests and Robolectric/Roborazzi renders at Pixel Fold inner/outer sizes into `screens/`
(git-ignored, re-recorded every run; override with `-Dscreens.dir`). **Do not start the Android emulator.** It crashes the box.

## Delivery
Preferred: GitHub Releases (above). Fallback: upload `Tap Zoo v<N>.apk` to Peyton's Google Drive, and he installs it on his phone from Drive. Downloads from chat don't work on his phone.
Uploading or sending anything needs his OK.
