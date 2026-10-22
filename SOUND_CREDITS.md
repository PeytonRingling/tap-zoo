# Tap Zoo v8 sound credits
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
## Music (music_menu.ogg, music_lullaby.ogg, music_balloons.ogg)
Original compositions synthesized in code for this app (tools/music.py; tools/balloon_music.py for Balloons). No third-party material.
## Other effects
Synthesized in code at runtime (Sound.kt).
