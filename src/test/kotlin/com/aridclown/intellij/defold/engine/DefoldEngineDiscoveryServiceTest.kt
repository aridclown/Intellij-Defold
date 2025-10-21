package com.aridclown.intellij.defold.engine

import com.intellij.execution.process.OSProcessHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefoldEngineDiscoveryServiceTest {

    private lateinit var service: DefoldEngineDiscoveryService
    private lateinit var handler: OSProcessHandler

    @BeforeEach
    fun setUp() {
        service = DefoldEngineDiscoveryService()
        handler = mockk(relaxed = true)
        every { handler.addProcessListener(any()) } returns Unit
        every { handler.isProcessTerminating } returns false
        every { handler.isProcessTerminated } returns false
    }

    @Test
    fun `captures dynamic port and address from engine logs`() {
        service.recordLogLine(handler, "INFO:DLIB: Log server started on port 49245")
        service.recordLogLine(handler, "INFO:ENGINE: Engine service started on port 49246")
        service.recordLogLine(handler, "INFO:ENGINE: Target listening with name: Host.local - 192.168.0.51 - Darwin")

        val endpoint = service.currentEndpoint()

        assertThat(endpoint).isNotNull
        assertThat(endpoint!!.port).isEqualTo(49246)
        assertThat(endpoint.address).isEqualTo("192.168.0.51")
        assertThat(endpoint.logPort).isEqualTo(49245)
    }

    @Test
    fun `falls back to localhost when address not present`() {
        service.recordLogLine(handler, "INFO:ENGINE: Engine service started on port 8001")

        val endpoint = service.currentEndpoint()

        assertThat(endpoint).isNotNull
        assertThat(endpoint!!.address).isEqualTo("127.0.0.1")
        assertThat(endpoint.port).isEqualTo(8001)
    }

    @Test
    fun `stops only engines running on matching debug port`() {
        val otherHandler = mockk<OSProcessHandler>(relaxed = true)
        every { otherHandler.addProcessListener(any()) } returns Unit
        every { otherHandler.isProcessTerminating } returns false
        every { otherHandler.isProcessTerminated } returns false

        service.attachToProcess(handler, 8172)
        service.attachToProcess(otherHandler, 9000)

        service.stopEnginesForPort(8172)

        verify { handler.destroyProcess() }
        verify(exactly = 0) { otherHandler.destroyProcess() }
    }

    @Test
    fun `hasEngineForPort matches debug port`() {
        service.attachToProcess(handler, 7000)

        assertThat(service.hasEngineForPort(7000)).isTrue()
        assertThat(service.hasEngineForPort(8000)).isFalse()
    }
}
