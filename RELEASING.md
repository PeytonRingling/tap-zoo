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
`app/build.gradle.kts` says `versionCode = 1, versionName = "1.0"`, and every Tap Zoo APK inspected (built from this tree plus
earlier ones on the box) really is versionCode 1 / "1.0". "v8" is only a release label. The v8 APK itself wasn't in this folder,
so this wasn't changed. Same-versionCode reinstalls work over adb or a file install. To make updates unambiguous, bump on the
next release (e.g. `versionCode = 9, versionName = "9"`). A higher code is always safe.

## Testing: never run the emulator
`./gradlew test` runs logic tests and Robolectric/Roborazzi renders at Pixel Fold inner/outer sizes into `screens/`
(git-ignored, re-recorded every run; override with `-Dscreens.dir`). **Do not start the Android emulator.** It crashes the box.

## Delivery
Upload `Tap Zoo v<N>.apk` to Peyton's Google Drive, and he installs it on his phone from Drive. Downloads from chat don't work on his phone.
Uploading or sending anything needs his OK.
