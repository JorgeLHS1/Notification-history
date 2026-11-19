package br.com.lugedevelopment.notificationhistory.ui

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.lugedevelopment.notificationhistory.R
import br.com.lugedevelopment.notificationhistory.backup.InvalidBackupException
import br.com.lugedevelopment.notificationhistory.media.MediaSettings
import br.com.lugedevelopment.notificationhistory.media.PrivateMediaStore
import br.com.lugedevelopment.notificationhistory.media.SafTreeMediaSource
import br.com.lugedevelopment.notificationhistory.NotificationHistoryApp as HistoryApplication

/** Settings block: backup export/import (SAF documents) and media recovery from a user-picked folder. */
@Composable
fun BackupMediaSection() {
    val context = LocalContext.current
    val app = context.applicationContext as HistoryApplication
    val main = remember { Handler(Looper.getMainLooper()) }
    var status by remember { mutableStateOf<String?>(null) }
    var folder by remember { mutableStateOf(MediaSettings.treeUri(context)) }
    val res = context.resources

    fun background(work: () -> String) {
        status = res.getString(R.string.backup_working)
        app.ioExecutor.execute {
            val message = runCatching(work).getOrElse {
                if (it is InvalidBackupException) res.getString(R.string.backup_invalid) else res.getString(R.string.backup_failed)
            }
            main.post { status = message }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        background {
            context.contentResolver.openOutputStream(uri)!!.use { app.ingestor.exportBackup(it) }
            res.getString(R.string.backup_export_done)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        background {
            val r = context.contentResolver.openInputStream(uri)!!.use { app.ingestor.importBackup(it) }
            res.getString(R.string.backup_import_done, r.messages, r.duplicates)
        }
    }
    fun scan(uri: android.net.Uri) = background {
        val store = PrivateMediaStore.of(context)
        val source = SafTreeMediaSource(context, uri)
        val r = app.ingestor.recoverMedia(source, save = { id, file ->
            source.open(file)?.let { store.save(id, file, it).absolutePath }
        })
        res.getString(R.string.media_scan_done, r.scanned, r.copied)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        MediaSettings.setTreeUri(context, uri)
        folder = uri
        scan(uri)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(stringResource(R.string.backup_note), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportLauncher.launch("notification-history-backup.zip") }) {
                Text(stringResource(R.string.backup_export))
            }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                Text(stringResource(R.string.backup_import))
            }
        }
        Text(
            stringResource(R.string.settings_media),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(stringResource(R.string.media_explanation), style = MaterialTheme.typography.bodySmall)
        Text(
            if (folder == null) stringResource(R.string.media_no_folder) else stringResource(R.string.media_folder_set),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { folderLauncher.launch(null) }) { Text(stringResource(R.string.media_pick_folder)) }
            folder?.let { f -> OutlinedButton(onClick = { scan(f) }) { Text(stringResource(R.string.media_scan)) } }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
    }
}
