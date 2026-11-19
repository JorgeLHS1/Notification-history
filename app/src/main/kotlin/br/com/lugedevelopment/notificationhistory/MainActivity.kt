package br.com.lugedevelopment.notificationhistory

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import br.com.lugedevelopment.notificationhistory.security.SecureGate
import br.com.lugedevelopment.notificationhistory.security.applySecureWindow
import br.com.lugedevelopment.notificationhistory.ui.NotificationHistoryApp as HistoryUi

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySecureWindow()
        val ingestor = (application as NotificationHistoryApp).ingestor
        setContent {
            SecureGate {
                HistoryUi(ingestor)
            }
        }
    }
}
