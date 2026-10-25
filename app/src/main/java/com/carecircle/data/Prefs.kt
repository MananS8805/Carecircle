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

    fun clearAll() = sp.edit().clear().apply()

    companion object {
        const val UNSET = -2L
        const val ALL_PEOPLE = -1L
    }
}
