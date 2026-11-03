package com.peyton.toddlergame

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The output thread around [Mixer]: one thread at a time, survives broken outputs, restarts cleanly. No real AudioTrack. */
class AudioOutputTest {
  private val real = Audio.openOutput
  private class FakeOut(val failWrite: Boolean = false) : PcmOut {
    val writes = AtomicInteger(); val closed = CountDownLatch(1); val firstWrite = CountDownLatch(1)
    override fun write(buf: ShortArray, frames: Int) {
      if (failWrite) throw IllegalStateException("device gone")
      writes.incrementAndGet(); firstWrite.countDown(); Thread.sleep(1)
    }
    override fun close() { closed.countDown() }
  }
  @Before fun reset() { Audio.stop(); Audio.thread?.join(2000) }
  @After fun restore() { Audio.stop(); Audio.thread?.join(2000); Audio.openOutput = real }

  @Test fun startStopStartLeavesExactlyOneLiveThread() {
    val a = FakeOut(); val b = FakeOut(); val outs = ArrayDeque(listOf(a, b))
    Audio.openOutput = { outs.removeFirst() }
    Audio.start(); assertTrue(a.firstWrite.await(2, TimeUnit.SECONDS)); val first = Audio.thread!!
    Audio.start()                                         // already running: no second thread
    assertSame(first, Audio.thread)
    Audio.stop(); Audio.start()                           // the fast pause/resume race
    val second = Audio.thread!!
    assertNotSame(first, second)
    first.join(2000); assertFalse("stale thread must exit", first.isAlive)
    assertTrue("stale output closed", a.closed.await(1, TimeUnit.SECONDS))
    assertTrue(b.firstWrite.await(2, TimeUnit.SECONDS)); assertTrue(second.isAlive)
    assertEquals(1, Thread.getAllStackTraces().keys.count { it.name == "toddler-audio" && it.isAlive })
  }

  @Test fun staleGenerationNeverMixes() {
    Audio.openOutput = { FakeOut() }
    Audio.start(); val stale = Audio.generation; Audio.stop(); Audio.start()
    Audio.mixer.add(FloatArray(1000) { 0.5f }, 1f)
    assertFalse(Audio.pump(stale, FloatArray(Audio.FRAMES), ShortArray(Audio.FRAMES)))
  }

  @Test fun outputThatFailsToOpenDoesNotCrashAndCanRetry() {
    Audio.openOutput = { throw UnsupportedOperationException("no audio device") }
    Audio.start(); Audio.thread!!.join(2000)
    assertFalse(Audio.isRunning())
    val ok = FakeOut(); Audio.openOutput = { ok }
    Audio.start(); assertTrue(ok.firstWrite.await(2, TimeUnit.SECONDS)); assertTrue(Audio.isRunning())
  }

  @Test fun outputThatFailsMidStreamStopsCleanly() {
    val bad = FakeOut(failWrite = true); Audio.openOutput = { bad }
    Audio.start(); Audio.thread!!.join(2000)
    assertFalse(Audio.isRunning()); assertTrue(bad.closed.await(1, TimeUnit.SECONDS))
  }

  @Test fun stoppedEngineRefusesToPump() {
    Audio.openOutput = { FakeOut() }; Audio.start(); val g = Audio.generation
    Audio.stop(); Audio.thread?.join(2000)
    assertFalse(Audio.pump(g, FloatArray(4), ShortArray(4)))
  }

  @Test fun pumpConvertsWithinShortRange() {
    val gate = CountDownLatch(1)
    Audio.openOutput = { object : PcmOut { override fun write(buf: ShortArray, frames: Int) { gate.await() }; override fun close() {} } }
    Audio.start(); val g = Audio.generation
    repeat(6) { Audio.mixer.add(FloatArray(4096) { 1f }, 1f) }   // 6x full scale
    val mix = FloatArray(Audio.FRAMES); val buf = ShortArray(Audio.FRAMES)
    val old = Audio.volume; Audio.volume = 1f
    try { assertTrue(Audio.pump(g, mix, buf)); buf.forEach { assertTrue(it in 31000..32000) } } finally { Audio.volume = old; gate.countDown() }
  }
}
