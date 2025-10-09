package com.aridclown.intellij.defold.logging

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.Result
import com.intellij.execution.filters.Filter.ResultItem
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.editor.markup.TextAttributes

class DefoldCounterFilter(private val attributes: TextAttributes) : Filter {
    private val pattern = Regex("^\\r?\\[x(\\d+)] ")

    override fun applyFilter(line: String, entireLength: Int): Result? {
        val match = pattern.find(line) ?: return null
        if (match.range.isEmpty()) return null
        val startOffset = entireLength - line.length + match.range.first
        val endOffset = startOffset + match.value.length
        val item = ResultItem(startOffset, endOffset, null, attributes)
        return Result(listOf(item))
    }
}

fun ConsoleView.installCounterFilter() {
    addMessageFilter(DefoldCounterFilter(DefoldConsoleStyles.counterTextAttributes))
}
