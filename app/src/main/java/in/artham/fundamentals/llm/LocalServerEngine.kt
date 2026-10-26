package `in`.artham.fundamentals.llm

import `in`.artham.fundamentals.core.Logx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to a language-model server the user runs themselves - llama.cpp's server,
 * Ollama, LM Studio, vLLM or anything else exposing an OpenAI-compatible API.
 *
 * The server is normally on the same phone (llama.cpp compiled for Android, or
 * Termux) or on the user's own LAN. Both protocols are supported and detected
 * automatically:
 *   - Ollama        POST {base}/api/generate        (NDJSON stream)
 *   - OpenAI-style  POST {base}/v1/chat/completions (SSE stream)
 */
class LocalServerEngine(
    private val baseUrlProvider: () -> String,
    private val modelProvider: () -> String
) : LlmEngine {

    override val kind: EngineKind = EngineKind.LOCAL_SERVER

    enum class Protocol { OLLAMA, OPENAI, UNKNOWN }

    private fun base(): String = baseUrlProvider().trim().trimEnd('/')

    override suspend fun status(): EngineStatus {
        val base = base()
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            return EngineStatus.NotReady("The server address must start with http:// or https://", "Example: http://127.0.0.1:8080")
        }
        return try {
            when (val protocol = detect()) {
                Protocol.OLLAMA -> {
                    val models = ollamaModels()
                    EngineStatus.Ready(
                        if (models.isEmpty()) "Ollama answered at $base but reported no models."
                        else "Ollama at $base, models: ${models.joinToString(", ").take(120)}"
                    )
                }
                Protocol.OPENAI -> {
                    val models = openAiModels()
                    EngineStatus.Ready(
                        if (models.isEmpty()) "An OpenAI-compatible server answered at $base."
                        else "OpenAI-compatible server at $base, models: ${models.joinToString(", ").take(120)}"
                    )
                }
                Protocol.UNKNOWN -> EngineStatus.NotReady(
                    "Nothing answered at $base.",
                    "Start your local model server (for example: llama-server -m model.gguf --port 8080) and check the address."
                )
            }
        } catch (e: Exception) {
            EngineStatus.NotReady("Could not reach $base: ${e.message}", "Confirm the server is running and that the phone is on the same network.")
        }
    }

    private fun detect(): Protocol {
        val base = base()
        if (httpGet("$base/api/tags") != null) return Protocol.OLLAMA
        if (httpGet("$base/v1/models") != null) return Protocol.OPENAI
        return Protocol.UNKNOWN
    }

    private fun ollamaModels(): List<String> = try {
        val body = httpGet(base() + "/api/tags") ?: return emptyList()
        val arr = JSONObject(body).optJSONArray("models") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() } }
    } catch (e: Exception) {
        emptyList()
    }

    private fun openAiModels(): List<String> = try {
        val body = httpGet(base() + "/v1/models") ?: return emptyList()
        val arr = JSONObject(body).optJSONArray("data") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() } }
    } catch (e: Exception) {
        emptyList()
    }

    override fun generate(system: String, prompt: String, config: LlmConfig): Flow<String> = flow {
        val protocol = detect()
        if (protocol == Protocol.UNKNOWN) {
            throw LlmException("No local model server answered at ${base()}.")
        }
        if (protocol == Protocol.OLLAMA) {
            streamOllama(system, prompt, config).collect { emit(it) }
        } else {
            streamOpenAi(system, prompt, config).collect { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun streamOllama(system: String, prompt: String, config: LlmConfig): Flow<String> = flow {
        val payload = JSONObject().apply {
            put("model", modelProvider())
            put("prompt", prompt)
            put("system", system)
            put("stream", true)
            put("options", JSONObject().apply {
                put("temperature", config.temperature.toDouble())
                put("num_predict", config.maxTokens)
            })
        }
        readStream(base() + "/api/generate", payload.toString()) { line ->
            val obj = runCatching { JSONObject(line) }.getOrNull() ?: return@readStream null
            obj.optString("response").takeIf { it.isNotEmpty() }
        }.collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    private suspend fun streamOpenAi(system: String, prompt: String, config: LlmConfig): Flow<String> = flow {
        val payload = JSONObject().apply {
            put("model", modelProvider())
            put("stream", true)
            put("temperature", config.temperature.toDouble())
            put("max_tokens", config.maxTokens)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", system) })
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
        }
        readStream(base() + "/v1/chat/completions", payload.toString()) { line ->
            val trimmed = line.trim()
            if (!trimmed.startsWith("data:")) return@readStream null
            val data = trimmed.removePrefix("data:").trim()
            if (data == "[DONE]" || data.isEmpty()) return@readStream null
            val obj = runCatching { JSONObject(data) }.getOrNull() ?: return@readStream null
            obj.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("delta")?.optString("content")?.takeIf { it.isNotEmpty() }
        }.collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    private suspend fun readStream(
        url: String,
        body: String,
        parse: (String) -> String?
    ): Flow<String> = flow {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream, application/x-ndjson, application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            if (code >= 400) {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw LlmException("The local server returned HTTP $code. ${err.take(300)}")
            }
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val piece = parse(line)
                    if (!piece.isNullOrEmpty()) emit(piece)
                    line = reader.readLine()
                }
            }
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun httpGet(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 4000
            }
            if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) {
            Logx.w("Llm", "GET $url failed: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    override suspend fun close() { /* stateless: nothing to release */ }
}
