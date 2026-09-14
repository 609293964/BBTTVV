package com.bbttvv.app.core.network.socket

import android.util.Log
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.network.resolveAppUserAgent
import com.bbttvv.app.core.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.pow

internal val LIVE_DANMAKU_AUTH_PROTOCOL_VERSION = DanmakuProtocol.PROTO_VER_BROTLI
internal const val LIVE_DANMAKU_MAX_TRANSPORT_RETRIES = 6

internal fun shouldRetryLiveDanmakuTransport(retryCount: Int): Boolean {
    return retryCount.coerceAtLeast(0) < LIVE_DANMAKU_MAX_TRANSPORT_RETRIES
}

data class LiveDanmakuEndpoint(val url: String)

data class LiveDanmakuConnectionConfig(
    val endpoints: List<LiveDanmakuEndpoint>,
    val token: String,
    val realRoomId: Long,
    val uid: Long = 0L,
)

enum class LiveDanmakuConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    AUTHENTICATED,
    RECONNECTING,
    FAILED,
    RELEASED,
}

class LiveDanmakuClient(
    private val scope: CoroutineScope,
    private val reconnectConfigProvider: (suspend () -> LiveDanmakuConnectionConfig?)? = null,
) {
    private val tag = "LiveDanmakuClient"
    private val released = AtomicBoolean(false)
    private val socketLock = Any()

    private var webSocket: WebSocket? = null
    private var socketGeneration: Long = 0L
    private var connectionLifecycleGeneration: Long = 0L
    private var currentConfig: LiveDanmakuConnectionConfig? = null
    private var currentEndpointIndex: Int = 0
    private var currentAuthBody: String = ""
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var decodeJob: Job? = null
    private var retryCount: Int = 0
    private var authFailureCount: Int = 0
    private var refreshBeforeNextReconnect: Boolean = false

    private val _isConnected = AtomicBoolean(false)
    val isConnected: Boolean get() = _isConnected.get()

    private val _connectionState = MutableStateFlow(LiveDanmakuConnectionState.IDLE)
    val connectionState = _connectionState.asStateFlow()

    private data class IncomingFrame(val generation: Long, val data: ByteArray)

    private val incomingFrames = Channel<IncomingFrame>(
        capacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _messageFlow = MutableSharedFlow<DanmakuProtocol.Packet>(
        replay = 0,
        extraBufferCapacity = 200,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messageFlow = _messageFlow.asSharedFlow()

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (released.get()) {
                webSocket.cancel()
                return
            }
            if (activeGenerationFor(webSocket) == null) {
                webSocket.cancel()
                return
            }
            Logger.d(tag) { "WebSocket connected: ${currentEndpointUrl()}" }
            _connectionState.value = LiveDanmakuConnectionState.CONNECTED
            sendAuthPacket()
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            val generation = activeGenerationFor(webSocket) ?: return
            onIncomingMessage(generation, bytes)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (activeGenerationFor(webSocket) == null) return
            Logger.d(tag) { "WebSocket closed: $code - $reason" }
            clearSocketIfCurrent(webSocket)
            _isConnected.set(false)
            stopHeartbeat()
            if (code != NORMAL_CLOSURE_CODE && !released.get()) scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (activeGenerationFor(webSocket) == null) return
            Log.e(tag, "WebSocket failure: ${t.message}")
            clearSocketIfCurrent(webSocket)
            _isConnected.set(false)
            stopHeartbeat()
            if (!released.get()) scheduleReconnect()
        }
    }

    init {
        startDecodeLoop()
    }

    fun connect(url: String, token: String, roomId: Long, uid: Long = 0L) {
        connect(
            LiveDanmakuConnectionConfig(
                endpoints = listOf(LiveDanmakuEndpoint(url)),
                token = token,
                realRoomId = roomId,
                uid = uid,
            )
        )
    }

    fun connect(config: LiveDanmakuConnectionConfig) {
        if (released.get()) return
        val normalizedConfig = config.normalized()
        if (normalizedConfig.endpoints.isEmpty()) {
            failConnection("No live danmaku endpoint available")
            return
        }

        val lifecycle = beginConnectionLifecycle()
        retryCount = 0
        authFailureCount = 0
        refreshBeforeNextReconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        currentConfig = normalizedConfig
        currentEndpointIndex = 0
        updateAuthBody(normalizedConfig)
        internalConnect(lifecycle)
    }

    fun disconnect() {
        Logger.d(tag) { "Disconnecting" }
        invalidateConnectionLifecycle()
        closeCurrentSocket(force = false, cancelReconnect = true)
        _connectionState.value = LiveDanmakuConnectionState.IDLE
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        Logger.d(tag) { "Releasing" }
        invalidateConnectionLifecycle()
        closeCurrentSocket(force = true, cancelReconnect = true)
        stopDecodeLoop()
        _connectionState.value = LiveDanmakuConnectionState.RELEASED
    }

    private fun internalConnect(expectedLifecycle: Long = currentLifecycleGeneration()) {
        if (released.get() || !isLifecycleCurrent(expectedLifecycle)) return
        val config = currentConfig ?: return failConnection("Live danmaku config is missing")
        val endpoint = config.endpoints.getOrNull(currentEndpointIndex)
            ?: return failConnection("Live danmaku endpoint is missing")

        closeCurrentSocket(force = false, cancelReconnect = false)
        if (released.get() || !isLifecycleCurrent(expectedLifecycle)) return
        updateAuthBody(config)

        _isConnected.set(false)
        _connectionState.value = if (retryCount > 0) {
            LiveDanmakuConnectionState.RECONNECTING
        } else {
            LiveDanmakuConnectionState.CONNECTING
        }

        Logger.d(tag) {
            "Connecting to ${endpoint.url}, room=${config.realRoomId}, endpointIndex=$currentEndpointIndex"
        }
        val request = try {
            Request.Builder()
                .url(endpoint.url)
                .header("User-Agent", resolveAppUserAgent(NetworkModule.appContext))
                .header("Origin", "https://live.bilibili.com")
                .build()
        } catch (error: IllegalArgumentException) {
            Log.e(tag, "Invalid live danmaku endpoint: ${error.message}")
            failConnection("Invalid live danmaku endpoint")
            return
        }

        if (released.get() || !isLifecycleCurrent(expectedLifecycle)) return
        val socket = NetworkModule.okHttpClient.newWebSocket(request, listener)
        val accepted = synchronized(socketLock) {
            if (released.get() || connectionLifecycleGeneration != expectedLifecycle) {
                false
            } else {
                socketGeneration += 1L
                webSocket = socket
                true
            }
        }
        if (!accepted) socket.cancel()
    }

    private fun closeCurrentSocket(force: Boolean, cancelReconnect: Boolean) {
        stopHeartbeat()
        if (cancelReconnect) {
            reconnectJob?.cancel()
            reconnectJob = null
        }
        val socket = synchronized(socketLock) {
            val current = webSocket
            webSocket = null
            socketGeneration += 1L
            current
        }
        _isConnected.set(false)
        if (force) socket?.cancel() else socket?.close(NORMAL_CLOSURE_CODE, "Normal Closure")
    }

    private fun beginConnectionLifecycle(): Long {
        return synchronized(socketLock) {
            connectionLifecycleGeneration += 1L
            connectionLifecycleGeneration
        }
    }

    private fun invalidateConnectionLifecycle(): Long = beginConnectionLifecycle()

    private fun currentLifecycleGeneration(): Long {
        return synchronized(socketLock) { connectionLifecycleGeneration }
    }

    private fun isLifecycleCurrent(expected: Long): Boolean {
        return synchronized(socketLock) { connectionLifecycleGeneration == expected }
    }

    private fun clearSocketIfCurrent(socket: WebSocket) {
        synchronized(socketLock) {
            if (webSocket === socket) {
                webSocket = null
                socketGeneration += 1L
            }
        }
    }

    private fun activeGenerationFor(socket: WebSocket): Long? {
        return synchronized(socketLock) {
            if (webSocket === socket) socketGeneration else null
        }
    }

    private fun currentGeneration(): Long = synchronized(socketLock) { socketGeneration }

    private fun currentEndpointUrl(): String {
        val config = currentConfig ?: return ""
        return config.endpoints.getOrNull(currentEndpointIndex)?.url.orEmpty()
    }

    private fun updateAuthBody(config: LiveDanmakuConnectionConfig) {
        currentAuthBody = JSONObject().apply {
            put("uid", config.uid)
            put("roomid", config.realRoomId)
            put("protover", LIVE_DANMAKU_AUTH_PROTOCOL_VERSION)
            put("platform", "web")
            put("type", 2)
            put("key", config.token)
        }.toString()
    }

    private fun sendAuthPacket() {
        Logger.d(tag) { "Sending auth packet" }
        sendPacket(
            DanmakuProtocol.Packet(
                version = DanmakuProtocol.PROTO_VER_HEARTBEAT,
                operation = DanmakuProtocol.OP_AUTH,
                body = currentAuthBody.toByteArray(),
            )
        )
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive && isConnected) {
                sendPacket(
                    DanmakuProtocol.Packet(
                        version = DanmakuProtocol.PROTO_VER_HEARTBEAT,
                        operation = DanmakuProtocol.OP_HEARTBEAT,
                        body = "[object Object]".toByteArray(),
                    )
                )
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleReconnect(forceRefresh: Boolean = false) {
        if (forceRefresh) refreshBeforeNextReconnect = true
        if (reconnectJob?.isActive == true) return
        if (!shouldRetryLiveDanmakuTransport(retryCount)) {
            failConnection("Live danmaku reconnect attempts exhausted")
            return
        }

        val expectedLifecycle = currentLifecycleGeneration()
        reconnectJob = scope.launch {
            val delayMs = min(
                BASE_RECONNECT_DELAY_MS * 2.0.pow(retryCount),
                MAX_RECONNECT_DELAY_MS.toDouble(),
            ).toLong()
            _connectionState.value = LiveDanmakuConnectionState.RECONNECTING
            Logger.d(tag) { "Reconnecting in ${delayMs}ms, attempt=${retryCount + 1}" }
            delay(delayMs)
            if (!isActive || released.get() || !isLifecycleCurrent(expectedLifecycle)) return@launch
            retryCount += 1
            prepareReconnectConfig()
            if (!isActive || released.get() || !isLifecycleCurrent(expectedLifecycle)) return@launch
            internalConnect(expectedLifecycle)
        }
    }

    private suspend fun prepareReconnectConfig() {
        val wrappedToFirstEndpoint = advanceEndpointForRetry()
        if (!refreshBeforeNextReconnect && !wrappedToFirstEndpoint) return

        val provider = reconnectConfigProvider ?: run {
            refreshBeforeNextReconnect = false
            return
        }
        val refreshed = try {
            provider()?.normalized()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(tag, "Refresh live danmaku config failed: ${error.message}")
            null
        }
        refreshBeforeNextReconnect = false
        if (refreshed != null && refreshed.endpoints.isNotEmpty()) {
            currentConfig = refreshed
            currentEndpointIndex = 0
            updateAuthBody(refreshed)
            Logger.d(tag) { "Live danmaku config refreshed, endpointCount=${refreshed.endpoints.size}" }
        }
    }

    private fun advanceEndpointForRetry(): Boolean {
        val endpoints = currentConfig?.endpoints.orEmpty()
        if (endpoints.isEmpty()) return true
        currentEndpointIndex = (currentEndpointIndex + 1) % endpoints.size
        return currentEndpointIndex == 0
    }

    private fun sendPacket(packet: DanmakuProtocol.Packet) {
        val bytes = DanmakuProtocol.encode(packet)
        synchronized(socketLock) { webSocket }?.send(ByteString.of(*bytes))
    }

    private fun startDecodeLoop() {
        decodeJob?.cancel()
        decodeJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val frame = incomingFrames.receive()
                if (frame.generation != currentGeneration()) continue
                handleMessage(frame)
            }
        }
    }

    private fun stopDecodeLoop() {
        decodeJob?.cancel()
        decodeJob = null
        incomingFrames.cancel()
    }

    private suspend fun handleMessage(frame: IncomingFrame) {
        try {
            DanmakuProtocol.decode(frame.data).forEach { packet ->
                if (frame.generation != currentGeneration()) return
                when (packet.operation) {
                    DanmakuProtocol.OP_HEARTBEAT_REPLY -> {
                        if (packet.body.size >= 4) {
                            val popularity = ByteBuffer.wrap(packet.body)
                                .order(java.nio.ByteOrder.BIG_ENDIAN)
                                .int
                            Logger.d(tag) { "Popularity: $popularity" }
                        }
                    }
                    DanmakuProtocol.OP_AUTH_REPLY -> {
                        val authCode = runCatching {
                            JSONObject(String(packet.body, Charsets.UTF_8)).optInt("code", -1)
                        }.getOrDefault(-1)
                        if (authCode == 0) {
                            Logger.d(tag) { "Auth success" }
                            authFailureCount = 0
                            retryCount = 0
                            _isConnected.set(true)
                            _connectionState.value = LiveDanmakuConnectionState.AUTHENTICATED
                            startHeartbeat()
                        } else {
                            handleAuthFailure(authCode)
                        }
                    }
                    DanmakuProtocol.OP_MESSAGE -> _messageFlow.tryEmit(packet)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(tag, "Message handling failed: ${error.message}")
        }
    }

    private fun handleAuthFailure(authCode: Int) {
        Log.e(tag, "Auth failed: code=$authCode")
        _isConnected.set(false)
        stopHeartbeat()
        if (authFailureCount < MAX_AUTH_FAILURE_RETRIES) {
            authFailureCount += 1
            scheduleReconnect(forceRefresh = true)
            synchronized(socketLock) { webSocket }?.close(AUTH_FAILED_CLOSURE_CODE, "Auth Failed: $authCode")
        } else {
            failConnection("Live danmaku auth failed: $authCode")
            closeCurrentSocket(force = false, cancelReconnect = true)
        }
    }

    private fun failConnection(message: String) {
        Log.e(tag, message)
        _isConnected.set(false)
        _connectionState.value = LiveDanmakuConnectionState.FAILED
    }

    private fun enqueueMessageFrame(frame: IncomingFrame) {
        if (released.get()) return
        if (!incomingFrames.trySend(frame).isSuccess) {
            Log.w(tag, "Incoming frame dropped due to backpressure")
        }
    }

    private fun onIncomingMessage(generation: Long, bytes: ByteString) {
        enqueueMessageFrame(IncomingFrame(generation = generation, data = bytes.toByteArray()))
    }

    private fun LiveDanmakuConnectionConfig.normalized(): LiveDanmakuConnectionConfig {
        return copy(
            endpoints = endpoints
                .map { endpoint -> LiveDanmakuEndpoint(endpoint.url.trim()) }
                .filter { endpoint -> endpoint.url.isNotBlank() }
                .distinctBy { endpoint -> endpoint.url },
        )
    }

    private companion object {
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val BASE_RECONNECT_DELAY_MS = 1_000.0
        private const val MAX_RECONNECT_DELAY_MS = 10_000L
        private const val MAX_AUTH_FAILURE_RETRIES = 2
        private const val NORMAL_CLOSURE_CODE = 1000
        private const val AUTH_FAILED_CLOSURE_CODE = 4001
    }
}
