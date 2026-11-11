package com.peyton.toddlergame

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper

/** Animal voice clips played through SoundPool: res/raw/animal_*.ogg from the private `sounds/` submodule (the first 8 animals) and
 *  public-domain/CC recordings in this repo (horse, sheep, rooster, owl, donkey), see SOUND_CREDITS.md.
 *  Clips are looked up by name, so a build without the submodule has none; then (or before a clip loads) the
 *  synthesized voice plays instead. */
object AnimalSounds {
  private var pool: SoundPool? = null
  private val ids = HashMap<Critter, Int>(); private val ready = HashSet<Int>()
  private val main by lazy { Handler(Looper.getMainLooper()) }
  private var lastPlay = 0L
  /** The pig has no clip on purpose: the generated `animal_pig` clip is a run of high (~500 Hz) tonal squeals that don't read as
   *  a pig, so it always uses the synthesized oink-oink ([Sfx.oinkOink]) instead. */
  internal val SYNTH_ONLY = setOf(Critter.PIG)
  private val names = mapOf(Critter.DOG to "animal_dog", Critter.CAT to "animal_cat", Critter.COW to "animal_cow",
    Critter.FROG to "animal_frog", Critter.LION to "animal_lion", Critter.CHICK to "animal_chick", Critter.DUCK to "animal_duck",
    // public recordings in app/src/main/res/raw (tools/prepare_animals.py), always in the build
    Critter.HORSE to "animal_horse", Critter.SHEEP to "animal_sheep", Critter.ROOSTER to "animal_rooster", Critter.OWL to "animal_owl", Critter.DONKEY to "animal_donkey")

  /** Raw resource id for [name], or 0 if this build doesn't include it (public clone without the sounds submodule). */
  @android.annotation.SuppressLint("DiscouragedApi")
  private fun rawId(c: Context, name: String): Int = c.resources.getIdentifier(name, "raw", c.packageName)

  /** How many animal clips this build uses: 12 with the sounds submodule (all but [SYNTH_ONLY]), 5 (the public ones) without. */
  internal fun available(c: Context): Int = names.values.count { rawId(c, it) != 0 }

  fun init(c: Context) {
    if (pool != null) return
    val p = SoundPool.Builder().setMaxStreams(3) // capped so taps can't pile up
      .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    p.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(ready) { ready += id } }
    names.forEach { (k, n) -> val r = rawId(c, n); if (r != 0) runCatching { ids[k] = p.load(c, r, 1) } }
    pool = p
  }
  fun release() { pool?.release(); pool = null; ids.clear(); synchronized(ready) { ready.clear() } }

  fun play(k: Critter, gain: Float = 1f, delayMs: Long = 0) {
    if (delayMs > 0) { main.postDelayed({ play(k, gain) }, delayMs); return }
    val vol = (Audio.volume * gain).coerceIn(0f, 1f); if (vol <= 0f) return
    val now = System.currentTimeMillis(); if (now - lastPlay < 350) return; lastPlay = now
    val p = pool; val id = ids[k]
    BgMusic.duck()
    if (p != null && id != null && synchronized(ready) { id in ready }) p.play(id, vol, vol, 1, 0, 1f)
    else Audio.play(Sfx.animal.getValue(k), 0.7f * gain)
  }
}
