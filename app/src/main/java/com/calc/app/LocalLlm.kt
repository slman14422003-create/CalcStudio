package com.calc.app

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
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
            backend = Backend.CPU(),
            cacheDir = cacheDir
        )
        val created = Engine(config)
        runBlocking { created.initialize() }
        engine = created
        loadedPath = modelPath
    }

    fun cancel() {
        cancelled = true
    }

    fun ask(system: String, prompt: String, listener: Listener) {
        val current = engine
        if (current == null) {
            listener.onError("Model is not loaded")
            return
        }
        if (busy) {
            listener.onError("Busy")
            return
        }
        busy = true
        cancelled = false
        try {
            closeConversation()
            val created = current.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(system),
                    samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = 0.2)
                )
            )
            conversation = created
            created.sendMessageAsync(prompt, object : MessageCallback {
                override fun onMessage(message: Message) {
                    if (!cancelled) listener.onToken(message.toString())
                }

                override fun onDone() {
                    busy = false
                    listener.onDone()
                }

                override fun onError(throwable: Throwable) {
                    busy = false
                    listener.onError(throwable.message ?: "Error")
                }
            })
        } catch (t: Throwable) {
            busy = false
            listener.onError(t.message ?: "Error")
        }
    }

    @Synchronized
    fun release() {
        closeConversation()
        try {
            engine?.close()
        } catch (ignored: Throwable) {
        }
        engine = null
        loadedPath = null
        busy = false
    }

    private fun closeConversation() {
        try {
            conversation?.close()
        } catch (ignored: Throwable) {
        }
        conversation = null
    }

    companion object {
        private val instance = LocalLlm()

        @JvmStatic
        fun get(): LocalLlm = instance
    }
}
