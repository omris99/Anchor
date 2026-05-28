package com.anchor.watch.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    val HEBREW: Locale = Locale("he")

    fun wrap(context: Context): Context {
        Locale.setDefault(HEBREW)
        val config = Configuration(context.resources.configuration)
        config.setLocale(HEBREW)
        config.setLayoutDirection(HEBREW)
        return context.createConfigurationContext(config)
    }
}
