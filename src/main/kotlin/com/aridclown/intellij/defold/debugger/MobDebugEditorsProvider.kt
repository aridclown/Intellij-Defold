package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProviderBase
import com.tang.intellij.lua.lang.LuaFileType
import com.tang.intellij.lua.lang.LuaLanguage
import com.tang.intellij.lua.psi.impl.LuaExprCodeFragmentImpl

/**
 * Provides Lua editors for evaluating expressions in the debugger.
 */
object MobDebugEditorsProvider : XDebuggerEditorsProviderBase() {

    override fun getSupportedLanguages(
        project: Project,
        sourcePosition: XSourcePosition?
    ): Collection<Language> = listOf(LuaLanguage.INSTANCE)

    override fun getFileType(): FileType = LuaFileType.INSTANCE

    override fun createExpressionCodeFragment(
        project: Project,
        text: String,
        context: PsiElement?,
        isPhysical: Boolean
    ): PsiFile {
        val fragment = LuaExprCodeFragmentImpl(project, name = "defold_debugger_expr.lua", text, isPhysical)

        // This is required to make the fragment work with Lua references and code completion
        return fragment.apply {
            setContext(context)
            putUserData(DEBUGGER_LOCALS_KEY, currentFrameLocals(project))
        }
    }

    /**
     * Get the local variables from the current stack frame, if available.
     */
    private fun currentFrameLocals(project: Project): List<MobVariable>? {
        val session = XDebuggerManager.getInstance(project).currentSession ?: return null
        val frame = session.currentStackFrame as? MobDebugStackFrame ?: return null

        return frame.visibleLocals().ifEmpty { null }
    }
}

internal val DEBUGGER_LOCALS_KEY: Key<List<MobVariable>> = Key.create("defold.debugger.locals")
