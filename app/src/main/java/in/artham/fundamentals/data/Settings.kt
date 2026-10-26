package `in`.artham.fundamentals.data

import android.content.Context
import `in`.artham.fundamentals.llm.EngineKind

/** Small typed wrapper over SharedPreferences: no dependency, survives process death. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("artham_settings", Context.MODE_PRIVATE)

    var engine: EngineKind
        get() = runCatching { EngineKind.valueOf(prefs.getString(KEY_ENGINE, EngineKind.MEDIAPIPE.name) ?: "MEDIAPIPE") }
            .getOrDefault(EngineKind.MEDIAPIPE)
        set(value) = prefs.edit().putString(KEY_ENGINE, value.name).apply()

    /** Base URL of a local llama.cpp / Ollama server, e.g. http://127.0.0.1:8080 */
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "http://127.0.0.1:8080") ?: "http://127.0.0.1:8080"
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value.trim()).apply()

    var serverModel: String
        get() = prefs.getString(KEY_SERVER_MODEL, "gemma3:1b") ?: "gemma3:1b"
        set(value) = prefs.edit().putString(KEY_SERVER_MODEL, value.trim()).apply()

    var maxTokens: Int
        get() = prefs.getInt(KEY_MAX_TOKENS, 512)
        set(value) = prefs.edit().putInt(KEY_MAX_TOKENS, value).apply()

    var temperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, 0.2f)
        set(value) = prefs.edit().putFloat(KEY_TEMPERATURE, value).apply()

    var darkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK, value).apply()

    var demoAcknowledged: Boolean
        get() = prefs.getBoolean(KEY_DEMO_ACK, false)
        set(value) = prefs.edit().putBoolean(KEY_DEMO_ACK, value).apply()

    var disclaimerAccepted: Boolean
        get() = prefs.getBoolean(KEY_DISCLAIMER, false)
        set(value) = prefs.edit().putBoolean(KEY_DISCLAIMER, value).apply()

    private companion object {
        const val KEY_ENGINE = "engine"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_SERVER_MODEL = "server_model"
        const val KEY_MAX_TOKENS = "max_tokens"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_DARK = "dark_theme"
        const val KEY_DEMO_ACK = "demo_ack"
        const val KEY_DISCLAIMER = "disclaimer"
    }
}
