package com.redmagic.fps

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

internal object LocaleSupport {
    const val KEY = "ui_language"
    val supported = listOf("en", "ru", "es", "de", "ja")
    val names = listOf("English", "Русский", "Español", "Deutsch", "日本語")

    fun resolve(language: String?): String = language?.takeIf { it in supported } ?: "en"

    fun language(context: Context): String = resolve(context.getSharedPreferences("state", Context.MODE_PRIVATE).getString(KEY, null))

    fun contextFor(context: Context, language: String): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(resolve(language)))
        return context.createConfigurationContext(config)
    }

    fun wrap(context: Context): Context = contextFor(context, language(context))
}
