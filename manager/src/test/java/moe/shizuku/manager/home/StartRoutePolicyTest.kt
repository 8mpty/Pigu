package moe.shizuku.manager.home

import org.junit.Assert.assertEquals
import org.junit.Test

class StartRoutePolicyTest {

    @Test
    fun usbDebuggingOffWinsFirst() {
        assertEquals(
            StartRoute.USB_DEBUGGING_OFF,
            StartRoutePolicy.decide(adbEnabled = 0, tcpPort = 5555, tlsSupported = true, tcpMode = true),
        )
    }

    @Test
    fun noTcpPortAndNoTlsIsUnavailable() {
        assertEquals(
            StartRoute.WIRELESS_DEBUGGING_UNAVAILABLE,
            StartRoutePolicy.decide(adbEnabled = 1, tcpPort = -1, tlsSupported = false, tcpMode = true),
        )
        assertEquals(
            StartRoute.WIRELESS_DEBUGGING_UNAVAILABLE,
            StartRoutePolicy.decide(adbEnabled = 1, tcpPort = 0, tlsSupported = false, tcpMode = false),
        )
    }

    @Test
    fun noTcpPortWithTlsDiscovers() {
        assertEquals(
            StartRoute.DISCOVER,
            StartRoutePolicy.decide(adbEnabled = 1, tcpPort = 0, tlsSupported = true, tcpMode = true),
        )
    }

    @Test
    fun tcpPortIsUsedDirectlyWhenTcpModeIsOn() {
        assertEquals(
            StartRoute.DIRECT_PORT,
            StartRoutePolicy.decide(adbEnabled = 1, tcpPort = 5555, tlsSupported = true, tcpMode = true),
        )
        assertEquals(
            StartRoute.DIRECT_PORT,
            StartRoutePolicy.decide(adbEnabled = 1, tcpPort = 5555, tlsSupported = false, tcpMode = true),
        )
    }

    @Test
    fun tcpPortIsStoppedBeforeDiscoveryWhenTcpModeIsOff() {
        assertEquals(
            StartRoute.STOP_TCP_AND_DISCOVER,
            StartRoutePolicy.decide(adbEnabled = 1, tcpPort = 5555, tlsSupported = true, tcpMode = false),
        )
    }
}
