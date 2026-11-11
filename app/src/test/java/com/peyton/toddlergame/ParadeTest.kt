package com.peyton.toddlergame

import androidx.compose.ui.geometry.Offset
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** The animal parade is a reward for lots of real taps, with a cooldown, and nothing else can start it. */
class ParadeTest {
  private var now = 1000.0
  @Before fun setUp() { Parade.gate = ParadeGate(); Parade.clock = { now }; Parade.pending = false }
  @After fun tearDown() { Parade.gate = ParadeGate(); Parade.clock = { System.nanoTime() / 1e9 }; Parade.pending = false }

  @Test fun every45thTapStartsOne() {
    val g = ParadeGate(); var t = 0.0
    assertEquals((1..44).map { false }, (1..44).map { g.tap(t++) })
    assertTrue("45th", g.tap(t++)); assertEquals(0, g.taps)
  }

  @Test fun neverAgainWithinTheCooldownEvenWithNonstopTaps() {
    val g = ParadeGate(); var starts = ArrayList<Double>(); var t = 0.0
    while (t < 600.0) { if (g.tap(t)) starts += t; t += 0.1 }   // 10 taps a second for 10 minutes
    assertEquals("one parade per 2 minutes at most: $starts", 5, starts.size)
    starts.zipWithNext().forEach { (a, b) -> assertTrue("gap ${b - a}", b - a >= g.cooldown - 1e-6) }
  }

  @Test fun aSlowTapperStillEarnsOneAfterTheCooldown() {
    val g = ParadeGate(); var t = 0.0; repeat(45) { g.tap(t); t += 1.0 }
    var second = -1.0; while (second < 0) { if (g.tap(t)) second = t; t += 1.0 }
    assertTrue(second >= 120.0 && second < 170.0)
  }

  @Test fun hapticsAndSoundsNoLongerCountAsTaps() {
    // Wash buzzes on every rub step and Fireworks on every auto-launch and boom; that used to start a parade every few seconds.
    repeat(1000) { Fx.tap(); Fx.tap(true); GameSound.haptic() }
    assertFalse(Parade.pending); assertEquals(0, Parade.gate.taps)
  }

  @Test fun washRubbingIsNotTapping() {
    val s = FakeSink(); val m = WashModel(s, kotlin.random.Random(1), WashAnimal.PIG); m.w = 1000f; m.h = 800f
    val f = FingerTracker(onDown = { id, p -> Parade.onFingerDown(); m.down(id, p) }, onMove = m::move, onUp = m::up)
    f.event(listOf(FingerChange(1, Offset(400f, 400f), true, false, false)))
    var x = 400f
    repeat(2000) { x = if (x > 600f) 400f else x + 40f; m.t += 0.01f; m.step(0.01f); f.event(listOf(FingerChange(1, Offset(x, 400f), true, true, true))) }
    assertTrue("lots of rub haptics: ${s.haptics.size}", s.haptics.size > 500)
    assertEquals("one finger-down = one tap", 1, Parade.gate.taps); assertFalse(Parade.pending)
  }

  @Test fun fingerDownsStartItWhenEarned() {
    val f = FingerTracker(onDown = { _, _ -> Parade.onFingerDown() }, onMove = { _, _ -> }, onUp = {})
    repeat(45) { i -> f.event(listOf(FingerChange(i.toLong(), Offset(1f, 1f), true, false, false))); f.event(listOf(FingerChange(i.toLong(), Offset(1f, 1f), false, true, false))) }
    assertTrue(Parade.pending)
  }
}
