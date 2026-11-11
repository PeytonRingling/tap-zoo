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
## Animal recordings in this repo (app/src/main/res/raw/animal_{horse,sheep,rooster,owl,donkey}.ogg)
Real recordings under free licences (public domain, CC0 or CC BY 4.0), so they can live in this public repo. tools/prepare_animals.py
downloads each from Wikimedia Commons and makes one short cut: mono 44.1 kHz, 80 Hz high-pass, short fades, then the loud part is
loudness-matched to the private clips (about -19 dBFS RMS, peak at most -1 dBFS), saved as Ogg Vorbis q4. No spoken name is added.
- animal_horse.ogg (whinny, 2.05 s from 0.66 s): S2 Sound from Lemasson A, Remeuf K, Trabalon M, Cuir F, Hausberger M (2015),
  "Mares Prefer the Voices of Highly Fertile Stallions", PLOS ONE 10(2): e0118468. **CC BY 4.0**
  (https://creativecommons.org/licenses/by/4.0/). Edited: trimmed, filtered, loudness-matched.
  https://commons.wikimedia.org/wiki/File:Mares-Prefer-the-Voices-of-Highly-Fertile-Stallions-pone.0118468.s002.oga
- animal_sheep.ogg (baa, 1.12 s from 0.58 s): "Sheep bleating" by earthcalling (PDSounds), **public domain**.
  https://commons.wikimedia.org/wiki/File:Sheep_bleating.ogg
- animal_rooster.ogg (cock-a-doodle-doo, 2.25 s from 2.86 s): "Medium rooster crowing" by alys (PDSounds), **public domain**.
  https://commons.wikimedia.org/wiki/File:Medium_rooster_crowing.ogg
- animal_owl.ogg (two hoots, 0.95 s from 1.80 s): "Barred Owl, Yellowstone National Park", US National Park Service (a work of the
  US federal government), **public domain**. https://commons.wikimedia.org/wiki/File:Barred_Owl,_Yellowstone_National_Park.oga
- animal_donkey.ogg (hee-haw, 2.10 s from 6.30 s): "A donkey is braying in his enclosure in south of France" by felix.blume
  (freesound.org/s/157763), **CC0 1.0**. https://commons.wikimedia.org/wiki/File:157763_felix-blume_a-donkey-is-braying-in-his-enclosure-in-south-of-france.wav

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
Synthesized in code at runtime (Sound.kt), including the bubble pops and the pig's oink (the generated pig clip isn't used).
