package com.nourtime.app.data.apps

data class NewAppsResult(val toLimit: Set<String>, val known: Set<String>)

/**
 * Apps installed after protection started share the time budget unless the parent says otherwise, so
 * a newly installed game or browser isn't unlimited.
 */
object NewApps {
    /**
     * [known] is null the first time: the apps already there are only remembered. [exempt] are the
     * phone, Settings and home screen apps, which are never limited.
     */
    fun check(
        known: Set<String>?,
        launchable: Set<String>,
        limitNewApps: Boolean,
        limited: Set<String>,
        allowed: Set<String>,
        exempt: Set<String>,
    ): NewAppsResult {
        if (known == null || !limitNewApps) return NewAppsResult(emptySet(), launchable)
        val toLimit = launchable - known - limited - allowed - exempt
        return NewAppsResult(toLimit, launchable)
    }
}
