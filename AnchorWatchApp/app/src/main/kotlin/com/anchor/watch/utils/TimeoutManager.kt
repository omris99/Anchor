package com.anchor.watch.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TimeoutManager(
    val firstDelayMs: Long = DEFAULT_PHASE_MS,
    val secondDelayMs: Long = DEFAULT_PHASE_MS,
    private val tickIntervalMs: Long = 1000L,
) {
    sealed class Phase {
        data object Idle : Phase()
        data class First(val remainingMs: Long, val totalMs: Long) : Phase()
        data class Second(val remainingMs: Long, val totalMs: Long) : Phase()
        data object Expired : Phase()
    }

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private var job: Job? = null

    fun start(
        scope: CoroutineScope,
        onFirstTimeout: () -> Unit = {},
        onFinalTimeout: () -> Unit = {},
    ) {
        stop()
        job = scope.launch {
            countDown(firstDelayMs) { rem ->
                _phase.value = Phase.First(rem, firstDelayMs)
            }
            onFirstTimeout()
            countDown(secondDelayMs) { rem ->
                _phase.value = Phase.Second(rem, secondDelayMs)
            }
            _phase.value = Phase.Expired
            onFinalTimeout()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _phase.value = Phase.Idle
    }

    private suspend fun countDown(totalMs: Long, emit: (Long) -> Unit) {
        var remaining = totalMs
        while (remaining > 0) {
            emit(remaining)
            delay(tickIntervalMs)
            remaining -= tickIntervalMs
        }
    }

    companion object {
        const val DEFAULT_PHASE_MS = 3 * 60 * 1000L
    }
}
