package br.com.lugedevelopment.notificationhistory.security

/**
 * Pure lock-state logic (no Android deps, unit-tested). The app is locked until
 * authenticated, and re-locks when it returns from background after [timeoutMs].
 */
class SessionLock(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var authenticated = false
    private var backgroundedAt: Long? = null

    val isUnlocked: Boolean get() = authenticated

    fun onAuthenticated() {
        authenticated = true
        backgroundedAt = null
    }

    fun onBackgrounded() {
        if (authenticated && backgroundedAt == null) backgroundedAt = clock()
    }

    /** Call on resume; returns true if the app is locked and needs authentication. */
    fun onForegrounded(): Boolean {
        val since = backgroundedAt
        backgroundedAt = null
        if (authenticated && since != null && clock() - since >= timeoutMs) authenticated = false
        return !authenticated
    }

    fun lock() {
        authenticated = false
        backgroundedAt = null
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}
