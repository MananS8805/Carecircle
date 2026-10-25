package com.carecircle

import android.content.Context
import android.content.res.Configuration
import com.carecircle.data.Prefs
import java.util.Locale

/**
 * Notifications, WhatsApp messages and the PDF are built outside an activity, so they take the app language from
 * here instead of the system language. Empty means "follow the system".
 */
object AppLocale {
    val supported = listOf("" to "System default", "en" to "English", "hi" to "हिन्दी", "gu" to "ગુજરાતી")

    fun wrap(ctx: Context): Context {
        val tag = Prefs(ctx).language
        if (tag.isEmpty()) return ctx
        val cfg = Configuration(ctx.resources.configuration)
        cfg.setLocale(Locale.forLanguageTag(tag))
        return ctx.createConfigurationContext(cfg)
    }

    /** The locale used for dates and times in non-UI code. */
    fun locale(ctx: Context): Locale = Prefs(ctx).language.let { if (it.isEmpty()) Locale.getDefault() else Locale.forLanguageTag(it) }
}
