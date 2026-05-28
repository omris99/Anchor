package com.anchor.watch

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.anchor.anchorwatchapp.presentation.MainActivity
import com.anchor.watch.screens.LanguageSelectionScreen
import com.anchor.watch.utils.LanguagePreference

class LanguageSelectionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LanguageSelectionScreen(
                onLanguageSelected = { language ->
                    LanguagePreference.setLanguage(applicationContext, language)
                    val intent = Intent(this, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK,
                        )
                    }
                    startActivity(intent)
                    finish()
                },
            )
        }
    }
}
