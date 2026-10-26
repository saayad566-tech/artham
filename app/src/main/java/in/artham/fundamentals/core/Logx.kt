package `in`.artham.fundamentals.core

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plain-English activity log. Every user-visible operation is recorded so the
 * app can explain what it did, on device, without any telemetry.
 */
object Logx {
    private const val TAG = "Artham"
    private val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
    private val buffer = ArrayDeque<String>()
    private const val CAP = 400

    fun i(tag: String, message: String) = record("INFO", tag, message)
    fun w(tag: String, message: String) = record("WARN", tag, message)
    fun e(tag: String, message: String, t: Throwable? = null) {
        record("ERROR", tag, message + (t?.let { " :: ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
        if (t != null) Log.e(TAG, message, t) else Log.e(TAG, message)
    }

    @Synchronized
    private fun record(level: String, tag: String, message: String) {
        val line = "${stamp.format(Date())} [$level] $tag: $message"
        buffer.addLast(line)
        while (buffer.size > CAP) buffer.removeFirst()
        Log.println(if (level == "ERROR") Log.ERROR else Log.INFO, TAG, line)
    }

    @Synchronized
    fun snapshot(): List<String> = buffer.toList()

    @Synchronized
    fun clear() = buffer.clear()
}
