package com.peyton.toddlergame

import kotlin.math.max
import kotlin.math.min

/** Where background music can be switched on/off by a parent. Bedtime's lullaby isn't here: it's part of that mode. */
enum class MusicSlot(val label: String) { MENU("Menu"), BALLOONS("Balloons"), BUBBLES("Bubbles"), WASH("Wash") }

/** A background track: resource names in preference order (first one packaged wins), loudness, and how far it ducks under SFX. */
internal data class Track(val names: List<String>, val level: Float, val duckDepth: Float)

/** What to play right now. */
internal data class MusicPick(val name: String, val level: Float, val duckDepth: Float)

/** Which track plays where. Pure (no Android types) so every mode and setting is unit-tested. Resources are looked up by name
 *  through [has], so a missing file means "try the next name, then silence", never a crash. */
internal object MusicDirector {
  val TABLE: Map<MusicSlot, Track> = mapOf(
    MusicSlot.MENU to Track(listOf("music_menu"), 0.28f, 0.35f),
    // Busy play modes duck only lightly (to 80%) so the music keeps going under a storm of pops.
    MusicSlot.BALLOONS to Track(listOf("music_balloons"), 0.45f, 0.8f),
    MusicSlot.BUBBLES to Track(listOf("music_bubbles", "music_balloons"), 0.42f, 0.8f),
    // Wash reuses Bubbles' Beachfront Celebration (falls back to the Balloons track), a little quieter under the scrubbing.
    MusicSlot.WASH to Track(listOf("music_bubbles", "music_balloons"), 0.38f, 0.75f),
  )
  val LULLABY = Track(listOf("music_lullaby"), 0.55f, 0.35f)
  private val V10_SLOTS = setOf("MENU", "BALLOONS", "BUBBLES")

  fun slotFor(screen: Mode?): MusicSlot? = when (screen) {
    null -> MusicSlot.MENU
    Mode.BALLOONS -> MusicSlot.BALLOONS
    Mode.BUBBLES -> MusicSlot.BUBBLES
    Mode.WASH -> MusicSlot.WASH
    else -> null
  }

  /** [on] = slots the parent left switched on; [musicVolume] 0..5 (0 silences everything, Bedtime included). */
  fun trackFor(screen: Mode?, on: Set<MusicSlot>, musicVolume: Int, has: (String) -> Boolean): MusicPick? {
    if (musicVolume <= 0) return null
    val t = if (screen == Mode.BEDTIME) LULLABY else slotFor(screen)?.takeIf { it in on }?.let { TABLE[it] } ?: return null
    val name = t.names.firstOrNull { runCatching { has(it) }.getOrDefault(false) } ?: return null
    return MusicPick(name, t.level, t.duckDepth)
  }

  /** Per-slot switches. Before v10 there were two switches: `mmus` (menu) and `bmus` (balloons); a parent who had turned one off
   *  keeps it off. [known] = the slots that existed when [stored] was saved (null = saved by v10/v11, which had Menu, Balloons and
   *  Bubbles): a slot added since then starts on, so a new mode's music isn't silently off after an update. Unknown names are ignored. */
  fun migrate(stored: Set<String>?, oldMenu: Boolean?, oldBalloons: Boolean?, known: Set<String>? = null): Set<MusicSlot> {
    if (stored != null) {
      val seen = known ?: V10_SLOTS
      return (stored.mapNotNull { n -> MusicSlot.entries.firstOrNull { it.name == n } } + MusicSlot.entries.filter { it.name !in seen }).toSet()
    }
    val s = MusicSlot.entries.toMutableSet()
    if (oldMenu == false) s -= MusicSlot.MENU
    if (oldBalloons == false) s -= MusicSlot.BALLOONS
    return s
  }
}

/** Smooth duck envelope: fall to [depth] over [attackMs], hold while sounds keep coming, then rise back to 1 over [releaseMs].
 *  The old one jumped straight to the depth and back, which made the music "pump" under fast tapping. Pure, so it's unit-tested;
 *  BgMusic drives it from a main-thread ticker. */
internal class Ducker(val attackMs: Float = 60f, val releaseMs: Float = 500f, val holdMs: Long = 600) {
  var depth = 0.35f
    set(v) { field = v.coerceIn(0f, 1f) }
  var k = 1f; private set
  private var holdUntil = Long.MIN_VALUE

  fun duck(now: Long) { holdUntil = now + holdMs }

  /** Advances by [dtMs] at time [now]. Returns true while it still needs ticking (held or not yet back at 1). */
  fun step(now: Long, dtMs: Float): Boolean {
    val dt = max(0f, dtMs)
    val target = if (now < holdUntil) depth else 1f
    val span = max(1f - depth, 0.05f)
    k = if (target < k) max(target, k - span * dt / attackMs) else min(target, k + span * dt / releaseMs)
    k = k.coerceIn(0f, 1f)
    return now < holdUntil || k < 1f
  }
  fun reset() { k = 1f; holdUntil = Long.MIN_VALUE }
}
