package com.ronybrand.estados.core

import kotlinx.coroutines.CancellationException

/**
 * Runs [block] and returns what it produced or what it threw. Unlike `runCatching`, a cancelled coroutine stays
 * cancelled: catching [CancellationException] would keep a screen that was left alive. This is the one place where the
 * app deliberately catches every exception, because any failure of a call is shown to the user the same way.
 */
suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    Result.failure(e)
}
