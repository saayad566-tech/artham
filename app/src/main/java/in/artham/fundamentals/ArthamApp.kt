package `in`.artham.fundamentals

import android.app.Application
import android.content.Context
import `in`.artham.fundamentals.core.Logx
import `in`.artham.fundamentals.data.AiDao
import `in`.artham.fundamentals.data.ArthamDb
import `in`.artham.fundamentals.data.AlertDao
import `in`.artham.fundamentals.data.CompanyDao
import `in`.artham.fundamentals.data.DocumentDao
import `in`.artham.fundamentals.data.DemoData
import `in`.artham.fundamentals.data.ImportDao
import `in`.artham.fundamentals.data.LogDao
import `in`.artham.fundamentals.data.ModelPackDao
import `in`.artham.fundamentals.data.ScreenDao
import `in`.artham.fundamentals.data.PortfolioDao
import `in`.artham.fundamentals.data.Settings
import `in`.artham.fundamentals.data.WatchlistDao
import `in`.artham.fundamentals.data.import.ImportService
import `in`.artham.fundamentals.domain.ScreenEngine
import `in`.artham.fundamentals.llm.LlmManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Single place where the app's collaborators are constructed. Small enough that a
 * dependency-injection framework would be overhead, explicit enough that every
 * wiring decision is visible in one file.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val db: ArthamDb = ArthamDb.get(appContext)
    val companyDao = CompanyDao(db)
    val watchlistDao = WatchlistDao(db)
    val screenDao = ScreenDao(db)
    val aiDao = AiDao(db)
    val modelPackDao = ModelPackDao(db)
    val logDao = LogDao(db)
    val importDao = ImportDao(db)
    val portfolioDao = PortfolioDao(db)
    val documentDao = DocumentDao(db)
    val alertDao = AlertDao(db)
    val settings = Settings(appContext)
    val screenEngine = ScreenEngine()
    val importService = ImportService(companyDao, importDao)
    val llm = LlmManager(appContext, settings, modelPackDao)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** First run: make the app immediately usable with the labelled demo dataset. */
    fun bootstrap() {
        scope.launch {
            try {
                if (companyDao.stats().companies == 0) {
                    Logx.i("Boot", "Empty database; loading the labelled demo dataset")
                    val (companies, rows) = DemoData.load(companyDao, importDao)
                    logDao.append("INFO", "Boot", "Loaded demo dataset: $companies companies, $rows annual rows")
                    if (!settings.demoAcknowledged) settings.demoAcknowledged = true
                }
                watchlistDao.ensureDefault()
                portfolioDao.ensureDefault()
            } catch (e: Exception) {
                Logx.e("Boot", "Bootstrap failed", e)
                logDao.append("ERROR", "Boot", "Bootstrap failed: ${e.message}")
            }
        }
    }
}

class ArthamApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.bootstrap()
    }
}

/** Convenience accessor for composables and view models. */
fun Context.artham(): AppContainer = (applicationContext as ArthamApp).container
