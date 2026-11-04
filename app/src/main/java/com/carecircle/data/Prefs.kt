package com.carecircle.data

import android.content.Context

/** Small local settings. Contacts live on each Person in the database, not here. Nothing leaves the device. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("carecircle", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboardingDone", false)
        set(v) = sp.edit().putBoolean("onboardingDone", v).apply()

    /** [ALL_PEOPLE] shows everyone on Today; [UNSET] means "use the first person". */
    var selectedPersonId: Long
        get() = sp.getLong("selectedPerson", UNSET)
        set(v) = sp.edit().putLong("selectedPerson", v).apply()

    /** "" = follow the system language, otherwise a BCP-47 tag such as "hi" or "gu". */
    var language: String
        get() = sp.getString("language", "") ?: ""
        set(v) = sp.edit().putString("language", v).apply()

    /** "" = not chosen yet, "patient" = track and manage medicines, "family" = follow a family member. */
    var appMode: String
        get() = sp.getString("appMode", "") ?: ""
        set(v) = sp.edit().putString("appMode", v).apply()

    /** When this app last posted a dose reminder. Shown on the reminders health check. */
    var lastReminderAt: Long
        get() = sp.getLong("lastReminderAt", 0L)
        set(v) = sp.edit().putLong("lastReminderAt", v).apply()

    /** Newest first, at most [LOG_MAX] lines of time, reason and alarm count. Holds no health data. */
    val rescheduleLog: List<RescheduleEntry>
        get() = (sp.getString("rescheduleLog", "") ?: "").lines().mapNotNull { l ->
            val p = l.split("|")
            if (p.size == 3) RescheduleEntry(p[0].toLongOrNull() ?: return@mapNotNull null, p[1], p[2].toIntOrNull() ?: 0) else null
        }

    fun logReschedule(at: Long, reason: String, alarms: Int) {
        val line = "$at|${reason.filter { it.isLetterOrDigit() || it == '_' }.takeLast(40)}|$alarms"
        val old = (sp.getString("rescheduleLog", "") ?: "").lines().filter { it.isNotBlank() }
        sp.edit().putString("rescheduleLog", (listOf(line) + old).take(LOG_MAX).joinToString("\n")).apply()
    }

    /** Settings > Display. Not health data, so it survives "Delete all my data" like the language does. */
    var textSize: String
        get() = sp.getString("textSize", "normal") ?: "normal"
        set(v) = sp.edit().putString("textSize", v).apply()

    var highContrast: Boolean
        get() = sp.getBoolean("highContrast", false)
        set(v) = sp.edit().putBoolean("highContrast", v).apply()

    var simpleMode: Boolean
        get() = sp.getBoolean("simpleMode", false)
        set(v) = sp.edit().putBoolean("simpleMode", v).apply()

    /** First-run guide: finished or skipped, the step reached (survives process death), and the app version whose news was seen. */
    var guideDone: Boolean
        get() = sp.getBoolean("guideDone", false)
        set(v) = sp.edit().putBoolean("guideDone", v).apply()

    var guideStep: Int
        get() = sp.getInt("guideStep", 0)
        set(v) = sp.edit().putInt("guideStep", v).apply()

    var whatsNewSeen: Int
        get() = sp.getInt("whatsNewSeen", 0)
        set(v) = sp.edit().putInt("whatsNewSeen", v).apply()

    /** Removes one registered key; used by delete-all so settings that are not health data can stay. */
    fun remove(key: String) = sp.edit().remove(key).apply()

    fun clearAll() = sp.edit().clear().apply()

    companion object {
        const val LOG_MAX = 20
        const val MODE_PATIENT = "patient"
        const val MODE_FAMILY = "family"
        const val UNSET = -2L
        const val ALL_PEOPLE = -1L
    }
}

data class RescheduleEntry(val at: Long, val reason: String, val alarms: Int)
