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
import com.tang.intellij.lua.psi.LuaBlock
import com.tang.intellij.lua.psi.LuaForAStat
import com.tang.intellij.lua.psi.LuaForBStat
import com.tang.intellij.lua.psi.LuaFuncBody
import com.tang.intellij.lua.psi.LuaLocalDef
import com.tang.intellij.lua.psi.LuaLocalFuncDef
import com.tang.intellij.lua.psi.LuaPsiFile
import com.tang.intellij.lua.psi.LuaTypes

/**
 * EmmyLua2 no longer provides the `LuaDeclarationTree` helper the original plugin relied on, so we
 * recreate the minimal scope walk we need for debugger navigation: climb upwards through blocks,
 * function bodies and loop statements, checking preceding siblings for `local` declarations and
 * parameters until we find the matching PSI element.
 */
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
    val declaration = findLocalDeclaration(element, variableName)
    if (declaration != null) {
        val position = XSourcePositionImpl.createByElement(declaration)
        xNavigable.setSourcePosition(position)
    }
}

private fun navigateToVarargDeclaration(element: PsiElement, xNavigable: XNavigatable) {
    val funcBody = PsiTreeUtil.getParentOfType(element, LuaFuncBody::class.java) ?: return
    val ellipsisElement = funcBody.findVarargElement()

    if (ellipsisElement != null) {
        val position = XSourcePositionImpl.createByElement(ellipsisElement)
        xNavigable.setSourcePosition(position)
    }
}

private fun findLocalDeclaration(element: PsiElement, variableName: String): PsiElement? {
    var child: PsiElement? = element
    var scope: PsiElement? = element.parent

    while (scope != null) {
        when (scope) {
            is LuaFuncBody -> {
                scope.findParameter(variableName)?.let { return it }
                scope.findDeclarationBefore(child, variableName)?.let { return it }
            }

            is LuaBlock, is LuaPsiFile -> scope.findDeclarationBefore(child, variableName)?.let { return it }
            is LuaForAStat -> scope.findLoopVariable(child, variableName)?.let { return it }
            is LuaForBStat -> scope.findLoopVariable(child, variableName)?.let { return it }
        }

        child = scope
        scope = scope.parent
    }

    return null
}

private fun PsiElement.findDeclarationBefore(child: PsiElement?, variableName: String): PsiElement? {
    var current = child?.prevSibling ?: lastChild

    while (current != null) {
        when (current) {
            is LuaLocalDef -> current.nameList?.nameDefList
                ?.firstOrNull { it.id.text == variableName }
                ?.id
                ?.let { return it }

            is LuaLocalFuncDef -> current.id
                ?.takeIf { it.text == variableName }
                ?.let { return it }

            is LuaBlock, is LuaFuncBody -> current.findDeclarationBefore(null, variableName)
                ?.let { return it }
        }

        current = current.prevSibling
    }

    return null
}

private fun LuaFuncBody.findParameter(variableName: String): PsiElement? =
    paramNameDefList.firstOrNull { it.id.text == variableName }?.id

private fun LuaForAStat.findLoopVariable(child: PsiElement?, variableName: String): PsiElement? {
    val body = loopBody() ?: return null
    if (child == null || !PsiTreeUtil.isAncestor(body, child, false)) return null

    return paramNameDef.id.takeIf { it.text == variableName }
}

private fun LuaForBStat.findLoopVariable(child: PsiElement?, variableName: String): PsiElement? {
    val body = loopBody() ?: return null
    if (child == null || !PsiTreeUtil.isAncestor(body, child, false)) return null

    return paramNameDefList.firstOrNull { it.id.text == variableName }?.id
}

private fun PsiElement.loopBody(): PsiElement? =
    children.firstOrNull { it is LuaBlock }

private fun LuaFuncBody.findVarargElement(): PsiElement? {
    var ellipsis: PsiElement? = null
    PsiTreeUtil.processElements(this) { candidate ->
        if (candidate.node?.elementType == LuaTypes.ELLIPSIS) {
            ellipsis = candidate
            return@processElements false
        }
        true
    }
    return ellipsis
}
