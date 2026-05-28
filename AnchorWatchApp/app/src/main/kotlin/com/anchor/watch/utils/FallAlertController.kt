package com.anchor.watch.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FallAlertController(
    private val graceMs: Long = FallDetectionConstants.GRACE_PERIOD_MS,
    private val tickIntervalMs: Long = 1000L,
    private val onTrigger: () -> Unit,
    private val onCancel: () -> Unit = {},
) {
    sealed class State {
        data class Counting(val remainingMs: Long) : State()
        data object Cancelled : State()
        data object Triggered : State()
    }

    private val _state = MutableStateFlow<State>(State.Counting(graceMs))
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            var remaining = graceMs
            while (remaining > 0) {
                _state.value = State.Counting(remaining)
                delay(tickIntervalMs)
                remaining -= tickIntervalMs
            }
            _state.value = State.Triggered
            onTrigger()
        }
    }

    fun cancel() {
        if (_state.value !is State.Counting) return
        job?.cancel()
        job = null
        _state.value = State.Cancelled
        onCancel()
    }
}
