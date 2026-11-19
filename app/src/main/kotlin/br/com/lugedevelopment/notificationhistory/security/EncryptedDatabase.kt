package br.com.lugedevelopment.notificationhistory.security

import android.content.Context
import androidx.room.Room
import br.com.lugedevelopment.notificationhistory.data.HistoryDatabase
import java.io.File
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Factory for the production (encrypted) [HistoryDatabase]. Tests keep using
 * [HistoryDatabase.inMemory]; SQLCipher native libs are not available on the JVM.
 *
 * Callers (Application/listener/UI) should create ONE instance and share it.
 */
object EncryptedDatabase {
    const val FILE_NAME = "history-secure.db"
    private const val KEY_FILE = "history-db.key"

    fun build(context: Context): HistoryDatabase {
        val app = context.applicationContext
        System.loadLibrary("sqlcipher")
        val passphrase = DatabaseKeyStore(File(app.filesDir, KEY_FILE), KeystoreKeyWrapper())
            .loadOrCreate()
        return Room.databaseBuilder(app, HistoryDatabase::class.java, FILE_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
    }
}
