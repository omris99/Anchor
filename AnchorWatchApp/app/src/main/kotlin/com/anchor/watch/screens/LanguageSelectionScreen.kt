package com.anchor.watch.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.anchor.watch.R
import com.anchor.watch.utils.LanguagePreference

@Composable
fun LanguageSelectionScreen(
    onLanguageSelected: (String) -> Unit,
) {
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
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            ) {
                Text(
                    text = "Language / שפה",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.text_primary),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { onLanguageSelected(LanguagePreference.LANG_ENGLISH) },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorResource(R.color.primary),
                        contentColor = colorResource(R.color.text_primary),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        text = "English",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(R.color.text_primary),
                    )
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { onLanguageSelected(LanguagePreference.LANG_HEBREW) },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = colorResource(R.color.primary),
                        contentColor = colorResource(R.color.text_primary),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        text = "עברית",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(R.color.text_primary),
                    )
                }
            }
        }
    }
}
