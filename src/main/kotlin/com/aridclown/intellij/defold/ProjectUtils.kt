package com.aridclown.intellij.defold

import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT

private fun ConsoleView.printLine(message: String, type: ConsoleViewContentType) {
    print("$message\n", type)
}

fun ConsoleView.printInfo(message: String) = printLine(message, NORMAL_OUTPUT)

fun ConsoleView.printError(message: String) = printLine(message, ERROR_OUTPUT)