package br.com.lugedevelopment.notificationhistory

import android.app.Application
import br.com.lugedevelopment.notificationhistory.security.EncryptedDatabase
import br.com.lugedevelopment.notificationhistory.ingest.Ingestor
import br.com.lugedevelopment.notificationhistory.media.PrivateMediaStore
import br.com.lugedevelopment.notificationhistory.security.SessionLock
import br.com.lugedevelopment.notificationhistory.retention.RetentionWorker
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
    val ingestor: Ingestor by lazy {
        val media = PrivateMediaStore.of(this)
        Ingestor(EncryptedDatabase.build(this), deleteMediaFile = { media.delete(it) })
    }

    /** Single background thread for capture: serialises ingest writes in arrival order. */
    val ioExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }

    /**
     * Separate thread for long user-started jobs (backup export/import, media scan), so they
     * never queue ahead of notification capture on [ioExecutor].
     */
    val taskExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }

    /** Lock state lives for the process, so a configuration change (rotation) does not re-prompt. */
    val sessionLock: SessionLock by lazy { SessionLock() }

    override fun onCreate() {
        super.onCreate()
        // Scheduling must never crash app startup (also WorkManager may be uninitialised under Robolectric).
        runCatching { RetentionWorker.schedule(this) }
            .onFailure { android.util.Log.w("NotificationHistoryApp", "retention schedule failed", it) }
    }
}

