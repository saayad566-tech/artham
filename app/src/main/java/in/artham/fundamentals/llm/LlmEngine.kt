package `in`.artham.fundamentals.llm

import kotlinx.coroutines.flow.Flow

enum class EngineKind(val label: String) {
    MEDIAPIPE("On-device (MediaPipe / LiteRT)"),
    LOCAL_SERVER("Local server (llama.cpp / Ollama)")
}

data class LlmConfig(val maxTokens: Int = 512, val temperature: Float = 0.2f)

sealed interface EngineStatus {
    data class Ready(val detail: String) : EngineStatus
    data class NotReady(val reason: String, val hint: String) : EngineStatus
}

/**
 * A local text-generation engine. Implementations must never contact a hosted
 * model: [MediaPipeEngine] runs a model file from the app's own storage, and
 * [LocalServerEngine] talks to a server the user runs on their own device or LAN.
 */
interface LlmEngine {
    val kind: EngineKind

    suspend fun status(): EngineStatus

    /**
     * Streams generated text. Emissions are incremental: each item is the next
     * piece of the answer, not the whole answer repeated.
     */
    fun generate(system: String, prompt: String, config: LlmConfig): Flow<String>

    suspend fun close()
}

class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)
