package com.anchor.watch.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.anchor.watch.services.EmergencyService
import com.anchor.watch.services.EmergencyState
import kotlinx.coroutines.delay

@Composable
fun SosScreen(
    graceSeconds: Int = EmergencyService.DEFAULT_GRACE_SECONDS,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state by EmergencyService.liveState.collectAsState()

    // Start a fresh countdown on entry. Guard only against a genuinely in-flight
    // run (countdown/dispatch) so a stale terminal Sent state can't wedge the flow
    // and block a second SOS press.
    LaunchedEffect(Unit) {
        val current = EmergencyService.liveState.value
        val inFlight = current is EmergencyState.CountingDown ||
            current is EmergencyState.Dispatching
        if (!inFlight) {
            EmergencyService.start(context, graceSeconds)
        }
    }

    // Reset the process-static state when leaving the screen so the next open
    // always starts from Idle (fixes single-use SOS countdown).
    DisposableEffect(Unit) {
        onDispose {
            if (EmergencyService.liveState.value !is EmergencyState.CountingDown) {
                EmergencyService.liveState.value = EmergencyState.Idle
            }
        }
    }

    LaunchedEffect(state) {
        if (state is EmergencyState.Sent) {
            delay(2500L)
            onDismiss()
        }
    }

    val cancelCd = stringResource(R.string.cd_sos_cancel)

    Scaffold(timeText = {}) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(R.color.sos)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Text(
                    text = stringResource(R.string.sos_title),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.text_on_colored),
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = stringResource(R.string.sos_subtitle),
                    fontSize = 13.sp,
                    color = colorResource(R.color.text_on_colored),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(6.dp))

                when (val s = state) {
                    is EmergencyState.CountingDown -> {
                        Text(
                            text = "${s.secondsLeft}",
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorResource(R.color.text_on_colored),
                        )
                        Text(
                            text = stringResource(R.string.sos_seconds_label),
                            fontSize = 13.sp,
                            color = colorResource(R.color.text_on_colored),
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                EmergencyService.cancel(context)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = colorResource(R.color.surface),
                                contentColor = colorResource(R.color.text_primary),
                            ),
                            modifier = Modifier
                                .height(52.dp)
                                .width(140.dp)
                                .semantics { contentDescription = cancelCd },
                        ) {
                            Text(
                                text = "✕  ${stringResource(R.string.sos_cancel)}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorResource(R.color.text_primary),
                            )
                        }
                    }
                    else -> {
                        Spacer(Modifier.height(8.dp))
                        val message = when (s) {
                            EmergencyState.Dispatching -> stringResource(R.string.sos_dispatching)
                            is EmergencyState.Sent ->
                                if (s.online) stringResource(R.string.sos_sent)
                                else stringResource(R.string.sos_queued)
                            else -> stringResource(R.string.sos_initializing)
                        }
                        Text(
                            text = message,
                            fontSize = 18.sp,
                            color = colorResource(R.color.text_on_colored),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
