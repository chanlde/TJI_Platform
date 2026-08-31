package com.tji.device.product.firebucket.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import kotlinx.coroutines.CancellationException

class DirectLinkGatewayResolverTest {
    @Test
    fun selectsIpv4GatewayFromEsp32Subnet() {
        val gateway = selectIpv4DefaultGateway(
            listOf(defaultRoute("192.168.4.1"))
        )

        assertEquals(InetAddress.getByName("192.168.4.1"), gateway)
    }

    @Test
    fun supportsDifferentIpv4SubnetWithoutFixedHost() {
        val gateway = selectIpv4DefaultGateway(
            listOf(defaultRoute("10.20.30.1"))
        )

        assertEquals(InetAddress.getByName("10.20.30.1"), gateway)
    }

    @Test
    fun ignoresIpv6AndNonDefaultRoutes() {
        val gateway = selectIpv4DefaultGateway(
            listOf(
                defaultRoute("fe80::1"),
                GatewayRouteCandidate(
                    isDefaultRoute = false,
                    gateway = InetAddress.getByName("192.168.8.2")
                ),
                defaultRoute("192.168.8.1")
            )
        )

        assertEquals(InetAddress.getByName("192.168.8.1"), gateway)
    }

    @Test
    fun returnsNullWhenWifiProvidesNoDefaultGateway() {
        val gateway = selectIpv4DefaultGateway(
            listOf(GatewayRouteCandidate(isDefaultRoute = false, gateway = null))
        )

        assertNull(gateway)
    }

    @Test
    fun usesConfiguredDirectLinkServerPort() {
        val address = directLinkSocketAddress(
            gateway = InetAddress.getByName("192.168.4.1"),
            serverPort = 19010
        )

        assertEquals("192.168.4.1", address.address.hostAddress)
        assertEquals(19010, address.port)
    }

    @Test
    fun commandTimeoutDoesNotDisconnectTheLongLivedSocket() {
        assertFalse(shouldCloseDirectLinkSocket(CancellationException("ACK timeout")))
        assertTrue(shouldCloseDirectLinkSocket(IOException("socket write failed")))
    }

    private fun defaultRoute(gateway: String): GatewayRouteCandidate = GatewayRouteCandidate(
        isDefaultRoute = true,
        gateway = InetAddress.getByName(gateway)
    )
}
