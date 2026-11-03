# Tap Zoo

A calm, ad-free tap game for toddlers (ages ~1–3), built with Kotlin and Jetpack Compose (`com.peyton.toddlergame`).
No network access, no ads, no links out; settings sit behind a parent lock.

## Build
```bash
git clone --recurse-submodules https://github.com/PeytonRingling/tap-zoo.git
cd tap-zoo
./gradlew test assembleDebug   # APK -> app/build/outputs/apk/debug/app-debug.apk
```
Needs JDK 17+ and the Android SDK (platform 34).

The recorded animal voices are in a **private** submodule (`sounds/`) because their licence doesn't allow redistributing
them as standalone files. If you can't fetch it, everything still builds and runs; the animals use synthesized voices instead.
Balloons and Bubbles music is by Kevin MacLeod (CC BY 4.0) and the balloon pop is CC0. The other music and effects are original and synthesized. See SOUND_CREDITS.md.

See AGENTS.md and RELEASING.md for signing and release details.
