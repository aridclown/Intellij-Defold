package com.aridclown.intellij.defold.process

import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes.STDOUT
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.OutputStream

class DeferredProcessHandlerTest {

    private lateinit var handler: DeferredProcessHandler

    @BeforeEach
    fun setUp() {
        handler = DeferredProcessHandler().also { it.startNotify() }
    }

    @AfterEach
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun `forwards state when connecting to already terminated process`() {
        val osHandler = mockOSHandler(isTerminated = true, exitCode = 42)

        handler.attach(osHandler)

        verify(exactly = 0) { osHandler.notifyTextAvailable(any(), any()) }
        verify(exactly = 0) { osHandler.addProcessListener(any()) }
    }

    @Test
    fun `notifies when engine starts and monitors for termination`() {
        val capturedListener = slot<ProcessListener>()
        val osHandler = mockOSHandler {
            every { addProcessListener(capture(capturedListener)) } just Runs
        }

        handler.attach(osHandler)

        assertThat(capturedListener.isCaptured).isTrue
        verify { osHandler.notifyTextAvailable("Defold engine started with PID $DEFAULT_PID\n", STDOUT) }
        verify { osHandler.addProcessListener(any()) }
    }

    @Test
    fun `reports termination when engine exits`() {
        val osHandler = mockOSHandler(pid = 99999)
        val listener = attachAndCaptureListener(osHandler)

        val processEvent = ProcessEvent(osHandler, 0)
        listener.processTerminated(processEvent)

        assertThat(handler.isProcessTerminated).isTrue
        assertThat(handler.exitCode).isEqualTo(0)
    }

    @Test
    fun `stops the engine when requested`() {
        val osHandler = mockOSHandler(pid = 123)

        handler.attach(osHandler)
        handler.destroyProcess()

        verify { osHandler.destroyProcess() }
    }

    @Test
    fun `detaches from engine when requested`() {
        val osHandler = mockOSHandler(pid = 123)

        handler.attach(osHandler)
        handler.detachProcess()

        verify { osHandler.detachProcess() }
    }

    @Test
    fun `has no input stream when not connected to engine`() {
        assertThat(handler.processInput).isNull()
    }

    @Test
    fun `provides input stream when connected to engine`() {
        val outputStream = mockk<OutputStream>()
        val osHandler = mockOSHandler(pid = 123, processInput = outputStream)

        handler.attach(osHandler)

        assertThat(handler.processInput).isEqualTo(outputStream)
    }

    @Test
    fun `reports exit code as -1 when termination reason is unknown`() {
        val osHandler = mockOSHandler(isTerminated = true, exitCode = null)

        handler.attach(osHandler)

        assertThat(handler.isProcessTerminated).isTrue
        assertThat(handler.exitCode).isEqualTo(-1)
    }

    private fun mockProcess(pid: Long = DEFAULT_PID) = mockk<Process> {
        every { pid() } returns pid
    }

    private fun mockOSHandler(
        isTerminated: Boolean = false,
        exitCode: Int? = null,
        pid: Long = DEFAULT_PID,
        processInput: OutputStream? = null,
        configure: OSProcessHandler.() -> Unit = {}
    ): OSProcessHandler = mockk(relaxed = true) {
        every { isProcessTerminated } returns isTerminated
        every { this@mockk.exitCode } returns exitCode
        if (!isTerminated) every { process } returns mockProcess(pid)
        processInput?.let {
            every { this@mockk.processInput } returns it
        }
        configure()
    }

    private fun attachAndCaptureListener(osHandler: OSProcessHandler): ProcessListener {
        val capturedListener = slot<ProcessListener>()
        every { osHandler.addProcessListener(capture(capturedListener)) } just Runs
        handler.attach(osHandler)
        return capturedListener.captured
    }

    companion object {
        private const val DEFAULT_PID = 12345L
    }
}
