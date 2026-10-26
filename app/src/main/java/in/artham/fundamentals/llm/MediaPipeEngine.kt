package `in`.artham.fundamentals.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import `in`.artham.fundamentals.core.Logx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Runs a quantised model entirely on the device through MediaPipe's LLM Inference
 * task (LiteRT under the hood). No network call is made and no token is spent.
 *
 * The model file is supplied by the user and copied into app-private storage; the
 * app never downloads a model on its own. Accepted formats are the ones the
 * runtime understands: .task and .litertlm bundles.
 */
class MediaPipeEngine(
    private val context: Context,
    private val modelPathProvider: () -> String?
) : LlmEngine {

    override val kind: EngineKind = EngineKind.MEDIAPIPE

    @Volatile
    private var inference: LlmInference? = null

    @Volatile
    private var loadedPath: String? = null

    @Volatile
    private var lastError: String? = null

    override suspend fun status(): EngineStatus {
        val path = modelPathProvider()
        if (path.isNullOrBlank()) {
            return EngineStatus.NotReady(
                "No model file has been imported.",
                "Open Models, import a .task or .litertlm model you have downloaded, then return here."
            )
        }
        val file = File(path)
        if (!file.exists()) {
            return EngineStatus.NotReady("The recorded model file is missing from storage.", "Re-import the model from the Models screen.")
        }
        if (file.length() < 10L * 1024 * 1024) {
            return EngineStatus.NotReady(
                "The model file is only ${file.length() / 1024} KB, which is too small to be a language model.",
                "The import may have copied an incomplete file. Import the model again."
            )
        }
        val engine = ensureLoaded(path)
            ?: return EngineStatus.NotReady(
                "The model could not be initialised: ${lastError ?: "unknown reason"}",
                "Check that the file is a valid MediaPipe .task / .litertlm bundle for this device."
            )
        return EngineStatus.Ready("Loaded ${file.name} (${file.length() / (1024 * 1024)} MB) via MediaPipe.")
    }

    private fun ensureLoaded(path: String): LlmInference? {
        val current = inference
        if (current != null && loadedPath == path) return current
        synchronized(this) {
            val again = inference
            if (again != null && loadedPath == path) return again
            return try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(path)
                    .setMaxTokens(1024)
                    .setMaxTopK(40)
                    .build()
                val created = LlmInference.createFromOptions(context, options)
                inference = created
                loadedPath = path
                lastError = null
                Logx.i("Llm", "MediaPipe model loaded from $path")
                created
            } catch (e: Throwable) {
                lastError = "${e.javaClass.simpleName}: ${e.message}"
                Logx.e("Llm", "MediaPipe model failed to load", e)
                null
            }
        }
    }

    override fun generate(system: String, prompt: String, config: LlmConfig): Flow<String> = flow {
        val path = modelPathProvider()
            ?: throw LlmException("No model imported. Open Models and import a model file first.")
        val engine = ensureLoaded(path)
            ?: throw LlmException("The model could not be initialised: ${lastError ?: "unknown reason"}")
        // MediaPipe's blocking call returns the complete answer. It is emitted as a
        // single chunk, which is honest: this engine does not stream token by token.
        val full = buildString {
            append(system.trim())
            append("\n\n")
            append(prompt.trim())
            append("\n")
        }
        val answer = try {
            engine.generateResponse(full)
        } catch (e: Throwable) {
            throw LlmException("Generation failed: ${e.javaClass.simpleName}: ${e.message}", e)
        }
        emit(answer)
    }.flowOn(Dispatchers.Default)

    override suspend fun close() {
        synchronized(this) {
            try {
                inference?.close()
            } catch (e: Throwable) {
                Logx.w("Llm", "Closing MediaPipe engine failed: ${e.message}")
            }
            inference = null
            loadedPath = null
        }
    }
}
