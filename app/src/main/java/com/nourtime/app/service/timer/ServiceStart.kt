package com.nourtime.app.service.timer

/**
 * How [TimerService] is started. A plain start comes first: startForegroundService() gives the service a
 * few seconds to call startForeground(), and a phone too slow for that crashed. Android refuses plain
 * starts while the app is in the background (IllegalStateException, e.g. at boot), and only then the
 * foreground start is used.
 */
internal object ServiceStart {
    fun start(plain: () -> Unit, foreground: () -> Unit): Result<Unit> =
        runCatching(plain).recoverCatching { e ->
            if (e is IllegalStateException) foreground() else throw e
        }
}
