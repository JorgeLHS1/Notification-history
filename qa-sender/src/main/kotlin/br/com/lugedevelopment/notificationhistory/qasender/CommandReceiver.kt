package br.com.lugedevelopment.notificationhistory.qasender

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * adb entry point. Extras: cmd=send|delete_remove|delete_placeholder|read|plain|link|picture|reset,
 * chat=alice|bob|group, text=<text>, index=<n> (position in the chat's current list, 0 = oldest),
 * sender=<name> (optional, send only).
 */
class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ChatStore.init(context)
        val chat = ChatStore.chat(intent.getStringExtra("chat"))
        // `am broadcast --es index 2` and `--ei index 2` both work.
        val index = intent.getStringExtra("index")?.toIntOrNull() ?: intent.getIntExtra("index", -1)
        when (val cmd = intent.getStringExtra("cmd")) {
            "send" -> {
                ChatStore.send(context, chat, intent.getStringExtra("text") ?: "Hello", intent.getStringExtra("sender"))
                Notifier.postChat(context, chat)
            }
            "delete_remove" -> {
                ChatStore.removeAt(context, chat, index)
                Notifier.postChat(context, chat)
            }
            "delete_placeholder" -> {
                ChatStore.placeholderAt(context, chat, index)
                Notifier.postChat(context, chat)
            }
            "read" -> Notifier.cancel(context, chat.notificationId)
            "plain" -> Notifier.postPlain(context)
            "link" -> Notifier.postLink(context)
            "picture" -> Notifier.postPicture(context)
            "reset" -> Notifier.resetAll(context)
            else -> Log.w("QaSender", "Unknown cmd: $cmd")
        }
    }
}
