package `in`.artham.fundamentals.llm

import android.content.Context
import `in`.artham.fundamentals.data.ModelPackDao
import `in`.artham.fundamentals.data.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Owns the engine instances and resolves which model file each engine should use.
 * The active engine is a user setting, so nothing here assumes a cloud provider.
 */
class LlmManager(
    private val context: Context,
    private val settings: Settings,
    private val packDao: ModelPackDao
) {

    private val mediaPipe: MediaPipeEngine by lazy {
        MediaPipeEngine(context) { packDao.active(EngineKind.MEDIAPIPE.name)?.path }
    }

    private val localServer: LocalServerEngine by lazy {
        LocalServerEngine({ settings.serverUrl }, { settings.serverModel })
    }

    fun engine(kind: EngineKind = settings.engine): LlmEngine = when (kind) {
        EngineKind.MEDIAPIPE -> mediaPipe
        EngineKind.LOCAL_SERVER -> localServer
    }

    fun config(): LlmConfig = LlmConfig(settings.maxTokens, settings.temperature)

    suspend fun status(kind: EngineKind = settings.engine): EngineStatus = engine(kind).status()

    fun nlToQuery(): NlToQuery = NlToQuery { engine() }

    /**
     * Generates text, turning an engine failure into a single readable error
     * emission instead of crashing the screen.
     */
    fun generate(system: String, prompt: String): Flow<String> = flow {
        val status = engine().status()
        if (status is EngineStatus.NotReady) {
            throw LlmException("${status.reason} ${status.hint}")
        }
        engine().generate(system, prompt, config()).collect { emit(it) }
    }

    suspend fun shutdown() {
        mediaPipe.close()
        localServer.close()
    }
}
