package com.calc.app

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
import java.util.concurrent.CancellationException
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
    private val turns = ArrayList<Array<String>>()

    @Volatile
    private var busy = false

    @Volatile
    private var cancelled = false

    val isLoaded: Boolean
        get() = engine != null

    val isBusy: Boolean
        get() = busy

    @Synchronized
    fun load(modelPath: String, cacheDir: String) {
        if (engine != null && loadedPath == modelPath) return
        release()
        val config = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(threadCount = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 6)),
            cacheDir = cacheDir
        )
        val created = Engine(config)
        runBlocking { created.initialize() }
        engine = created
        loadedPath = modelPath
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
        if (systemPrompt != system) {
            reset()
            system = systemPrompt
        }
        dispatch(prompt, listener, true)
    }

    private fun dispatch(prompt: String, listener: Listener, canRetry: Boolean) {
        if (rawMode) askRaw(prompt, listener, canRetry) else askChat(prompt, listener, canRetry)
    }

    private fun sampler() = SamplerConfig(topK = 10, topP = 0.8, temperature = 0.1)

    @Synchronized
    private fun ensureConversation(current: Engine): Conversation {
        conversation?.let { return it }
        val config = ConversationConfig(
            systemInstruction = if (system.isBlank()) null else Contents.of(system),
            samplerConfig = sampler(),
            maxOutputToken = 320,
            chatTemplate = CHAT_TEMPLATE
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
        if (system.isNotBlank()) {
            sb.append("<|im_start|>system\n").append(system).append("<|im_end|>\n")
        }
        val from = if (turns.size > 4) turns.size - 4 else 0
        for (i in from until turns.size) {
            sb.append("<|im_start|>user\n").append(turns[i][0]).append("<|im_end|>\n")
            sb.append("<|im_start|>assistant\n").append(turns[i][1]).append("<|im_end|>\n")
        }
        sb.append("<|im_start|>user\n").append(prompt).append("<|im_end|>\n")
        sb.append("<|im_start|>assistant\n")
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
                        val clean = response.replace("<|im_end|>", "").replace("<|endoftext|>", "")
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

        @JvmStatic
        fun get(): LocalLlm = instance
    }
}
