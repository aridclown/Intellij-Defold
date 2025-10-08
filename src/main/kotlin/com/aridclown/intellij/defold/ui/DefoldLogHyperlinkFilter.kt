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
        val matches = pattern.findAll(line)
            .toList()
            .takeIf { it.isNotEmpty() }
            ?: return null

        val textStartOffset = entireLength - line.length
        val results = matches.mapNotNull { match ->
            val filePath = match.groupValues[1]
            val lineNumber = match.groupValues[3].toInt()

            resolveFile(filePath)?.let { file ->
                val start = textStartOffset + match.range.first
                val end = textStartOffset + match.range.last + 1
                val hyperlink = OpenFileHyperlinkInfo(project, file, lineNumber - 1)
                Filter.ResultItem(start, end, hyperlink)
            }
        }

        return results
            .takeIf { it.isNotEmpty() }
            ?.let { Filter.Result(it) }
    }

    private fun resolveFile(relativePath: String): VirtualFile? {
        val basePath = project.basePath ?: return null
        val fullPath = "$basePath/$relativePath"
        return LocalFileSystem.getInstance().findFileByPath(fullPath)
    }
}
