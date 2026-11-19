package br.com.lugedevelopment.notificationhistory.onboarding

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnboardingLogicTest {
    @Test
    fun missingAccessAlwaysShowsAccessStep() {
        assertEquals(OnboardingStep.ACCESS, nextStep(accessGranted = false, completed = false, guideRequested = false))
        assertEquals(OnboardingStep.ACCESS, nextStep(accessGranted = false, completed = true, guideRequested = true))
    }

    @Test
    fun guideRunsOnceThenDoneUnlessRequested() {
        assertEquals(OnboardingStep.GUIDE, nextStep(true, completed = false, guideRequested = false))
        assertEquals(OnboardingStep.DONE, nextStep(true, completed = true, guideRequested = false))
        assertEquals(OnboardingStep.GUIDE, nextStep(true, completed = true, guideRequested = true))
    }

    @Test
    fun revokedOnlyAfterCompletion() {
        assertTrue(isRevoked(accessGranted = false, completed = true))
        assertFalse(isRevoked(accessGranted = false, completed = false))
        assertFalse(isRevoked(accessGranted = true, completed = true))
    }

    @Test
    fun statusAllOkNeedsEveryFlag() {
        assertTrue(SetupStatus(true, true, true).allOk)
        assertFalse(SetupStatus(true, true, false).allOk)
        assertFalse(SetupStatus(true, false, true).allOk)
        assertFalse(SetupStatus(false, true, true).allOk)
    }

    @Test
    fun xiaomiFamilyDetection() {
        assertTrue(OemDetector.isXiaomiFamily("Xiaomi", "Xiaomi"))
        assertTrue(OemDetector.isXiaomiFamily("XIAOMI", "POCO"))
        assertTrue(OemDetector.isXiaomiFamily("other", "Redmi"))
        assertFalse(OemDetector.isXiaomiFamily("samsung", "samsung"))
        assertFalse(OemDetector.isXiaomiFamily(null, null))
    }

    @Test
    fun launchUsesFirstCandidateThatWorks() {
        val started = mutableListOf<Intent>()
        val candidates = listOf(Intent("a"), Intent("b"), Intent("c"))
        val result = launchFirst(candidates, Intent("fallback")) {
            if (it.action == "a") throw android.content.ActivityNotFoundException()
            started += it
        }
        assertEquals(LaunchResult.OEM_SCREEN, result)
        assertEquals(listOf("b"), started.map { it.action })
    }

    @Test
    fun launchFallsBackWhenVendorScreensMissing() {
        val started = mutableListOf<Intent>()
        val result = launchFirst(listOf(Intent("a")), Intent("fallback")) {
            if (it.action != "fallback") throw SecurityException("not exported")
            started += it
        }
        assertEquals(LaunchResult.FALLBACK, result)
        assertEquals(listOf("fallback"), started.map { it.action })
    }

    @Test
    fun launchFailsGracefullyWhenNothingOpens() {
        val result = launchFirst(listOf(Intent("a")), Intent("fallback")) { throw IllegalStateException() }
        assertEquals(LaunchResult.FAILED, result)
        assertEquals(LaunchResult.FAILED, launchFirst(emptyList(), null) { })
    }

    @Test
    fun xiaomiIntentsCarryPackageExtras() {
        val battery = SettingsIntents.xiaomiBatterySaver("pkg.x", "App")
        assertTrue(battery.all { it.getStringExtra("package_name") == "pkg.x" })
        assertTrue(SettingsIntents.xiaomiAutostart().isNotEmpty())
    }

    @Test
    fun completedFlagPersists() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences(OnboardingSettings.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        assertFalse(OnboardingSettings.from(ctx).completed)
        OnboardingSettings.from(ctx).completed = true
        assertTrue(OnboardingSettings.from(ctx).completed)
    }
}