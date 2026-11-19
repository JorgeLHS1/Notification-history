package br.com.lugedevelopment.notificationhistory.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** The three facts shown by the status card. */
data class SetupStatus(
    val accessGranted: Boolean,
    val listenerConnected: Boolean,
    val batteryOptimizationIgnored: Boolean,
) {
    val allOk: Boolean get() = accessGranted && listenerConnected && batteryOptimizationIgnored
}

enum class OnboardingStep { ACCESS, GUIDE, DONE }

/**
 * Pure routing rule. Missing access always wins (first run and revoked access look the same:
 * the user must grant it again); then the guide runs once, or on demand via [guideRequested].
 */
fun nextStep(accessGranted: Boolean, completed: Boolean, guideRequested: Boolean): OnboardingStep = when {
    !accessGranted -> OnboardingStep.ACCESS
    !completed || guideRequested -> OnboardingStep.GUIDE
    else -> OnboardingStep.DONE
}

/** True when access was granted before and then taken away (shows the "revoked" wording). */
fun isRevoked(accessGranted: Boolean, completed: Boolean): Boolean = !accessGranted && completed

fun isBatteryOptimizationIgnored(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

/** Remembers that the first-run flow was finished (plain private SharedPreferences, no network). */
class OnboardingSettings(private val prefs: android.content.SharedPreferences) {
    var completed: Boolean
        get() = prefs.getBoolean(KEY_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPLETED, value).apply()

    companion object {
        const val FILE = "onboarding_settings"
        const val KEY_COMPLETED = "onboarding_completed"
        fun from(context: Context) =
            OnboardingSettings(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }
}

/** OEM detection: MIUI / HyperOS ship on Xiaomi, Redmi and POCO devices. */
object OemDetector {
    private val XIAOMI_FAMILY = setOf("xiaomi", "redmi", "poco")

    fun isXiaomiFamily(manufacturer: String?, brand: String?): Boolean =
        listOfNotNull(manufacturer, brand).any { it.trim().lowercase() in XIAOMI_FAMILY }

    fun isXiaomiFamily(): Boolean = isXiaomiFamily(android.os.Build.MANUFACTURER, android.os.Build.BRAND)
}

enum class LaunchResult { OEM_SCREEN, FALLBACK, FAILED }

/**
 * Candidate settings screens, most specific first. These are vendor-internal components that
 * may be absent or restricted on any given ROM version, so every start is guarded: the first
 * candidate that does not throw wins, then the generic app-details screen, then [LaunchResult.FAILED].
 */
object SettingsIntents {
    private const val SECURITY_CENTER = "com.miui.securitycenter"
    private const val POWER_KEEPER = "com.miui.powerkeeper"

    fun xiaomiAutostart(): List<Intent> = listOf(
        Intent().setComponent(
            ComponentName(SECURITY_CENTER, "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ),
        Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
    )

    fun xiaomiBatterySaver(packageName: String, label: String): List<Intent> = listOf(
        Intent().setComponent(ComponentName(POWER_KEEPER, "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
            .putExtra("package_name", packageName)
            .putExtra("package_label", label),
        Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
            .putExtra("package_name", packageName)
            .putExtra("package_label", label),
    )

    /** Standard Android list of apps with battery optimization; needs no extra permission. */
    fun batteryOptimizationList(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun appDetails(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
}

/** Tries [candidates] in order through [start]; falls back to [fallback]. Never throws. */
fun launchFirst(
    candidates: List<Intent>,
    fallback: Intent?,
    start: (Intent) -> Unit,
): LaunchResult {
    for (intent in candidates) {
        if (tryStart(intent, start)) return LaunchResult.OEM_SCREEN
    }
    if (fallback != null && tryStart(fallback, start)) return LaunchResult.FALLBACK
    return LaunchResult.FAILED
}

private fun tryStart(intent: Intent, start: (Intent) -> Unit): Boolean =
    try {
        start(intent)
        true
    } catch (_: Exception) {
        // ActivityNotFoundException, SecurityException (not exported / permission denied), etc.
        false
    }
