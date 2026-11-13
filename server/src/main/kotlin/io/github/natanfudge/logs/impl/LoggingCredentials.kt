package io.github.natanfudge.logs.impl

/**
 * Upstream passed these to loggy to gate the /logs viewer with basic auth. The stand-in
 * has no viewer, so they are accepted for source compatibility and otherwise unused.
 */
class LoggingCredentials(val username: CharArray, val password: CharArray)
