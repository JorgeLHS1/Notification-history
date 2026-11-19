package br.com.lugedevelopment.notificationhistory.qasender

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChatStore.init(this)
        setContent { MaterialTheme { QaScreen() } }
    }
}

@Composable
private fun QaScreen() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var selected by rememberSaveable { mutableStateOf(ChatStore.chats.first().id) }
    var input by rememberSaveable { mutableStateOf("") }
    val all by ChatStore.messages.collectAsState()
    val chat = ChatStore.chat(selected)
    val messages = all[chat.id].orEmpty()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChatStore.chats.forEach {
                FilterChip(selected = it.id == selected, onClick = { selected = it.id }, label = { Text(it.title) })
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(stringResource(R.string.message_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (input.isNotBlank()) {
                    ChatStore.send(context, chat, input)
                    Notifier.postChat(context, chat)
                    input = ""
                }
            }) { Text(stringResource(R.string.send)) }
            OutlinedButton(onClick = {
                ChatStore.duplicate(context, chat)
                Notifier.postChat(context, chat)
            }) { Text(stringResource(R.string.send_duplicate)) }
            OutlinedButton(onClick = { Notifier.cancel(context, chat.notificationId) }) {
                Text(stringResource(R.string.mark_read))
            }
        }
        HorizontalDivider()
        if (messages.isEmpty()) Text(stringResource(R.string.no_messages))
        messages.forEachIndexed { index, msg ->
            Column {
                Text("[$index] ${msg.sender}: ${msg.text}")
                Row {
                    TextButton(onClick = {
                        ChatStore.removeAt(context, chat, index)
                        Notifier.postChat(context, chat)
                    }) { Text(stringResource(R.string.delete_remove)) }
                    TextButton(onClick = {
                        ChatStore.placeholderAt(context, chat, index)
                        Notifier.postChat(context, chat)
                    }) { Text(stringResource(R.string.delete_placeholder)) }
                }
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.other_notifications), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Notifier.postPlain(context) }) { Text(stringResource(R.string.post_plain)) }
            OutlinedButton(onClick = { Notifier.postLink(context) }) { Text(stringResource(R.string.post_link)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Notifier.postPicture(context) }) { Text(stringResource(R.string.post_picture)) }
            OutlinedButton(onClick = { Notifier.resetAll(context) }) { Text(stringResource(R.string.reset)) }
        }
    }
}
