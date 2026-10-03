package ir.pardava.mobile.core

import android.content.Context
import ir.pardava.mobile.data.dto.AppLogBatchIn
import ir.pardava.mobile.data.dto.AppLogEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Fire-and-forget logger for install & usage events, recorded on the site in
 * the same `customer_events` table used by web analytics (source=android).
 *
 * - install_id: stable UUID persisted in DataStore (first launch → app_install)
 * - session_id: fresh UUID per process
 * - Batches flush every ~15s or when 10 events queue up; failures are silent
 *   and re-queued (bounded) — usage stats, not critical data.
 */
class EventLogger private constructor(
    private val client: ApiClient,
    private val store: TokenStore,
    private val langProvider: () -> String,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ArrayDeque<AppLogEvent>()
    private val mutex = Any()
    @Volatile
    private var installId: String = ""
    val sessionId: String = UUID.randomUUID().toString().replace("-", "").take(32)
    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        scope.launch {
            installId = store.installId.let { flow ->
                var id = ""
                flow.collect { id = it }   // emits current value, then updates
                id
            }
            if (installId.isBlank()) {
                installId = UUID.randomUUID().toString().replace("-", "").take(32)
                store.setInstallId(installId)
            }
        }
        scope.launch {
            while (true) {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
        }
    }

    fun log(name: String, path: String? = null, label: String? = null, detail: Map<String, String>? = null, value: Double? = null) {
        if (!name.startsWith("app_")) return
        synchronized(mutex) {
            queue.addLast(
                AppLogEvent(
                    name = name,
                    ts = System.currentTimeMillis() / 1000,
                    path = path,
                    label = label?.take(120),
                    detail = detail,
                    value = value,
                ),
            )
            if (queue.size > MAX_QUEUE) queue.removeFirst()
        }
        if (synchronized(mutex) { queue.size } >= FLUSH_SIZE) {
            scope.launch { flush() }
        }
    }

    suspend fun flush() {
        val batch: List<AppLogEvent> = synchronized(mutex) {
            if (queue.isEmpty()) return
            val take = minOf(queue.size, MAX_BATCH)
            List(take) { queue.removeFirst() }
        }
        runCatching {
            client.api.logEvents(
                AppLogBatchIn(events = batch, installId = installId, sessionId = sessionId, lang = langProvider()),
            )
        }.onFailure {
            synchronized(mutex) {
                for (i in batch.indices.reversed()) queue.addFirst(batch[i])
                while (queue.size > MAX_QUEUE) queue.removeLast()
            }
        }
    }

    companion object {
        private const val MAX_QUEUE = 200
        private const val MAX_BATCH = 50
        private const val FLUSH_SIZE = 10
        private const val FLUSH_INTERVAL_MS = 15_000L

        @Volatile
        private var instance: EventLogger? = null

        fun get(client: ApiClient, store: TokenStore, langProvider: () -> String): EventLogger =
            instance ?: synchronized(this) {
                instance ?: EventLogger(client, store, langProvider).also { instance = it }
            }
    }
}
