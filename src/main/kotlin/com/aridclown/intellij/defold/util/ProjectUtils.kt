package com.aridclown.intellij.defold.util

import com.aridclown.intellij.defold.DefoldConstants.PLUGIN_DIRECTORY_NAME
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
import com.intellij.openapi.application.PathManager
import java.nio.file.Path

private fun ConsoleView.printLine(
    message: String,
    type: ConsoleViewContentType
) {
    print("$message\n", type)
}

fun ConsoleView.printInfo(message: String) = printLine(message, NORMAL_OUTPUT)

fun ConsoleView.printError(message: String) = printLine(message, ERROR_OUTPUT)

fun stdLibraryRootPath(): Path = PathManager
    .getPluginsDir()
    .resolve(PLUGIN_DIRECTORY_NAME)
    .resolve("std")
