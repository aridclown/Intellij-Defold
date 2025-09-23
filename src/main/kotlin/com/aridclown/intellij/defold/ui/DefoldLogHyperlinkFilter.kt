package com.aridclown.intellij.defold.ui

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

/**
 * Simple filter to convert "file:line" patterns into clickable hyperlinks for Defold logs.
 */
class DefoldLogHyperlinkFilter(private val project: Project) : Filter {
    private val pattern = Regex("([a-zA-Z0-9_/.-]+\\.(script|lua|gui_script|render_script|editor_script)):(\\d+)")

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val matches = pattern.findAll(line).toList()
        if (matches.isEmpty()) return null

        val results = mutableListOf<Filter.ResultItem>()
        val textStartOffset = entireLength - line.length

        for (match in matches) {
            val filePath = match.groupValues[1]
            val lineNumber = match.groupValues[3].toInt()

            val file = resolveFile(filePath) ?: continue

            val start = textStartOffset + match.range.first
            val end = textStartOffset + match.range.last + 1

            val hyperlink = OpenFileHyperlinkInfo(project, file, lineNumber - 1)
            results.add(Filter.ResultItem(start, end, hyperlink))
        }

        return if (results.isNotEmpty()) Filter.Result(results) else null
    }

    private fun resolveFile(relativePath: String): VirtualFile? {
        val basePath = project.basePath ?: return null
        val fullPath = "$basePath/$relativePath"
        return LocalFileSystem.getInstance().findFileByPath(fullPath)
    }
}
