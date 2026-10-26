package `in`.artham.fundamentals.llm

import android.content.Context
import android.net.Uri
import `in`.artham.fundamentals.core.Logx
import java.io.File
import java.security.MessageDigest

/**
 * A model the user can run. The app ships no model weights: they are large, they
 * carry their own licences, and downloading them silently is not something a
 * financial tool should do. [reference] points to the official place to obtain the
 * file; the import flow then copies it into app-private storage.
 */
data class ModelPack(
    val id: String,
    val name: String,
    val engine: EngineKind,
    val format: String,
    val approxSize: String,
    val licence: String,
    val reference: String,
    val note: String
)

object ModelPacks {

    val catalog: List<ModelPack> = listOf(
        ModelPack(
            id = "gemma3_1b_litertlm",
            name = "Gemma 3 1B (LiteRT bundle)",
            engine = EngineKind.MEDIAPIPE,
            format = ".litertlm / .task",
            approxSize = "~550 MB",
            licence = "Gemma Terms of Use (accept on download)",
            reference = "https://ai.google.dev/gemma/docs/gemma-3-on-device",
            note = "Smallest Gemma that produces usable analysis on a mid-range phone. Best default for on-device use."
        ),
        ModelPack(
            id = "gemma2_2b_task",
            name = "Gemma 2 2B (MediaPipe task)",
            engine = EngineKind.MEDIAPIPE,
            format = ".task",
            approxSize = "~1.3 GB",
            licence = "Gemma Terms of Use (accept on download)",
            reference = "https://ai.google.dev/gemma/docs/mediapipe",
            note = "Better reasoning than 1B, needs roughly 3 GB of free RAM."
        ),
        ModelPack(
            id = "phi3_mini",
            name = "Phi-3 Mini 4K (MediaPipe task)",
            engine = EngineKind.MEDIAPIPE,
            format = ".task",
            approxSize = "~2.4 GB",
            licence = "MIT (Microsoft)",
            reference = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct",
            note = "Strong at structured reasoning; needs a flagship device."
        ),
        ModelPack(
            id = "gguf_any",
            name = "Any GGUF via a local llama.cpp server",
            engine = EngineKind.LOCAL_SERVER,
            format = ".gguf served over HTTP",
            approxSize = "300 MB - 4 GB",
            licence = "Depends on the model you choose",
            reference = "https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md",
            note = "Run llama-server on the phone or on your LAN and point Artham at it. No file import needed."
        )
    )

    /** Directory where imported models live, inside app-private storage. */
    fun modelsDir(context: Context): File = File(context.filesDir, "models").apply { mkdirs() }

    /**
     * Copies a user-chosen model into private storage, verifying that a real file
     * arrived and computing a checksum so the import can be audited later.
     */
    fun importFrom(context: Context, uri: Uri, displayName: String): Result<ImportedModel> = runCatching {
        val dir = modelsDir(context)
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "model.bin" }
        val target = File(dir, safeName)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("The selected file could not be opened.")
        input.use { source ->
            target.outputStream().use { sink -> source.copyTo(sink, bufferSize = 1 shl 20) }
        }
        if (!target.exists() || target.length() < 1024L) {
            target.delete()
            throw IllegalStateException("The copied file was empty. Try importing again.")
        }
        val digest = sha256(target)
        Logx.i("Models", "Imported ${target.name} (${target.length()} bytes, sha256=${digest.take(16)}...)")
        ImportedModel(target.name, target.absolutePath, target.length(), digest)
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    data class ImportedModel(val name: String, val path: String, val sizeBytes: Long, val sha256: String)
}
