package br.com.lugedevelopment.notificationhistory

import android.content.Context
import br.com.lugedevelopment.notificationhistory.data.HistoryDatabase
import br.com.lugedevelopment.notificationhistory.ingest.Ingestor

/**
 * TEMPORARY (T5): lazily builds the app-wide [Ingestor] so the UI can run on its own.
 * T3 owns the Application class that builds the Ingestor shared with the listener; when it
 * lands, delete this file and make MainActivity read the Ingestor from the Application.
 */
object TemporaryIngestorProvider {
    @Volatile private var instance: Ingestor? = null

    fun get(context: Context): Ingestor = instance ?: synchronized(this) {
        instance ?: Ingestor(HistoryDatabase.build(context.applicationContext)).also { instance = it }
    }
}
