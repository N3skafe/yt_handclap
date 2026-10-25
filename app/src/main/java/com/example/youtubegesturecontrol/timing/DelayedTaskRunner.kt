package com.example.youtubegesturecontrol.timing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 제한 시간을 예약합니다. 테스트는 시계를 직접 움직이는 구현을 쓰고,
 * 서비스는 코루틴 delay를 씁니다.
 */
interface DelayedTaskRunner {
    fun schedule(token: String, delayMs: Long, block: () -> Unit)

    fun cancel(token: String)

    fun cancelAll()
}

/**
 * delay는 스레드를 붙잡지 않고 기다립니다. Dart의 Future.delayed와 비슷하지만,
 * 부모 [CoroutineScope]가 취소되면 대기 중인 작업도 함께 취소됩니다.
 */
class CoroutineDelayedTaskRunner(
    private val scope: CoroutineScope,
) : DelayedTaskRunner {
    private val jobs = mutableMapOf<String, Job>()

    override fun schedule(token: String, delayMs: Long, block: () -> Unit) {
        cancel(token)
        jobs[token] = scope.launch {
            delay(delayMs)
            jobs.remove(token)
            block()
        }
    }

    override fun cancel(token: String) {
        jobs.remove(token)?.cancel()
    }

    override fun cancelAll() {
        val running = jobs.values.toList()
        jobs.clear()
        running.forEach { job -> job.cancel() }
    }
}
