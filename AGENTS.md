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
./gradlew test             # GameTest, MixerTest, AudioOutputTest, GameModelsTest, MusicDirectorTest, BgMusicTest, SamplesTest, BalloonsTest (logic) + ScreensTest (Robolectric renders -> screens/, git-ignored)
./gradlew assembleDebug    # the shipped APK -> app/build/outputs/apk/debug/app-debug.apk
```
Needs JDK 17+ and an Android SDK with platform 34 (`local.properties` `sdk.dir=…` or `ANDROID_HOME`).

## Hard rules
* **Game models make sound only through `SoundSink`** (`GameSound` in the app, `FakeSink` in tests) and take an injectable
  `Random`, so their behaviour is unit-tested in GameModelsTest. Don't call `Audio.play`/`AnimalSounds.play`/`Fx.tap` from model code.
* Background music: which track plays where is the `MusicDirector` table (pure, MusicDirectorTest); tracks load by name and a
  missing one is silence. Music volume (`mvol`) and per-slot switches (`musslots`) are separate from the SFX volume (`vol`).
  `musknown` records which slots existed when `musslots` was saved; a slot added later starts ON (`MusicDirector.migrate`), so a
  new mode's music is never off by accident after an update. When adding a slot, keep `V10_SLOTS` as is.
* Downloaded audio in `app/src/main/res/raw` (Kevin MacLeod music, CC BY 4.0; CC0 balloon pop) is rebuilt by tools/prepare_audio.py and
  must stay credited in SOUND_CREDITS.md **and** in the app (`CREDITS` in Menu.kt, Parent settings › Credits). Recorded one-shots
  (`Samples`) are 16-bit WAV loaded by name with a synthesized fallback.
* Balloons is tuned for an 18-month-old: plain round balloons only, 5-7 on screen (hard cap 10), an animal balloon about 1 in 15,
  a soft combo note, an idle wiggle at 5 s that never pops anything, quiet after 90 s. `BalloonModel`
  constants are pinned in BalloonsTest; the classic "only what you make" behaviour (`Prefs.balloonStream` off) stays pinned in GameModelsTest.
* Modes: `Mode` in Common.kt; `Mode.TILES` are the menu tiles. Bedtime has `tile = false` and is started from Parent settings ›
  Start bedtime. Saved tile sets go through `enabledFrom`, which drops names from older versions (Garden, Music pads, Night sky were
  removed in round 2) and never returns an empty menu (ModeListTest).
* Fireworks (`FireworksModel`, FireworksTest): any touch launches a rocket under the finger that bursts where it is; holding fires
  every 0.3 s. Caps: 6 rockets (the oldest bursts early), 900 sparks (the oldest drop), whoosh/boom throttled. No music.
  Each shape has its own sound (ring = `bigBoom`, heart = `twinkle`, star = loud crackle); quick taps on one spot build up to 3
  extra spark layers; every 6th rocket is a smiley or cat face; 12 silent fireflies drift and glow brighter between taps.
* Feed the monster (`FeedModel`, FeedTest): tap a food, or anywhere (feeds the nearest), and it flies into the mouth; drag and let
  go anywhere hops it in. Burp every 5th bite. Foods always respawn. No music.
  Foods: apple, banana, lemon (pucker), carrot (crunch), cupcake (wiggle). Food within 1.6 monster radii: mouth extra wide + lean.
  Each bite turns the tummy the food's colour and shifts the body hue 20% (`hueToward`, never grey); bite 10 = round belly + 3 s dance.
* Wash the animal (`WashModel`, WashTest): rub or tap the mud (3 rubs a splat, twice as hard after 10 s); every touch foams and
  squeaks. At 20% mud left: shower, animal sound, next friend walks in. Music: the `WASH` slot (Beachfront Celebration).
* Menu layout (`MenuModel.rects`, MenuLayoutTest): 11 tiles must fit phone, phone landscape and both Fold screens, ≥ 90 dp each and
  clear of the parent gear corner.
* Audio: one output thread at a time (generation counter in `Audio`); the `Mixer` fades a stolen voice over 5 ms instead of cutting it.
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
