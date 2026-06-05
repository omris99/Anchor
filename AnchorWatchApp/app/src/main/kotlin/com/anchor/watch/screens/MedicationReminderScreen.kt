package com.anchor.watch.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.anchor.watch.R
import com.anchor.watch.data.local.MedicationEntity
import com.anchor.watch.data.local.MedicationStore
import com.anchor.watch.services.MedicationAlarmService
import com.anchor.watch.utils.TimeoutManager
import kotlinx.coroutines.delay

/** How long the "✓ Taken" acknowledgment shows before the screen closes. */
private const val CONFIRMATION_DISPLAY_MS = 1200L

@Composable
fun MedicationReminderScreen(
    store: MedicationStore,
    medicationId: String,
    onConfirm: () -> Unit,
    onFinished: () -> Unit,
    confirmationDisplayMs: Long = CONFIRMATION_DISPLAY_MS,
) {
    var medication by remember { mutableStateOf<MedicationEntity?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    val phase by MedicationAlarmService.livePhase.collectAsState()

    LaunchedEffect(medicationId) {
        medication = store.byId(medicationId)
        if (medication == null) onFinished()
    }

    // Don't let a timeout yank the screen away once the user has acknowledged.
    LaunchedEffect(phase) {
        if (!confirmed && phase == TimeoutManager.Phase.Expired) onFinished()
    }

    // Show the acknowledgment briefly so the elderly user sees feedback, then close.
    LaunchedEffect(confirmed) {
        if (confirmed) {
            delay(confirmationDisplayMs)
            onFinished()
        }
    }

    val remainingMs = when (val p = phase) {
        is TimeoutManager.Phase.First -> p.remainingMs
        is TimeoutManager.Phase.Second -> p.remainingMs
        else -> 0L
    }
    val minutes = (remainingMs / 60_000L).toInt()
    val seconds = ((remainingMs % 60_000L) / 1000L).toInt()
    val takenCd = stringResource(R.string.cd_medication_taken)
    val confirmedCd = stringResource(R.string.cd_medication_confirmed)

    Scaffold(timeText = {}) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.background)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            ) {
                if (confirmed) {
                    Text(
                        text = stringResource(R.string.medication_confirmed),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(R.color.confirm),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { contentDescription = confirmedCd },
                    )
                    return@Column
                }

                Text(
                    text = "💊",
                    fontSize = 28.sp,
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = stringResource(R.string.medication_subtitle),
                    fontSize = 12.sp,
                    color = colorResource(R.color.text_secondary),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = medication?.name ?: stringResource(R.string.medication_loading),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.text_primary),
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = stringResource(R.string.time_left_template, minutes, seconds),
                    fontSize = 12.sp,
                    color = colorResource(R.color.text_secondary),
                )

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        confirmed = true
                        onConfirm()
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorResource(R.color.confirm),
                        contentColor = colorResource(R.color.text_on_colored),
                    ),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 96.dp, minHeight = 48.dp)
                        .semantics { contentDescription = takenCd },
                ) {
                    Text(
                        text = "✓  ${stringResource(R.string.medication_taken)}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(R.color.text_on_colored),
                    )
                }
            }
        }
    }
}
