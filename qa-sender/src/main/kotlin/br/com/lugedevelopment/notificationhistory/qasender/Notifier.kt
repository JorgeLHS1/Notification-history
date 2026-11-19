package br.com.lugedevelopment.notificationhistory.qasender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person

/** Posts the notifications that imitate a chat app. Every chat reuses one stable notification id. */
object Notifier {
    private const val CHANNEL = "qa_chats"
    private const val TAG = "QaSender"
    const val ID_PLAIN = 2001
    const val ID_LINK = 2002
    const val ID_PICTURE = 2003

    /** Reposts the chat's notification with its full current list (cancels it when empty). */
    fun postChat(context: Context, chat: Chat) {
        val messages = ChatStore.list(chat)
        if (messages.isEmpty()) {
            cancel(context, chat.notificationId)
            return
        }
        val me = Person.Builder().setName("Me").build()
        val style = NotificationCompat.MessagingStyle(me)
        if (chat.group) {
            style.conversationTitle = chat.title
            style.isGroupConversation = true
        }
        messages.forEach {
            val person = Person.Builder().setName(it.sender).setKey(it.sender).build()
            style.addMessage(NotificationCompat.MessagingStyle.Message(it.text, it.ts, person))
        }
        val notification = builder(context)
            .setContentTitle(chat.title)
            .setContentText(messages.last().text)
            .setStyle(style)
            .setOnlyAlertOnce(true)
            .build()
        notify(context, chat.notificationId, notification)
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    fun postPlain(context: Context) = notify(
        context, ID_PLAIN,
        builder(context)
            .setContentTitle(context.getString(R.string.plain_title))
            .setContentText(context.getString(R.string.plain_text))
            .build(),
    )

    fun postLink(context: Context) {
        val text = context.getString(R.string.link_text)
        notify(
            context, ID_LINK,
            builder(context)
                .setContentTitle(context.getString(R.string.link_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .build(),
        )
    }

    fun postPicture(context: Context) {
        val bitmap = generateBitmap()
        notify(
            context, ID_PICTURE,
            builder(context)
                .setContentTitle(context.getString(R.string.picture_title))
                .setContentText(context.getString(R.string.picture_text))
                .setLargeIcon(bitmap)
                .setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))
                .build(),
        )
    }

    fun resetAll(context: Context) {
        ChatStore.chats.forEach {
            ChatStore.clear(context, it)
            cancel(context, it.notificationId)
        }
        listOf(ID_PLAIN, ID_LINK, ID_PICTURE).forEach { cancel(context, it) }
    }

    private fun builder(context: Context): NotificationCompat.Builder {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH),
            )
        }
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(false)
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are not allowed for this app; open it and tap Allow")
            return
        }
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted", e)
        }
    }

    private fun generateBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 600f, 300f, Color.rgb(0x1E, 0x88, 0xE5), Color.rgb(0x8E, 0x24, 0xAA), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, 600f, 300f, paint)
        paint.shader = null
        paint.color = Color.WHITE
        paint.textSize = 96f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("QA", 300f, 190f, paint)
        return bmp
    }
}
