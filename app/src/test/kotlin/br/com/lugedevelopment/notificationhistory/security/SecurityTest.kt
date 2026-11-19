package br.com.lugedevelopment.notificationhistory.security

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityTest {

    private class XorWrapper : KeyWrapper {
        override fun wrap(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        override fun unwrap(wrapped: ByteArray) = wrap(wrapped)
    }

    @Test
    fun keyIsGeneratedOnceStoredWrappedAndStable() {
        val file = File.createTempFile("dbkey", ".bin").apply { delete() }
        val store = DatabaseKeyStore(file, XorWrapper())
        val first = store.loadOrCreate()
        assertEquals(32, first.size)
        assertNotEquals("plain key must not be on disk", first.toList(), file.readBytes().toList())
        assertArrayEquals(first, DatabaseKeyStore(file, XorWrapper()).loadOrCreate())
        file.delete()
    }

    @Test
    fun lockedUntilAuthenticated() {
        val lock = SessionLock(timeoutMs = 1000) { 0L }
        assertFalse(lock.isUnlocked)
        assertTrue(lock.onForegrounded())
        lock.onAuthenticated()
        assertTrue(lock.isUnlocked)
    }

    @Test
    fun relocksAfterTimeoutInBackgroundOnly() {
        var now = 0L
        val lock = SessionLock(timeoutMs = 1000) { now }
        lock.onAuthenticated()

        lock.onBackgrounded()
        now = 999
        assertFalse(lock.onForegrounded())

        lock.onBackgrounded()
        now = 999 + 1000
        assertTrue(lock.onForegrounded())
        assertFalse(lock.isUnlocked)
    }

    @Test
    fun explicitLockRequiresAuthAgain() {
        val lock = SessionLock { 0L }
        lock.onAuthenticated()
        lock.lock()
        assertTrue(lock.onForegrounded())
    }
}
