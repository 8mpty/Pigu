package moe.shizuku.manager.home

enum class StartRoute {
    USB_DEBUGGING_OFF,
    WIRELESS_DEBUGGING_UNAVAILABLE,
    DISCOVER,
    STOP_TCP_AND_DISCOVER,
    DIRECT_PORT,
}

/**
 * Pure decision for the wireless start button, extracted so every branch can be tested without
 * Android or a running service.
 */
object StartRoutePolicy {

    fun decide(
        adbEnabled: Int,
        tcpPort: Int,
        tlsSupported: Boolean,
        tcpMode: Boolean,
    ): StartRoute {
        if (adbEnabled == 0) return StartRoute.USB_DEBUGGING_OFF
        if (tcpPort <= 0) {
            return if (tlsSupported) StartRoute.DISCOVER else StartRoute.WIRELESS_DEBUGGING_UNAVAILABLE
        }
        return if (tcpMode) StartRoute.DIRECT_PORT else StartRoute.STOP_TCP_AND_DISCOVER
    }
}
