package com.neetbuddy.app

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

private const val PREFS_NAME = "neet_buddy_prefs"
private const val KEY_DEVICE_ID = "device_id"

private var cachedDeviceId: String? = null

actual fun getDeviceId(): String {
    cachedDeviceId?.let { return it }
    val context = AppContextHolder.appContext
        ?: return UUID.randomUUID().toString()
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val existing = prefs.getString(KEY_DEVICE_ID, null)
    if (existing != null) {
        cachedDeviceId = existing
        return existing
    }
    val newId = UUID.randomUUID().toString()
    prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
    cachedDeviceId = newId
    return newId
}

object AppContextHolder {
    var appContext: Context? = null
}
