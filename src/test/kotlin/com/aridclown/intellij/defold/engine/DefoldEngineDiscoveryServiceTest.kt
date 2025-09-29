package com.aridclown.intellij.defold.engine

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefoldEngineDiscoveryServiceTest {

    private lateinit var service: DefoldEngineDiscoveryService

    @BeforeEach
    fun setUp() {
        service = DefoldEngineDiscoveryService()
    }

    @Test
    fun `captures dynamic port and address from engine logs`() {
        service.recordLogLine("INFO:DLIB: Log server started on port 49245")
        service.recordLogLine("INFO:ENGINE: Engine service started on port 49246")
        service.recordLogLine("INFO:ENGINE: Target listening with name: Host.local - 192.168.0.51 - Darwin")

        val endpoint = service.currentEndpoint()

        assertThat(endpoint).isNotNull
        assertThat(endpoint!!.port).isEqualTo(49246)
        assertThat(endpoint.address).isEqualTo("192.168.0.51")
        assertThat(endpoint.logPort).isEqualTo(49245)
    }

    @Test
    fun `falls back to localhost when address not present`() {
        service.recordLogLine("INFO:ENGINE: Engine service started on port 8001")

        val endpoint = service.currentEndpoint()

        assertThat(endpoint).isNotNull
        assertThat(endpoint!!.address).isEqualTo("127.0.0.1")
        assertThat(endpoint.port).isEqualTo(8001)
    }
}
