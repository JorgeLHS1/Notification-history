package br.com.lugedevelopment.notificationhistory

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaceholderTest {
    @Test
    fun applicationId_matchesSpec() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("br.com.lugedevelopment.notificationhistory", context.packageName)
    }
}
