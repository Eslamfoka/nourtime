package com.nourtime.app.data.apps

/**
 * Browsers open YouTube and every other site, so limiting only the apps leaves the same content a tap
 * away. Found the same way on every phone: the apps that open an ordinary web link (`ACTION_VIEW` of
 * an http(s) address, `CATEGORY_BROWSABLE`), whoever made the phone or the browser.
 */
object Browsers {
    /**
     * The browsers among the apps the child can open from the launcher; [resolved] are the packages
     * that answered the web-link query (an app may answer for both http and https).
     */
    fun among(resolved: Collection<String>, launchable: Collection<String>, ownPackage: String): Set<String> {
        val apps = launchable.toSet()
        return resolved.filterTo(linkedSetOf()) { it in apps && it != ownPackage }
    }

    /**
     * Browsers the "Limit browsers" action would limit: not limited yet, and not one the parent chose to
     * allow during the lock (that choice is theirs to undo, app by app).
     */
    fun unlimited(browsers: Set<String>, limited: Set<String>, allowedDuringLock: Set<String>): Set<String> =
        browsers - limited - allowedDuringLock
}
