package io.github.natanfudge.logs

import io.github.natanfudge.logs.impl.LoggingCredentials
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.Collections
import kotlin.io.path.*

/**
 * Stand-in for Fudge's `loggy` library.
 *
 * Crashy calls into loggy for per-request logging and for the web log viewer at /logs, but
 * loggy was never published to a public repository: the build declares no repository that
 * carries it, so `io.github.natanfudge:loggy:0.3.5` only ever resolved from the author's
 * mavenLocal. This keeps the call sites upstream uses and writes plain text logs to disk.
 */
object Loggy {
    fun create(
        logToConsole: Boolean,
        logsDir: Path,
        @Suppress("UNUSED_PARAMETER") credentials: LoggingCredentials? = null
    ): CrashyLogger = CrashyLogger(logToConsole, logsDir)
}

class CrashyLogger internal constructor(
    private val logToConsole: Boolean,
    private val logsDir: Path
) {
    /** Upstream mounts the /logs viewer here. The stand-in has no viewer, so this is setup only. */
    fun install() {
        logsDir.createDirectories()
    }

    /** Upstream mounts the /logs routes here. Nothing to mount. */
    fun route() = Unit

    suspend fun <T> startSuspend(name: String, body: suspend LogContext.() -> T): T {
        val context = LogContext(name, logToConsole)
        return record(context) { context.body() }
    }

    suspend fun <T> startSuspendWithContextAsParam(name: String, body: suspend (LogContext) -> T): T {
        val context = LogContext(name, logToConsole)
        return record(context) { body(context) }
    }

    private suspend fun <T> record(context: LogContext, body: suspend () -> T): T {
        try {
            return body()
        } catch (e: Throwable) {
            context.logError(e) { "Unhandled error in '${context.name}'" }
            throw e
        } finally {
            context.flushTo(logsDir, logToConsole)
        }
    }
}

class LogContext internal constructor(
    val name: String,
    private val logToConsole: Boolean = false
) {
    private val startTime = Instant.now()
    private val lines: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())

    fun logInfo(message: () -> String) = add("INFO", message)
    fun logWarn(message: () -> String) = add("WARN", message)
    fun logData(key: String, value: () -> Any?) = add("DATA") { "$key = ${value()}" }
    fun logError(exception: Throwable, message: () -> String) = add("ERROR") { "${message()}: $exception" }
    fun logError(message: () -> String) = add("ERROR", message)

    private fun add(severity: String, message: () -> String) {
        lines.add("${Instant.now()} $severity ${message()}")
    }

    internal fun flushTo(logsDir: Path, logToConsole: Boolean) {
        val rendered = buildString {
            appendLine("$name started at $startTime")
            lines.forEach { appendLine(it) }
        }
        if (logToConsole) print(rendered)
        val file = logsDir.resolve(name.trim('/').replace('/', '_').ifEmpty { "root" } + ".log")
        // The schedule task runs on a timer that fires before install() has made the directory,
        // so create it here and retry once rather than losing the entry.
        repeat(2) { attempt ->
            try {
                logsDir.createDirectories()
                Files.writeString(file, rendered, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                return
            } catch (e: Exception) {
                if (attempt == 1) System.err.println("Could not write log for '$name': $e")
            }
        }
    }
}
