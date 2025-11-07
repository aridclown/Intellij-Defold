package com.aridclown.intellij.defold.logging

import com.aridclown.intellij.defold.logging.LogSeverity.*
import com.intellij.execution.process.ProcessOutputType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LogStylingTest {

    @Test
    fun `warn severity sticks across indented continuation`() {
        val first = LogClassifier.detect("WARNING: Something happened", INFO)
        val second = LogClassifier.detect("  context", first)

        assertThat(first).isEqualTo(WARNING)
        assertThat(second).isEqualTo(WARNING)
        assertThat(first.outputKey).isEqualTo(LogColorPalette.warningKey)
    }

    @Test
    fun `error severity switches to stderr output`() {
        val severity = LogClassifier.detect("ERROR: Boom", INFO)

        assertThat(severity).isEqualTo(ERROR)
        assertThat(severity.outputKey).isEqualTo(ProcessOutputType.STDERR)
    }

    @Test
    fun `trace lines behave like debug severity`() {
        val severity = LogClassifier.detect("TRACE: More info", INFO)

        assertThat(severity).isEqualTo(DEBUG)
        assertThat(severity.outputKey).isEqualTo(LogColorPalette.debugKey)
    }

    @Test
    fun `resource keyword upgrades info severity`() {
        val severity = LogClassifier.detect("INFO: resource loaded", INFO)

        assertThat(severity).isEqualTo(RESOURCE)
        assertThat(severity.outputKey).isEqualTo(LogColorPalette.resourceKey)
    }

    @Test
    fun `blank line keeps previous severity`() {
        val previous = WARNING
        val severity = LogClassifier.detect("   ", previous)

        assertThat(severity).isEqualTo(previous)
    }

    @Test
    fun `non continuation line keeps current severity`() {
        val first = LogClassifier.detect("ERROR: Boom", INFO)
        val second = LogClassifier.detect("Next", first)

        assertThat(second).isEqualTo(ERROR)
    }

    @Test
    fun `unrecognized prefixes fall back to previous severity`() {
        val severity = LogClassifier.detect("Random text", DEBUG)

        assertThat(severity).isEqualTo(DEBUG)
    }

    @Test
    fun `stack trace prefix is treated as new section`() {
        val first = LogClassifier.detect("ERROR: boom", INFO)
        val second = LogClassifier.detect("  stack traceback:", first)

        assertThat(second).isEqualTo(ERROR)
    }

    @Test
    fun `custom console keys are registered with palette`() {
        assertThat(LogColorPalette.warningKey.toString()).isEqualTo(DEFOLD_WARNING_KEY_ID)
        assertThat(LogColorPalette.debugKey.toString()).isEqualTo(DEFOLD_DEBUG_KEY_ID)
        assertThat(LogColorPalette.resourceKey.toString()).isEqualTo(DEFOLD_RESOURCE_KEY_ID)
    }
}
