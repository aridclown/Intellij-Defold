package com.aridclown.intellij.defold.debugger

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XExpression
import com.tang.intellij.lua.lang.LuaFileType

/**
 * Provides Lua editors for evaluating expressions in the debugger.
 */
object MobDebugEditorsProvider : XDebuggerEditorsProvider() {
    override fun getFileType(): FileType = LuaFileType.INSTANCE

    override fun createDocument(project: Project, expression: XExpression, sourcePosition: XSourcePosition?, mode: EvaluationMode): Document {
        return EditorFactory.getInstance().createDocument(expression.expression)
    }
}
