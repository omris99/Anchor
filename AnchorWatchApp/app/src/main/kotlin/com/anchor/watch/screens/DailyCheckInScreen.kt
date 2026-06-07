package com.anchor.watch.screens

import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.anchor.watch.R
import com.anchor.watch.data.CheckInContext
import com.anchor.watch.data.CheckInRepository
import com.anchor.watch.data.CheckInStatus
import com.anchor.watch.utils.TimeoutManager
import kotlinx.coroutines.launch

@Composable
fun DailyCheckInScreen(
    repository: CheckInRepository,
    contextProvider: () -> CheckInContext = { CheckInContext() },
    timeoutManager: TimeoutManager = remember { TimeoutManager() },
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val phase by timeoutManager.phase.collectAsState()
    val scope = rememberCoroutineScope()
    var submitted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        timeoutManager.start(
            scope = scope,
            onFirstTimeout = { playAlertSound(context) },
            onFinalTimeout = {
                if (!submitted) {
                    submitted = true
                    scope.launch {
                        repository.submit(CheckInStatus.NoResponse, contextProvider())
                        onFinished()
                    }
                }
            },
        )
    }

    val onPick: (CheckInStatus) -> Unit = { status ->
        if (!submitted) {
            submitted = true
            timeoutManager.stop()
            scope.launch {
                val ctx = contextProvider()
                repository.submit(status, ctx)
                onFinished()
            }
        }
    }

    val remainingMs = when (val p = phase) {
        is TimeoutManager.Phase.First -> p.remainingMs
        is TimeoutManager.Phase.Second -> p.remainingMs
        else -> 0L
    }
    val minutes = (remainingMs / 60_000L).toInt()
    val seconds = ((remainingMs % 60_000L) / 1000L).toInt()

    val sadCd = stringResource(R.string.cd_checkin_sad)
    val neutralCd = stringResource(R.string.cd_checkin_neutral)
    val happyCd = stringResource(R.string.cd_checkin_happy)

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
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.checkin_question),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.text_primary),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(12.dp))

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckInEmojiButton(
                        emoji = "😢",
                        borderColor = Color(0xFFF44336),
                        contentDescription = sadCd,
                        onClick = { onPick(CheckInStatus.Sad) },
                    )
                    CheckInEmojiButton(
                        emoji = "😐",
                        borderColor = Color(0xFFFF9800),
                        contentDescription = neutralCd,
                        onClick = { onPick(CheckInStatus.Neutral) },
                    )
                    CheckInEmojiButton(
                        emoji = "😊",
                        borderColor = Color(0xFF4CAF50),
                        contentDescription = happyCd,
                        onClick = { onPick(CheckInStatus.Happy) },
                    )
                }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.checkin_time_left_template, minutes, seconds),
                    fontSize = 11.sp,
                    color = colorResource(R.color.text_secondary),
                )
            }
        }
    }
}

@Composable
private fun RowScope.CheckInEmojiButton(
    emoji: String,
    borderColor: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = colorResource(R.color.surface),
            contentColor = colorResource(R.color.text_primary),
        ),
        modifier = Modifier
            .weight(1f)
            .height(64.dp)
            .border(3.dp, borderColor, MaterialTheme.shapes.small)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Text(text = emoji, fontSize = 30.sp)
    }
}

private fun playAlertSound(context: android.content.Context) {
    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
    val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
    ringtone.audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    ringtone.play()
}
