package br.com.lugedevelopment.notificationhistory

import android.app.Application
import br.com.lugedevelopment.notificationhistory.security.EncryptedDatabase
import br.com.lugedevelopment.notificationhistory.ingest.Ingestor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * App-level composition root. The single place that builds the [Ingestor]: the listener
 * service, the UI and any worker must reuse [ingestor] instead of creating their own.
 *
 * Ingestor calls are blocking: run them on [ioExecutor], never on the main thread.
 */
class NotificationHistoryApp : Application() {
    /** Lazy so a cold start does not open the database until something needs it. */
    val ingestor: Ingestor by lazy { Ingestor(EncryptedDatabase.build(this)) }

    /** Single background thread: serialises writes in arrival order. */
    val ioExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
}
