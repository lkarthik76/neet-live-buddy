package com.smartstudybuddy.app

import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID

private const val KEY_DEVICE_ID = "neet_buddy_device_id"

actual fun getDeviceId(): String {
    val defaults = NSUserDefaults.standardUserDefaults
    val existing = defaults.stringForKey(KEY_DEVICE_ID)
    if (existing != null) return existing
    val newId = NSUUID().UUIDString
    defaults.setObject(newId, KEY_DEVICE_ID)
    return newId
}
