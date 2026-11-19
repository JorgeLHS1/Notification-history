package br.com.lugedevelopment.notificationhistory.listener

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.core.app.ApplicationProvider
import android.graphics.Bitmap
import android.os.Bundle
import br.com.lugedevelopment.notificationhistory.ingest.AttachmentExtras
import br.com.lugedevelopment.notificationhistory.ingest.NotificationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SnapshotMapperTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun map(n: Notification, event: NotificationSnapshot.Event = NotificationSnapshot.Event.POSTED) =
        SnapshotMapper.map("pkg.chat", "Chat", "key1", 42L, n, event)

    @Test
    fun plainNotificationCopiesTitleAndText() {
        val n = NotificationCompat.Builder(ctx, "c").setContentTitle("Ana").setContentText("hi").build()
        val s = map(n)
        assertEquals("Ana", s.title)
        assertEquals("hi", s.text)
        assertEquals(42L, s.postedAt)
        assertEquals("key1", s.key)
        assertTrue(s.messages.isEmpty())
        assertFalse(s.isGroupSummary)
    }

    @Test
    fun messagingStyleIsCopiedWithSenderAndGroup() {
        val me = Person.Builder().setName("Me").build()
        val ana = Person.Builder().setName("Ana").build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle("Family")
            .setGroupConversation(true)
            .addMessage("one", 10L, ana)
            .addMessage("two", 20L, ana)
        val n = NotificationCompat.Builder(ctx, "c").setStyle(style).build()
        val s = map(n)
        assertEquals("Family", s.conversationTitle)
        assertTrue(s.isGroupConversation)
        assertEquals(listOf("one", "two"), s.messages.map { it.text })
        assertEquals("Ana", s.messages[0].sender)
        assertEquals(20L, s.messages[1].timestamp)
    }

    @Test
    fun groupSummaryFlagAndRemovedEventArePropagated() {
        val n = NotificationCompat.Builder(ctx, "c").setGroupSummary(true).setGroup("g").build()
        val s = map(n, NotificationSnapshot.Event.REMOVED)
        assertTrue(s.isGroupSummary)
        assertEquals(NotificationSnapshot.Event.REMOVED, s.event)
    }

    @Test
    fun pictureUriIsKeptAsAttachmentReference() {
        val n = NotificationCompat.Builder(ctx, "c").setContentTitle("Ana").setContentText("photo")
            .addExtras(Bundle().apply { putString(SnapshotMapper.EXTRA_PICTURE_URI, "content://example/pic/1") })
            .build()
        assertEquals("content://example/pic/1", map(n).extras[AttachmentExtras.PICTURE])
    }

    @Test
    fun embeddedPictureWithoutUriIsMarked() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val n = NotificationCompat.Builder(ctx, "c").setContentTitle("Ana").setContentText("photo")
            .setStyle(NotificationCompat.BigPictureStyle().bigPicture(bitmap))
            .build()
        assertEquals("embedded", map(n).extras[AttachmentExtras.PICTURE])
    }

    @Test
    fun noPictureMeansNoPictureExtra() {
        val n = NotificationCompat.Builder(ctx, "c").setContentTitle("Ana").setContentText("hi").build()
        assertFalse(map(n).extras.containsKey(AttachmentExtras.PICTURE))
    }
}
