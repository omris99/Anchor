package com.anchor.watch.screens

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Text
import com.anchor.watch.R
import com.anchor.watch.network.PartnerPairingApi
import com.anchor.watch.network.WatchKeyStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay

private const val POLL_INTERVAL_MS = 3_000L
private const val POLL_MAX_ATTEMPTS = 100   // ~5 minutes total

@Composable
fun WatchPairingScreen(
    pairingApi: PartnerPairingApi,
    watchKeyStore: WatchKeyStore,
    onPaired: () -> Unit,
    // Friendly name of this watch device, shown in the dashboard after pairing.
    deviceName: String,
) {
    // Holds the QR content (pairing_token) and the watch_id used for polling.
    var pairingToken by remember { mutableStateOf<String?>(null) }
    var watchId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Step 1: request a pairing token from the backend.
    LaunchedEffect(Unit) {
        val result = pairingApi.initPairing(deviceName)
        if (result == null) {
            errorMessage = "שגיאת חיבור\nנסה שוב"
        } else {
            pairingToken = result.pairing_token
            watchId = result.watch_id
        }
    }

    // Step 2: once we have a watch_id, poll for the permanent API key.
    LaunchedEffect(watchId) {
        val id = watchId ?: return@LaunchedEffect
        repeat(POLL_MAX_ATTEMPTS) {
            delay(POLL_INTERVAL_MS)
            val credentials = pairingApi.fetchCredentials(id)
            if (credentials != null) {
                watchKeyStore.savePairingResult(
                    apiKey = credentials.watch_api_key,
                    userId = credentials.user_id,
                )
                onPaired()
                return@LaunchedEffect
            }
        }
        errorMessage = "פג תוקף הקישור\nנסה שוב"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.surface)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            errorMessage != null -> {
                Text(
                    text = errorMessage!!,
                    color = colorResource(R.color.sos),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }

            pairingToken != null -> {
                Text(
                    text = "סרוק מהאפליקציה",
                    color = colorResource(R.color.text_primary),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                QrCodeImage(
                    content = pairingToken!!,
                    modifier = Modifier.size(130.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "פתחו את אפליקציית\nהמשפחה וסרקו",
                    color = colorResource(R.color.text_secondary),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }

            else -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    indicatorColor = colorResource(R.color.primary),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "מתחבר...",
                    color = colorResource(R.color.text_secondary),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun QrCodeImage(content: String, modifier: Modifier = Modifier) {
    val bitmap = remember(content) { generateQrBitmap(content) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR code לקישור השעון",
            modifier = modifier,
        )
    }
}

private fun generateQrBitmap(content: String, size: Int = 400): Bitmap? {
    return runCatching {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    }.getOrNull()
}
