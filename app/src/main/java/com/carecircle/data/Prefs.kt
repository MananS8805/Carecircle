package com.carecircle.data

import android.content.Context

/** Caregiver / pharmacy contacts. Stored locally only (privacy: nothing leaves the device). */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("carecircle", Context.MODE_PRIVATE)
    var caregiverPhone: String
        get() = sp.getString("caregiver", "") ?: ""
        set(v) = sp.edit().putString("caregiver", v).apply()
    var pharmacyPhone: String
        get() = sp.getString("pharmacy", "") ?: ""
        set(v) = sp.edit().putString("pharmacy", v).apply()
    var patientName: String
        get() = sp.getString("patient", "") ?: ""
        set(v) = sp.edit().putString("patient", v).apply()
}
