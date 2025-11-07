package com.aridclown.intellij.defold.process

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.util.Key

internal class DefoldProcessHandler(commandLine: GeneralCommandLine) : OSProcessHandler(commandLine) {
    private val processor = ProcessTextProcessor()

    override fun notifyTextAvailable(text: String, outputType: Key<*>) {
        processor.processChunk(text) { line, severity ->
            super.notifyTextAvailable(line, severity.outputKey)
        }
    }

    override fun notifyProcessTerminated(exitCode: Int) {
        processor.flush { line, severity ->
            super.notifyTextAvailable(line, severity.outputKey)
        }
        super.notifyProcessTerminated(exitCode)
    }
}
