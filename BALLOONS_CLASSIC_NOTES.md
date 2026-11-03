# Research notes: "Baby Balloons pop" (Edujoy/AppQuiz), Classic mode
Sources: the Google Play listing (com.appquiz.baby.ballons), the App Store listing (id876037874, now called "Balloon Pop Games for Toddler"), user reviews quoted on allbestapps.net, and a 2016 Peachy & Clementine hands-on review. No gameplay video transcript of Classic specifically could be found, so the music and pop details below are partly inferred (marked *).

- **Start:** an empty, bright blue sky with no menus or prompts while playing.
- **Spawning:** touching the sky makes a balloon appear right where the finger is ("Mommy touches the sky, and a balloon appears"). More taps make more balloons, "all in different colors". Soap bubbles sometimes appear too.
- **Movement:** in the classic/original version, balloons "would pop up and stay on screen until you pop them". A reviewer complained when a later update made them zip from bottom to top. So movement is a gentle float, not a fast stream. Nothing to win or lose.
- **Look:** glossy, colorful cartoon balloons on strings. Optional letter/number balloons (with spoken names) belong to the other category modes.
- **Popping:** tapping or grabbing a balloon pops it with a "fun animation" and it disappears. *A burst with confetti/sparkle and a pop sound, plus a cheerful note.
- **Sounds:** described as "soft and relaxing sounds". Older version notes call it a "musical game for children". *Background music is a light, upbeat, cheerful kids' loop playing continuously during play.
- **Characters:** a bear guide appears in the category modes (not Classic).
- **Annoyances to avoid:** ads, home buttons, no lock.

## Tap Zoo match (original art and audio, nothing copied)
- Bright blue sky, sun, drifting clouds. The old hills were removed for the "empty sky" feel.
- Starts with 3 balloons. Tapping empty sky inflates a new balloon under the finger (springy inflate and a rising "fwip"). Each new balloon is a different color from the last.
- About 1 in 3 taps also releases 1–2 soap bubbles, which pop with a soft plip.
- Balloons hover, bob, sway and drift slowly, staying until popped. They bounce off the edges and gently push apart.
- Up to 8–16 balloons fit, depending on screen size. Past that, the oldest balloon floats away.
- If the sky is ever empty for 2.5 s, one balloon rises from the bottom and settles in the middle.
- Tap or drag-swipe pops: a rubber-scrap burst, confetti, stars, a pop sound, a random cheerful xylophone note and a haptic tick.
- Kept from before: shapes/letters on balloons (optional), family photos every 4th pop (optional), an animal on a balloon every 6th pop, the hint hand after 5 s idle, kid lock, and the animal parade.
- Music (v8, since replaced by "Monkeys Spinning Monkeys", see SOUND_CREDITS.md): an original track (tools/balloon_music.py, removed), at 116 BPM in F major with a light swing feel. It has marimba melody, glockenspiel accents, a plucked-ukulele off-beat strum, bouncy bass, shaker and soft claps.
  - The track is 16 bars, about 33 s. Note tails wrap around to the start so the loop is seamless.
  - It plays the whole time in Balloons and dips only lightly (to 70%) under pops. It follows the volume setting and has the parent toggle "Balloon game music" (default on).

## Overhaul (PR D, 2026-11)
Default is now a gentle stream that keeps 6-12 balloons on screen (Parent settings › "Balloons keep coming"; off = the classic mode
above). New: heart, foil-star and animal-face balloons, a squash-then-burst with a comic starburst, see-through animal balloons whose
animal parachutes down with its sound, a golden star balloon, a giant rainbow balloon every 25-40 s that bursts into 7 minis, pops in a
quick run climbing the G-major pentatonic scale (the key of the Balloons music) with a ta-da at 5 in a row, and an idle wiggle + a
two-balloon auto-show at 20 s that stops after 90 s without a touch. Still no score and nothing to lose.
