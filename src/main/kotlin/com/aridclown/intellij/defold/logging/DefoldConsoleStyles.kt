package com.aridclown.intellij.defold.logging

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import java.awt.Font

object DefoldConsoleStyles {
    val counterTextAttributes: TextAttributes = TextAttributes().apply {
        foregroundColor = JBColor(0x5E81AC, 0x5E81AC)
        fontType = Font.BOLD
    }

    val counterContentType: ConsoleViewContentType = ConsoleViewContentType("DEFOLD_COUNTER", counterTextAttributes)
}
