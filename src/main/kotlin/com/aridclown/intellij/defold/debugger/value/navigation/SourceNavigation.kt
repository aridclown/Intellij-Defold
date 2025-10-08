package com.aridclown.intellij.defold.debugger.value.navigation

import com.aridclown.intellij.defold.DefoldConstants.ELLIPSIS_VAR
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XNavigatable
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.tang.intellij.lua.psi.LuaDeclarationTree
import com.tang.intellij.lua.psi.LuaFuncBody

internal fun navigateToLocalDeclaration(
    project: Project,
    framePosition: XSourcePosition,
    variableName: String,
    xNavigable: XNavigatable
) = runReadAction {
    val file = framePosition.file
    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@runReadAction
    val editor = FileEditorManager.getInstance(project).getSelectedEditor(file) as? TextEditor ?: return@runReadAction
    val document = editor.editor.document
    if (framePosition.line !in 0 until document.lineCount) return@runReadAction
    val lineStartOffset = document.getLineStartOffset(framePosition.line)
    val element = psiFile.findElementAt(lineStartOffset) ?: return@runReadAction

    // Special handling for varargs
    if (variableName == ELLIPSIS_VAR) {
        navigateToVarargDeclaration(element, xNavigable)
        return@runReadAction
    }

    // Standard handling for named parameters and local variables
    LuaDeclarationTree.get(psiFile).walkUpLocal(element) {
        if (variableName == it.name) {
            val position = XSourcePositionImpl.createByElement(it.psi)
            xNavigable.setSourcePosition(position)
            return@walkUpLocal false
        }
        true
    }
}

private fun navigateToVarargDeclaration(element: PsiElement, xNavigable: XNavigatable) {
    val funcBody = PsiTreeUtil.getParentOfType(element, LuaFuncBody::class.java) ?: return
    val ellipsisElement = funcBody.ellipsis

    if (ellipsisElement != null) {
        val position = XSourcePositionImpl.createByElement(ellipsisElement)
        xNavigable.setSourcePosition(position)
    }
}
