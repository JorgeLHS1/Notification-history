package br.com.lugedevelopment.notificationhistory

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import br.com.lugedevelopment.notificationhistory.onboarding.OnboardingGate
import br.com.lugedevelopment.notificationhistory.security.SecureGate
import br.com.lugedevelopment.notificationhistory.security.applySecureWindow
import br.com.lugedevelopment.notificationhistory.ui.NotificationHistoryApp as HistoryUi

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySecureWindow()
        val app = application as NotificationHistoryApp
        val ingestor = app.ingestor
        setContent {
            SecureGate(lock = app.sessionLock) {
                OnboardingGate {
                    HistoryUi(ingestor)
                }
            }
        }
    }
}
