# Tap Zoo sound credits
## Animal sounds (animal_*.ogg)
These files are NOT in this repo. They live in the private repo PeytonRingling/tap-zoo-sounds, checked out as the `sounds/`
submodule (`sounds/res/raw/`). Builds without it use the synthesized animal voices instead (Sound.kt).
Generated with ElevenLabs (elevenlabs.io) on the owner's account. No API key is stored in this repo. Credit: sounds made with elevenlabs.io.
- Animal noise: ElevenLabs Sound Effects API (/v1/sound-generation). 3 variants per animal; the best was picked by trimmed duration and loudness.
- Spoken name ("Cow!", "Doggy!"...): ElevenLabs Text to Speech, premade voice "Jessica", model eleven_multilingual_v2.
The noise and name were joined, trimmed to under 1.6 s, peak-normalized to -1 dBFS, and saved as Ogg Vorbis.
License note: ElevenLabs output from a free plan is non-commercial only and must be credited to "elevenlabs.io" when shared; paid plans allow
commercial use. ElevenLabs' Sound Effects terms also say generated sound effects may be distributed as part of an app/production, not
as standalone audio files or a sound library. That's why they're kept in a private repo and only ship inside the APK; do not reuse them separately.
## Music
### music_balloons.ogg (Balloons, Splash pond) and music_bubbles.ogg (Bubbles, Wash): Kevin MacLeod, CC BY 4.0
In this public repo (CC BY 4.0 allows redistribution with credit). The same credit appears in the app: Parent settings › Credits.
```
"Monkeys Spinning Monkeys" Kevin MacLeod (incompetech.com)
Licensed under Creative Commons: By Attribution 4.0 License
http://creativecommons.org/licenses/by/4.0/
Edited: trimmed/looped and re-encoded for Tap Zoo.
```
```
"Beachfront Celebration" Kevin MacLeod (incompetech.com)
Licensed under Creative Commons: By Attribution 4.0 License
http://creativecommons.org/licenses/by/4.0/
Edited: trimmed/looped and re-encoded for Tap Zoo.
```
Source: `https://incompetech.com/music/royalty-free/mp3-royaltyfree/<title>.mp3` (ISRC USUAN1400011 and USUAN1200022).
Edits (tools/prepare_audio.py): Monkeys Spinning Monkeys is the whole track cut to exactly 75 bars from its first note. Beachfront
Celebration is 28 bars (56.0 s from 43.88 s) with a 20 ms crossfade at the loop seam. Both are loudness-matched to about -16 LUFS and
re-encoded as Ogg Vorbis.
### music_menu.ogg, music_lullaby.ogg
Original compositions synthesized in code for this app (tools/music.py). No third-party material.

## Balloon pop (pop_balloon.wav): CC0
A layer of two CC0 (public domain) recordings from freesound.org, credited anyway:
- "Pop 01" (cartoon balloon pop) by LilMati, https://freesound.org/people/LilMati/sounds/506546/ (CC0 1.0)
- "Balloon Pop" by Breviceps, https://freesound.org/people/Breviceps/sounds/458398/ (CC0 1.0), the first 120 ms at -8 dB for the snap
Made from Freesound's public previews. It's one 119 ms hit, 16-bit mono 44.1 kHz, peak -1 dBFS (tools/prepare_audio.py). The app plays
it at 7 pitch steps (±8%, bigger balloon = lower). If the file is missing, the synthesized pop in Sound.kt is used.

## Other effects
Synthesized in code at runtime (Sound.kt), including the bubble pops.
