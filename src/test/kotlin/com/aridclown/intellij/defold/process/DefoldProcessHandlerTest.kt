package com.aridclown.intellij.defold.process

import com.aridclown.intellij.defold.logging.DEFOLD_DEBUG_KEY_ID
import com.aridclown.intellij.defold.logging.DEFOLD_RESOURCE_KEY_ID
import com.aridclown.intellij.defold.logging.DEFOLD_WARNING_KEY_ID
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes.STDERR
import com.intellij.execution.process.ProcessOutputTypes.STDOUT
import com.intellij.openapi.util.Key
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.OutputStream

class DefoldProcessHandlerTest {
    @Test
    fun `processes single complete line and detects severity`() {
        val (handler, capture) = createHandlerWithCapture()

        handler.simulateTextAvailable("INFO: Application started\n", STDOUT)

        assertThat(capture.outputs).containsExactly("INFO: Application started\n" to STDOUT)
    }

    @Test
    fun `processes multiple complete lines in single chunk`() {
        val (handler, capture) = createHandlerWithCapture()

        handler.simulateTextAvailable("INFO: Line 1\nINFO: Line 2\n", STDOUT)

        assertThat(capture.outputs).containsExactly(
            "INFO: Line 1\n" to STDOUT,
            "INFO: Line 2\n" to STDOUT
        )
    }

    @Test
    fun `buffers incomplete line until newline arrives`() {
        val (handler, capture) = createHandlerWithCapture()

        handler.simulateTextAvailable("INFO: Partial", STDOUT)

        assertThat(capture.outputs).isEmpty()

        handler.simulateTextAvailable(" line\n", STDOUT)

        assertThat(capture.outputs).containsExactly(
            "INFO: Partial line\n" to STDOUT
        )
    }

    @Test
    fun `detects WARNING severity and switches output key`() {
        val (handler, capture) = createHandlerWithCapture()

        handler.simulateTextAvailable("WARNING: Something wrong\n", STDOUT)

        assertThat(capture.outputs)
            .singleElement()
            .extracting({ it.first }, { it.second.toString() })
            .containsExactly("WARNING: Something wrong\n", DEFOLD_WARNING_KEY_ID)
    }

    @Test
    fun `detects ERROR severity and switches to stderr`() {
        val (handler, capture) = createHandlerWithCapture()

        handler.simulateTextAvailable("ERROR: Fatal issue\n", STDOUT)

        assertThat(capture.outputs).containsExactly(
            "ERROR: Fatal issue\n" to STDERR
        )
    }

    @Test
    fun `detects DEBUG severity and switches output key`() {
        val (handler, capture) = createHandlerWithCapture()

        handler.simulateTextAvailable("DEBUG: Trace info\n", STDOUT)

        assertThat(capture.outputs)
            .singleElement()
            .extracting({ it.first }, { it.second.toString() })
            .containsExactly("DEBUG: Trace info\n", DEFOLD_DEBUG_KEY_ID)
    }

    @Test
    fun `maintains severity across continuation lines with indentation`() {
        val (handler, capture) = createHandlerWithCapture()

        handler.simulateTextAvailable("ERROR: Main error\n", STDOUT)
        handler.simulateTextAvailable("  continuation\n", STDOUT)

        assertThat(capture.outputs).containsExactly(
            "ERROR: Main error\n" to STDERR,
            "  continuation\n" to STDERR
        )
    }

    @Test
    fun `resets severity when non-continuation line is detected`() {
        val (handler, capture) = createHandlerWithCapture()

        handler.simulateTextAvailable("ERROR: Error message\n", STDOUT)
        handler.simulateTextAvailable("Normal line\n", STDOUT)

        assertThat(capture.outputs).containsExactly(
            "ERROR: Error message\n" to STDERR,
            "Normal line\n" to STDERR
        )
    }

    @Test
    fun `flushes remaining buffer on process termination`() {
        val (handler, capture) = createHandlerWithCapture()

        handler.simulateTextAvailable("Incomplete line without newline", STDOUT)

        assertThat(capture.outputs).isEmpty()

        handler.simulateProcessTerminated(0)

        assertThat(capture.outputs).containsExactly(
            "Incomplete line without newline" to STDOUT
        )
    }

    @Test
    fun `does not flush empty buffer on termination`() {
        val (handler, capture) = createHandlerWithCapture()

        handler.simulateProcessTerminated(0)

        assertThat(capture.outputs).isEmpty()
    }

    @Test
    fun `handles mixed complete and incomplete lines`() {
        val (handler, capture) = createHandlerWithCapture()

        handler.simulateTextAvailable("INFO: Complete\nPartial", STDOUT)

        assertThat(capture.outputs).containsExactly(
            "INFO: Complete\n" to STDOUT
        )

        handler.simulateTextAvailable(" finish\n", STDOUT)

        assertThat(capture.outputs).containsExactly(
            "INFO: Complete\n" to STDOUT,
            "Partial finish\n" to STDOUT
        )
    }

    @Test
    fun `detects RESOURCE severity in INFO lines`() {
        val (handler, capture) = createHandlerWithCapture()

        handler.simulateTextAvailable("INFO: RESOURCE loaded successfully\n", STDOUT)

        assertThat(capture.outputs)
            .singleElement()
            .extracting({ it.first }, { it.second.toString() })
            .containsExactly("INFO: RESOURCE loaded successfully\n", DEFOLD_RESOURCE_KEY_ID)
    }

    @Test
    fun `handles TRACE as DEBUG severity`() {
        val (handler, capture) = createHandlerWithCapture()

        handler.simulateTextAvailable("TRACE: Detailed trace\n", STDOUT)

        assertThat(capture.outputs)
            .singleElement()
            .extracting({ it.first }, { it.second.toString() })
            .containsExactly("TRACE: Detailed trace\n", DEFOLD_DEBUG_KEY_ID)
    }

    @Test
    fun `handles empty lines using previous severity`() {
        val (handler, capture) = createHandlerWithCapture()

        handler.simulateTextAvailable("ERROR: Error\n", STDOUT)
        handler.simulateTextAvailable("\n", STDOUT)

        assertThat(capture.outputs).containsExactly(
            "ERROR: Error\n" to STDERR,
            "\n" to STDERR
        )
    }

    @Test
    fun `treats stack traceback as non-continuation even with whitespace`() {
        val (handler, capture) = createHandlerWithCapture()

        handler.simulateTextAvailable("ERROR: Error occurred\n", STDOUT)
        handler.simulateTextAvailable("  stack traceback:\n", STDOUT)

        assertThat(capture.outputs).containsExactly(
            "ERROR: Error occurred\n" to STDERR,
            "  stack traceback:\n" to STDERR
        )
    }

    @Test
    fun `case insensitive severity detection`() {
        val (handler, capture) = createHandlerWithCapture()

        handler.simulateTextAvailable("warning: lowercase warning\n", STDOUT)

        assertThat(capture.outputs)
            .singleElement()
            .extracting({ it.first }, { it.second.toString() })
            .containsExactly("warning: lowercase warning\n", DEFOLD_WARNING_KEY_ID)
    }

    @Test
    fun `handles multiple chunks buffering correctly`() {
        val (handler, capture) = createHandlerWithCapture()

        handler.simulateTextAvailable("Part ", STDOUT)
        handler.simulateTextAvailable("of ", STDOUT)
        handler.simulateTextAvailable("line\n", STDOUT)

        assertThat(capture.outputs).containsExactly(
            "Part of line\n" to STDOUT
        )
    }

    private fun createHandlerWithCapture(): Pair<StubbedDefoldProcessHandler, OutputCapture> {
        val handler = StubbedDefoldProcessHandler()
        val capture = OutputCapture().also(handler::addProcessListener)

        return handler to capture
    }

    /**
     * Test implementation that uses ProcessTextProcessor without spawning a real OS process.
     * This allows us to test the buffering and severity detection logic in isolation.
     */
    private class StubbedDefoldProcessHandler : ProcessHandler() {
        private val processor = ProcessTextProcessor()

        override fun destroyProcessImpl() {}

        override fun detachProcessImpl() {}

        override fun detachIsDefault() = false

        override fun getProcessInput(): OutputStream? = null

        @Suppress("UNUSED_PARAMETER")
        fun simulateTextAvailable(
            text: String,
            outputType: Key<*>
        ) {
            processor.processChunk(text) { line, severity ->
                notifyTextAvailable(line, severity.outputKey)
            }
        }

        fun simulateProcessTerminated(exitCode: Int) {
            processor.flush { line, severity ->
                notifyTextAvailable(line, severity.outputKey)
            }
            notifyProcessTerminated(exitCode)
        }
    }

    private class OutputCapture : ProcessListener {
        val outputs = mutableListOf<Pair<String, Key<*>>>()

        override fun onTextAvailable(
            event: ProcessEvent,
            outputType: Key<*>
        ) {
            outputs.add(event.text to outputType)
        }
    }
}
