package com.anchor.watch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import com.anchor.anchorwatchapp.presentation.MainActivity
import com.anchor.watch.screens.LanguageSelectionScreen
import com.anchor.watch.utils.LanguagePreference
import com.anchor.watch.utils.LocaleHelper

class LanguageSelectionActivity : ComponentActivity() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Direction from the resolved (system) locale — the picker is bilingual, but we
        // still honor whatever the OS resolves so it never renders against the grain.
        val layoutDirection = LocaleHelper.layoutDirection(this)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                LanguageSelectionScreen(
                    onLanguageSelected = { language ->
                        // Gate flag for "has the user chosen?" (drives first-launch picker).
                        LanguagePreference.setLanguage(applicationContext, language)
                        // Platform per-app locale: the OS now owns locale + direction app-wide.
                        LocaleHelper.applyLanguage(applicationContext, language)
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
}
