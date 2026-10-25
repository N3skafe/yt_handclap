package com.example.youtubegesturecontrol.timing

import com.example.youtubegesturecontrol.ControlTimeouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualScheduler(
    private var nowMs: Long = 0L,
) : DelayedTaskRunner {
    private class Task(
        val token: String,
        val dueAtMs: Long,
        val block: () -> Unit,
    )

    private val tasks = mutableListOf<Task>()

    fun advanceTo(targetMs: Long) {
        check(targetMs >= nowMs)
        nowMs = targetMs
        while (true) {
            val due = tasks.filter { task -> task.dueAtMs <= nowMs }.minByOrNull { task -> task.dueAtMs }
                ?: break
            tasks.remove(due)
            due.block()
        }
    }

    override fun schedule(token: String, delayMs: Long, block: () -> Unit) {
        cancel(token)
        tasks.add(Task(token = token, dueAtMs = nowMs + delayMs, block = block))
    }

    override fun cancel(token: String) {
        tasks.removeAll { task -> task.token == token }
    }

    override fun cancelAll() {
        tasks.clear()
    }
}

class ManualSchedulerTest {
    @Test
    fun recognitionTimeoutDoesNotFireEarlyAndFiresOnce() {
        val scheduler = ManualScheduler()
        var runs = 0
        scheduler.schedule(token = "recognition-1", delayMs = ControlTimeouts.RECOGNITION_TIMEOUT_MS) {
            runs += 1
        }
        scheduler.advanceTo(ControlTimeouts.RECOGNITION_TIMEOUT_MS - 1L)
        assertEquals(0, runs)
        scheduler.advanceTo(ControlTimeouts.RECOGNITION_TIMEOUT_MS)
        assertEquals(1, runs)
        scheduler.advanceTo(ControlTimeouts.RECOGNITION_TIMEOUT_MS + 5_000L)
        assertEquals(1, runs)
    }

    @Test
    fun cancelPreventsCameraStartTimeout() {
        val scheduler = ManualScheduler()
        var fired = false
        scheduler.schedule(token = "camera-7", delayMs = ControlTimeouts.CAMERA_START_TIMEOUT_MS) {
            fired = true
        }
        scheduler.advanceTo(1_000L)
        scheduler.cancel("camera-7")
        scheduler.advanceTo(ControlTimeouts.CAMERA_START_TIMEOUT_MS)
        assertTrue(!fired)
    }

    @Test
    fun rescheduleReplacesThePreviousDeadline() {
        val scheduler = ManualScheduler()
        var value = ""
        scheduler.schedule("recognition-2", 1_000L) { value = "old" }
        scheduler.schedule("recognition-2", 2_000L) { value = "new" }
        scheduler.advanceTo(1_000L)
        assertEquals("", value)
        scheduler.advanceTo(2_000L)
        assertEquals("new", value)
    }
}
