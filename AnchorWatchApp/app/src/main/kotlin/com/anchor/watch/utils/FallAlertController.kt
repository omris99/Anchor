package com.anchor.watch.utils

import android.util.Log
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
        if (job?.isActive == true) {
            Log.d(TAG, "start: already running — skipping restart")
            return
        }
        Log.d(TAG, "start: grace=${graceMs}ms")
        job = scope.launch {
            var remaining = graceMs
            while (remaining > 0) {
                _state.value = State.Counting(remaining)
                delay(tickIntervalMs)
                remaining -= tickIntervalMs
            }
            Log.i(TAG, "countdown elapsed — triggering SOS")
            _state.value = State.Triggered
            runCatching { onTrigger() }.onFailure { e ->
                Log.e(TAG, "onTrigger threw an exception", e)
            }
        }
    }

    fun cancel() {
        if (_state.value !is State.Counting) {
            Log.w(TAG, "cancel() called but state is ${_state.value} — ignoring")
            return
        }
        Log.i(TAG, "cancel: user dismissed alert as false positive")
        job?.cancel()
        job = null
        _state.value = State.Cancelled
        runCatching { onCancel() }.onFailure { e ->
            Log.e(TAG, "onCancel threw an exception", e)
        }
    }

    companion object {
        private const val TAG = "FallAlertController"
    }
}
