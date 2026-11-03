package com.peyton.toddlergame

/** Records what a game model asked to play, instead of making noise. */
class FakeSink : SoundSink {
  data class Play(val data: FloatArray, val gain: Float)
  val plays = ArrayList<Play>(); val animals = ArrayList<Pair<Critter, Long>>(); val haptics = ArrayList<Boolean>()
  override fun play(data: FloatArray, gain: Float) { plays += Play(data, gain) }
  override fun animal(k: Critter, gain: Float, delayMs: Long) { animals += k to delayMs }
  override fun haptic(strong: Boolean) { haptics += strong }
  fun clear() { plays.clear(); animals.clear(); haptics.clear() }
}
