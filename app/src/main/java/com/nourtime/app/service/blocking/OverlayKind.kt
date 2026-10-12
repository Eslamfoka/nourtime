package com.nourtime.app.service.blocking

/** The "Time's up" window's type: an accessibility overlay when Accessibility is on, else a regular one. */
enum class OverlayKind {
    /** Drawn above the notifications; needs the connected Accessibility service. */
    ACCESSIBILITY,

    /** Needs "Display over other apps"; drawn below the notification shade. */
    APP,
    ;

    companion object {
        /**
         * In order of preference. Android can refuse the accessibility overlay while the service
         * reconnects ("token null is not valid", seen for about a minute), so the regular one follows.
         */
        fun attempts(accessibilityAvailable: Boolean): List<OverlayKind> =
            if (accessibilityAvailable) listOf(ACCESSIBILITY, APP) else listOf(APP)
    }
}
