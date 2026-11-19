package br.com.lugedevelopment.notificationhistory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.lugedevelopment.notificationhistory.ui.NotificationHistoryApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // T3: replace TemporaryIngestorProvider with the Ingestor owned by the Application class.
        val ingestor = TemporaryIngestorProvider.get(this)
        setContent { NotificationHistoryApp(ingestor) }
    }
}
