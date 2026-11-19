package br.com.lugedevelopment.notificationhistory.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Retention values chosen by the user. Only stored here; the cleanup job (T7) reads them.
 * A value of [KEEP_FOREVER] (0) means "never expire". Pinned messages never expire anyway.
 */
class RetentionSettings(private val prefs: SharedPreferences) {

    var generalDays: Int
        get() = prefs.getInt(KEY_GENERAL_DAYS, DEFAULT_GENERAL_DAYS)
        set(value) = prefs.edit().putInt(KEY_GENERAL_DAYS, value.coerceAtLeast(0)).apply()

    var deletedDays: Int
        get() = prefs.getInt(KEY_DELETED_DAYS, DEFAULT_DELETED_DAYS)
        set(value) = prefs.edit().putInt(KEY_DELETED_DAYS, value.coerceAtLeast(0)).apply()

    companion object {
        const val PREFS_NAME = "retention_settings"
        const val KEY_GENERAL_DAYS = "retention_general_days"
        const val KEY_DELETED_DAYS = "retention_deleted_days"
        const val KEEP_FOREVER = 0
        const val DEFAULT_GENERAL_DAYS = 90
        const val DEFAULT_DELETED_DAYS = KEEP_FOREVER

        /** Choices offered by the settings screen, in days. */
        val OPTIONS = listOf(7, 30, 90, 180, 365, KEEP_FOREVER)

        fun from(context: Context) = RetentionSettings(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        )
    }
}
