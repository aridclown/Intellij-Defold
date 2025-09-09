package com.aridclown.intellij.defold.debugger

import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider

/**
 * Provides Lua editors for evaluating expressions in the debugger.
 */
object MobDebugEditorsProvider : XDebuggerEditorsProvider() {
    override fun getSupportedLanguages(
        project: Project,
        sourcePosition: XSourcePosition?
    ): Collection<Language> =
        listOfNotNull(Language.findLanguageByID("Lua"))

    override fun getFileType(): FileType =
        FileTypeManager.getInstance().getFileTypeByExtension("lua")

    override fun createDocument(
        project: Project,
        expression: XExpression,
        sourcePosition: XSourcePosition?,
        mode: EvaluationMode
    ): Document = EditorFactory.getInstance().createDocument(expression.expression)
}
