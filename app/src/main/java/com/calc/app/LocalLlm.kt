package com.calc.app

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.ResponseCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Session
import com.google.ai.edge.litertlm.SessionConfig
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

class LocalLlm private constructor() {

    interface Listener {
        fun onToken(text: String)
        fun onDone()
        fun onError(message: String)
    }

    private var engine: Engine? = null
    private var loadedPath: String? = null
    private var conversation: Conversation? = null
    private var activeSession: Session? = null
    private var system: String = ""
    private var rawMode = false
    private var phi = false
    private var qwen3 = false
    private var conf: ModelConf = ModelConf.fallback()
    private var lowPower = false
    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable { releaseIfIdle() }
    private val turns = ArrayList<Array<String>>()
    private val worker = Executors.newSingleThreadExecutor()
    private var usingGpu = false
    private var cachePath = ""

    @Volatile
    private var busy = false

    @Volatile
    private var cancelled = false

    val isLoaded: Boolean
        get() = engine != null

    val isBusy: Boolean
        get() = busy

    val backendName: String
        get() = if (usingGpu) "GPU" else "CPU"

    private fun cpuThreads(): Int {
        if (lowPower) return 2
        if (conf.threads > 0) return conf.threads
        return (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 6)
    }

    private fun buildEngine(path: String, cache: String, gpu: Boolean): Engine {
        val backend = if (gpu) Backend.GPU() else Backend.CPU(threadCount = cpuThreads())
        val created = Engine(EngineConfig(modelPath = path, backend = backend, cacheDir = cache))
        try {
            runBlocking { created.initialize() }
        } catch (t: Throwable) {
            try {
                created.close()
            } catch (ignored: Throwable) {
            }
            throw t
        }
        return created
    }

    @Synchronized
    fun load(modelPath: String, cacheDir: String, modelConf: ModelConf, lowPowerMode: Boolean) {
        cancelIdleRelease()
        if (engine != null && loadedPath == modelPath && lowPower == lowPowerMode) return
        release()
        conf = modelConf
        lowPower = lowPowerMode
        phi = conf.template == "phi"
        qwen3 = conf.template == "qwen3"
        rawMode = false
        cachePath = cacheDir
        var created: Engine? = null
        val failedMark = File(cacheDir, "gpu_failed")
        val tryingMark = File(cacheDir, "gpu_trying")
        if (tryingMark.exists()) {
            tryingMark.delete()
            failedMark.createNewFile()
        }
        if (!failedMark.exists() && !lowPower) {
            try {
                tryingMark.createNewFile()
                created = buildEngine(modelPath, cacheDir, true)
                usingGpu = true
            } catch (t: Throwable) {
                usingGpu = false
                try {
                    failedMark.createNewFile()
                } catch (ignored: Throwable) {
                }
            }
            tryingMark.delete()
        }
        if (created == null) created = buildEngine(modelPath, cacheDir, false)
        engine = created
        loadedPath = modelPath
    }

    @Synchronized
    private fun switchToCpu() {
        val path = loadedPath ?: return
        closeConversation()
        try {
            engine?.close()
        } catch (ignored: Throwable) {
        }
        engine = null
        usingGpu = false
        try {
            File(cachePath, "gpu_failed").createNewFile()
        } catch (ignored: Throwable) {
        }
        engine = buildEngine(path, cachePath, false)
    }

    fun cancel() {
        cancelled = true
        try {
            conversation?.cancelProcess()
        } catch (ignored: Throwable) {
        }
        try {
            activeSession?.cancelProcess()
        } catch (ignored: Throwable) {
        }
    }

    @Synchronized
    fun reset() {
        closeConversation()
        turns.clear()
    }

    fun ask(systemPrompt: String, prompt: String, listener: Listener) {
        cancelIdleRelease()
        if (engine == null) {
            listener.onError("Model is not loaded")
            return
        }
        if (busy) {
            listener.onError("Busy")
            return
        }
        busy = true
        cancelled = false
        worker.execute {
            if (systemPrompt != system) {
                reset()
                system = systemPrompt
            }
            dispatch(prompt, listener, true)
        }
    }

    private fun dispatch(prompt: String, listener: Listener, canRetry: Boolean) {
        if (rawMode) askRaw(prompt, listener, canRetry) else askChat(prompt, listener, canRetry)
    }

    private fun sampler() = SamplerConfig(topK = conf.topK, topP = conf.topP, temperature = conf.temperature)

    @Synchronized
    private fun ensureConversation(current: Engine): Conversation {
        conversation?.let { return it }
        val config = ConversationConfig(
            systemInstruction = if (system.isBlank()) null else Contents.of(system),
            samplerConfig = sampler(),
            maxOutputToken = conf.maxOutputTokens,
            prefillPrefaceOnInit = true,
            chatTemplate = if (phi) PHI_TEMPLATE else if (qwen3) QWEN3_TEMPLATE else CHAT_TEMPLATE
        )
        val created = current.createConversation(config)
        conversation = created
        return created
    }

    private fun askChat(prompt: String, listener: Listener, canRetry: Boolean) {
        val current = engine
        if (current == null) {
            finishError(listener, "Model is not loaded")
            return
        }
        val reply = StringBuilder()
        try {
            val conv = ensureConversation(current)
            conv.sendMessageAsync(prompt, object : MessageCallback {
                override fun onMessage(message: Message) {
                    val text = message.toString()
                    reply.append(text)
                    if (!cancelled) listener.onToken(text)
                }

                override fun onDone() {
                    busy = false
                    listener.onDone()
                }

                override fun onError(throwable: Throwable) {
                    handleChatError(throwable, prompt, listener, canRetry, reply.isEmpty())
                }
            })
        } catch (t: Throwable) {
            handleChatError(t, prompt, listener, canRetry, reply.isEmpty())
        }
    }

    private fun handleChatError(
        t: Throwable,
        prompt: String,
        listener: Listener,
        canRetry: Boolean,
        nothingSent: Boolean
    ) {
        if (t is CancellationException || cancelled) {
            busy = false
            listener.onDone()
            return
        }
        val msg = t.message ?: ""
        val lower = msg.lowercase()
        if (usingGpu && nothingSent && !lower.contains("template")) {
            Thread {
                try {
                    switchToCpu()
                    dispatch(prompt, listener, canRetry)
                } catch (e: Throwable) {
                    finishError(listener, e.message ?: "Error")
                }
            }.start()
            return
        }
        if (canRetry && nothingSent) {
            if (!rawMode && lower.contains("template")) {
                rawMode = true
                closeConversation()
                Thread { dispatch(prompt, listener, false) }.start()
                return
            }
            closeConversation()
            turns.clear()
            Thread { dispatch(prompt, listener, false) }.start()
            return
        }
        finishError(listener, if (msg.isEmpty()) "Error" else msg)
    }

    private fun buildRawPrompt(prompt: String): String {
        val sb = StringBuilder()
        val from = if (turns.size > 4) turns.size - 4 else 0
        if (phi) {
            if (system.isNotBlank()) sb.append("<|system|>").append(system).append("<|end|>")
            for (i in from until turns.size) {
                sb.append("<|user|>").append(turns[i][0]).append("<|end|>")
                sb.append("<|assistant|>").append(turns[i][1]).append("<|end|>")
            }
            sb.append("<|user|>").append(prompt).append("<|end|><|assistant|>")
            return sb.toString()
        }
        if (system.isNotBlank()) {
            sb.append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
        }
        for (i in from until turns.size) {
            sb.append("<|im_start|>user\n").append(turns[i][0]).append("<|im_end|>\n")
            sb.append("<|im_start|>assistant\n").append(turns[i][1]).append("<|im_end|>\n")
        }
        sb.append("<|im_start|>user\n").append(prompt).append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
        if (qwen3) sb.append("<think>\n\n</think>\n\n")
        return sb.toString()
    }

    private fun askRaw(prompt: String, listener: Listener, canRetry: Boolean) {
        val current = engine
        if (current == null) {
            finishError(listener, "Model is not loaded")
            return
        }
        val reply = StringBuilder()
        try {
            closeSession()
            val created = current.createSession(SessionConfig(samplerConfig = sampler()))
            activeSession = created
            created.generateContentStream(
                listOf(InputData.Text(buildRawPrompt(prompt))),
                object : ResponseCallback {
                    override fun onNext(response: String) {
                        val clean = response.replace("<|im_end|>", "").replace("<|end|>", "").replace("<|endoftext|>", "")
                        if (clean.isEmpty()) return
                        reply.append(clean)
                        if (!cancelled) listener.onToken(clean)
                    }

                    override fun onDone() {
                        turns.add(arrayOf(prompt, reply.toString().trim()))
                        busy = false
                        listener.onDone()
                    }

                    override fun onError(throwable: Throwable) {
                        if (throwable is CancellationException || cancelled) {
                            busy = false
                            listener.onDone()
                        } else if (canRetry && reply.isEmpty() && turns.isNotEmpty()) {
                            turns.clear()
                            Thread { askRaw(prompt, listener, false) }.start()
                        } else {
                            finishError(listener, throwable.message ?: "Error")
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            finishError(listener, t.message ?: "Error")
        }
    }

    private fun finishError(listener: Listener, message: String) {
        busy = false
        listener.onError(message)
    }

    fun scheduleIdleRelease(delayMs: Long) {
        idleHandler.removeCallbacks(idleRunnable)
        idleHandler.postDelayed(idleRunnable, delayMs)
    }

    fun cancelIdleRelease() {
        idleHandler.removeCallbacks(idleRunnable)
    }

    fun releaseIfIdle() {
        if (engine == null) return
        if (busy) {
            scheduleIdleRelease(10000)
            return
        }
        worker.execute {
            if (!busy) release()
        }
    }

    @Synchronized
    fun release() {
        closeConversation()
        turns.clear()
        try {
            engine?.close()
        } catch (ignored: Throwable) {
        }
        engine = null
        loadedPath = null
        busy = false
    }

    @Synchronized
    private fun closeConversation() {
        try {
            conversation?.close()
        } catch (ignored: Throwable) {
        }
        conversation = null
        closeSession()
    }

    private fun closeSession() {
        try {
            activeSession?.close()
        } catch (ignored: Throwable) {
        }
        activeSession = null
    }

    companion object {
        private val instance = LocalLlm()

        private val CHAT_TEMPLATE = """
{%- for message in messages -%}
<|im_start|>{{ message['role'] }}
{% if message['content'] is string -%}
{{ message['content'] }}
{%- else -%}
{%- for part in message['content'] -%}{%- if part['type'] == 'text' -%}{{ part['text'] }}{%- endif -%}{%- endfor -%}
{%- endif -%}
<|im_end|>
{% endfor -%}
{%- if add_generation_prompt -%}
<|im_start|>assistant
{% endif -%}
""".trimStart()

        private val QWEN3_TEMPLATE = CHAT_TEMPLATE.replace(
            "<|im_start|>assistant\n{% endif -%}",
            "<|im_start|>assistant\n<think>\n\n</think>\n\n{% endif -%}"
        )

        private val PHI_TEMPLATE = """
{%- for message in messages -%}
<|{{ message['role'] }}|>
{%- if message['content'] is string -%}
{{ message['content'] }}
{%- else -%}
{%- for part in message['content'] -%}{%- if part['type'] == 'text' -%}{{ part['text'] }}{%- endif -%}{%- endfor -%}
{%- endif -%}
<|end|>
{%- endfor -%}
{%- if add_generation_prompt -%}<|assistant|>{%- endif -%}
""".trimStart()

        @JvmStatic
        fun cacheDirFor(ctx: Context): String {
            val d = File(ctx.filesDir, "llm_cache")
            if (!d.exists()) d.mkdirs()
            return d.absolutePath
        }

        @JvmStatic
        fun hasEnoughMemory(ctx: Context, requiredMb: Long): Boolean {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return true
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            return info.availMem >= requiredMb * 1024L * 1024L
        }

        @JvmStatic
        fun totalMemoryMb(ctx: Context): Long {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            return info.totalMem / (1024L * 1024L)
        }

        @JvmStatic
        fun get(): LocalLlm = instance
    }
}
