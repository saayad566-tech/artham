package `in`.artham.fundamentals.data.net

import `in`.artham.fundamentals.core.Logx
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

/**
 * One small HTTP client for the whole app.
 *
 * Deliberately conservative: a browser user agent (the public endpoints reject the default
 * Java one), gzip, hard timeouts, a single retry with backoff, and a global minimum
 * interval between requests so a bulk sync cannot turn into a flood. No request is ever
 * made without the user starting a sync.
 */
object Http {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    /** Minimum gap between two requests, enforced globally across the process. */
    @Volatile
    var minIntervalMillis: Long = 400L

    private val lastRequestAt = AtomicLong(0L)

    sealed interface Result {
        data class Ok(val body: String) : Result
        data class Failed(val message: String, val httpCode: Int = 0) : Result
    }

    private fun throttle() {
        val now = System.currentTimeMillis()
        val last = lastRequestAt.get()
        val wait = minIntervalMillis - (now - last)
        if (wait > 0) {
            try {
                Thread.sleep(wait)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        lastRequestAt.set(System.currentTimeMillis())
    }

    fun get(url: String, accept: String = "application/json,text/plain,*/*", timeoutMs: Int = 15_000): Result {
        var attempt = 0
        var lastError = "unknown error"
        while (attempt < 2) {
            attempt++
            throttle()
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", accept)
                    setRequestProperty("Accept-Encoding", "gzip")
                    setRequestProperty("Accept-Language", "en-IN,en;q=0.9")
                    instanceFollowRedirects = true
                }
                val code = connection.responseCode
                if (code in 200..299) {
                    val encoding = connection.contentEncoding
                    val stream = connection.inputStream
                    val body = if (encoding != null && encoding.contains("gzip", true)) {
                        java.util.zip.GZIPInputStream(stream).bufferedReader().use { it.readText() }
                    } else {
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                    }
                    return Result.Ok(body)
                }
                if (code == 429 || code >= 500) {
                    lastError = "HTTP $code"
                    Thread.sleep(600L * attempt)
                    continue
                }
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Logx.w("Http", "GET $url -> HTTP $code ${errorBody.take(120)}")
                return Result.Failed("HTTP $code", code)
            } catch (e: Exception) {
                lastError = "${e.javaClass.simpleName}: ${e.message}"
                Logx.w("Http", "GET $url failed (attempt $attempt): $lastError")
                if (attempt < 2) {
                    try {
                        Thread.sleep(500L * attempt)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            } finally {
                connection?.disconnect()
            }
        }
        return Result.Failed(lastError)
    }
}
