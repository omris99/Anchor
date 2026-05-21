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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.anchor.watch.utils.FallAlertController
import com.anchor.watch.utils.FallDetectionConstants

@Composable
fun FallAlertScreen(
    controller: FallAlertController = remember {
        FallAlertController(
            graceMs = FallDetectionConstants.GRACE_PERIOD_MS,
            onTrigger = {},
            onCancel = {},
        )
    },
    onFinished: () -> Unit,
) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { controller.start(scope) }

    LaunchedEffect(state) {
        when (state) {
            FallAlertController.State.Cancelled,
            FallAlertController.State.Triggered -> {
                kotlinx.coroutines.delay(800L)
                onFinished()
            }
            else -> Unit
        }
    }

    val cancelCd = stringResource(R.string.cd_fall_cancel)

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
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.fall_detected_title),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.text_primary),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(6.dp))

                val message = when (val s = state) {
                    is FallAlertController.State.Counting -> {
                        val seconds = ((s.remainingMs + 999) / 1000).toInt().coerceAtLeast(0)
                        stringResource(R.string.fall_countdown_template, seconds)
                    }
                    FallAlertController.State.Cancelled ->
                        stringResource(R.string.fall_cancelled)
                    FallAlertController.State.Triggered ->
                        stringResource(R.string.fall_alert_sent)
                }
                Text(
                    text = message,
                    fontSize = 18.sp,
                    color = colorResource(R.color.text_primary),
                    textAlign = TextAlign.Center,
                )

                if (state is FallAlertController.State.Counting) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { controller.cancel() },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorResource(R.color.confirm),
                            contentColor = colorResource(R.color.text_primary),
                        ),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 96.dp, minHeight = 56.dp)
                            .semantics { contentDescription = cancelCd },
                    ) {
                        Text(
                            text = stringResource(R.string.fall_im_ok),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorResource(R.color.text_primary),
                        )
                    }
                }
            }
        }
    }
}
