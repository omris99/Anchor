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

@Composable
fun MedicationReminderScreen(
    store: MedicationStore,
    medicationId: String,
    onConfirm: () -> Unit,
    onFinished: () -> Unit,
) {
    var medication by remember { mutableStateOf<MedicationEntity?>(null) }
    val phase by MedicationAlarmService.livePhase.collectAsState()

    LaunchedEffect(medicationId) {
        medication = store.byId(medicationId)
        if (medication == null) onFinished()
    }

    LaunchedEffect(phase) {
        if (phase == TimeoutManager.Phase.Expired) onFinished()
    }

    val remainingMs = when (val p = phase) {
        is TimeoutManager.Phase.First -> p.remainingMs
        is TimeoutManager.Phase.Second -> p.remainingMs
        else -> 0L
    }
    val minutes = (remainingMs / 60_000L).toInt()
    val seconds = ((remainingMs % 60_000L) / 1000L).toInt()
    val takenCd = stringResource(R.string.cd_medication_taken)

    Scaffold(timeText = {}) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.primary)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            ) {
                Text(
                    text = medication?.name ?: stringResource(R.string.medication_loading),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.text_primary),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.time_left_template, minutes, seconds),
                    fontSize = 18.sp,
                    color = colorResource(R.color.text_primary),
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorResource(R.color.confirm),
                        contentColor = colorResource(R.color.text_primary),
                    ),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 96.dp, minHeight = 52.dp)
                        .semantics { contentDescription = takenCd },
                ) {
                    Text(
                        text = stringResource(R.string.medication_taken),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(R.color.text_primary),
                    )
                }
            }
        }
    }
}
