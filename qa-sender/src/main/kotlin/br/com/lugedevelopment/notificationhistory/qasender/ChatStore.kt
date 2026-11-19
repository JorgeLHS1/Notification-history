package br.com.lugedevelopment.notificationhistory.qasender

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Chat(val id: String, val title: String, val notificationId: Int, val group: Boolean, val senders: List<String>)

data class Msg(val sender: String, val text: String, val ts: Long)

/** Fake chats and their current message lists, persisted so adb commands survive process death. */
object ChatStore {
    const val MAX_MESSAGES = 7
    const val PLACEHOLDER = "This message was deleted"
    private const val KEY = "chats"

    val chats = listOf(
        Chat("alice", "Alice", 1001, false, listOf("Alice")),
        Chat("bob", "Bob", 1002, false, listOf("Bob")),
        Chat("group", "QA Group", 1003, true, listOf("Carol", "Dave", "Erin")),
    )

    private val state = MutableStateFlow<Map<String, List<Msg>>>(emptyMap())
    val messages: StateFlow<Map<String, List<Msg>>> get() = state
    private var loaded = false

    fun chat(id: String?): Chat = chats.firstOrNull { it.id == id } ?: chats.first()

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val raw = prefs(context).getString(KEY, null) ?: return
        val root = JSONObject(raw)
        state.value = chats.associate { chat ->
            val arr = root.optJSONArray(chat.id) ?: JSONArray()
            chat.id to (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Msg(o.getString("s"), o.getString("t"), o.getLong("ts"))
            }
        }
    }

    fun list(chat: Chat): List<Msg> = state.value[chat.id].orEmpty()

    @Synchronized
    fun send(context: Context, chat: Chat, text: String, sender: String? = null) {
        val current = list(chat)
        val who = sender?.takeIf { it.isNotBlank() } ?: chat.senders[current.size % chat.senders.size]
        // Strictly increasing timestamps: the history app dedups on sender + timestamp.
        val ts = maxOf(System.currentTimeMillis(), (current.lastOrNull()?.ts ?: 0L) + 1)
        update(context, chat, (current + Msg(who, text, ts)).takeLast(MAX_MESSAGES))
    }

    @Synchronized
    fun duplicate(context: Context, chat: Chat) {
        val last = list(chat).lastOrNull() ?: return
        send(context, chat, last.text, last.sender)
    }

    @Synchronized
    fun removeAt(context: Context, chat: Chat, index: Int) {
        val current = list(chat)
        if (index !in current.indices) return
        update(context, chat, current.filterIndexed { i, _ -> i != index })
    }

    @Synchronized
    fun placeholderAt(context: Context, chat: Chat, index: Int) {
        val current = list(chat)
        if (index !in current.indices) return
        update(context, chat, current.mapIndexed { i, m -> if (i == index) m.copy(text = PLACEHOLDER) else m })
    }

    @Synchronized
    fun clear(context: Context, chat: Chat) = update(context, chat, emptyList())

    private fun update(context: Context, chat: Chat, list: List<Msg>) {
        state.value = state.value + (chat.id to list)
        val root = JSONObject()
        state.value.forEach { (id, msgs) ->
            val arr = JSONArray()
            msgs.forEach { arr.put(JSONObject().put("s", it.sender).put("t", it.text).put("ts", it.ts)) }
            root.put(id, arr)
        }
        prefs(context).edit().putString(KEY, root.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("qa_chats", Context.MODE_PRIVATE)
}
