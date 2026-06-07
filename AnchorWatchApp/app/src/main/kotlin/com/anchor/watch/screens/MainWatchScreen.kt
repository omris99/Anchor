package com.anchor.watch.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.anchor.watch.R
import com.anchor.watch.utils.LocaleHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object MainWatchFormatters {
    // Locale is supplied by the caller (resolved from the OS-configured context) so the
    // clock/date follow the user's chosen language instead of a hardcoded Hebrew locale.
    fun time(date: Date, locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("HH:mm", locale).format(date)

    fun date(date: Date, locale: Locale = Locale.getDefault()): String {
        val dayName = SimpleDateFormat("EEEE", locale).format(date)
            .removePrefix("יום ")
        val dayMonth = SimpleDateFormat("d MMMM", locale).format(date)
        return "$dayName · $dayMonth"
    }
}

internal object MainWatchSizing {
    const val MIN_FONT_SP = 18
    const val MIN_TOUCH_DP = 48
    const val TIME_ACTIVE_SP = 48
    const val TIME_AMBIENT_SP = 36
    const val DATE_SP = 20
    const val BUTTON_LABEL_SP = 18
    const val BUTTON_SIZE_DP = 56
}

@Composable
fun MainWatchScreen(
    isAmbient: Boolean,
    onSosClick: () -> Unit,
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                now = Date()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        now = Date()
        onDispose { context.unregisterReceiver(receiver) }
    }

    val locale = LocaleHelper.currentLocale(context)
    val timeText = MainWatchFormatters.time(now, locale)
    val dateText = MainWatchFormatters.date(now, locale)
    val clockCd = stringResource(R.string.cd_clock)
    val dateCd = stringResource(R.string.cd_date)
    val sosCd = stringResource(R.string.cd_sos)

    // Single clock only: the large centered Text below is the watch face's clock.
    // Wear's curved TimeText was removed to avoid a duplicate time readout (elderly UX).
    Scaffold(
        timeText = {},
    ) {
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
                    text = timeText,
                    fontSize = (if (isAmbient) MainWatchSizing.TIME_AMBIENT_SP else MainWatchSizing.TIME_ACTIVE_SP).sp,
                    fontWeight = if (isAmbient) FontWeight.Normal else FontWeight.Bold,
                    color = colorResource(R.color.text_primary),
                    modifier = Modifier.semantics {
                        contentDescription = "$clockCd $timeText"
                    },
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = dateText,
                    fontSize = MainWatchSizing.DATE_SP.sp,
                    color = colorResource(R.color.text_secondary),
                    modifier = Modifier.semantics {
                        contentDescription = "$dateCd $dateText"
                    },
                )

                if (!isAmbient) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onSosClick,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = colorResource(R.color.sos),
                            contentColor = colorResource(R.color.text_on_colored),
                        ),
                        modifier = Modifier
                            .size(MainWatchSizing.BUTTON_SIZE_DP.dp)
                            .semantics { contentDescription = sosCd },
                    ) {
                        Text(
                            text = stringResource(R.string.btn_sos),
                            fontSize = MainWatchSizing.BUTTON_LABEL_SP.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorResource(R.color.text_on_colored),
                        )
                    }
                }
            }
        }
    }
}
