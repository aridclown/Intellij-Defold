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
import com.tang.intellij.lua.psi.*

/**
 * EmmyLua2 no longer provides the declaration helper our debugger relied on. We map names to PSI
 * using small strategies so each special case (like `...`) stays isolated and easily testable.
 */
fun navigateToLocalDeclaration(
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

    VARIABLE_NAVIGATION_STRATEGIES
        .firstOrNull { it.accepts(variableName) }
        ?.locate(element, variableName)
        ?.let(XSourcePositionImpl::createByElement)
        ?.let(xNavigable::setSourcePosition)
}

private interface VariableNavigationStrategy {
    fun accepts(name: String): Boolean
    fun locate(context: PsiElement, name: String): PsiElement?
}

private object VarargNavigationStrategy : VariableNavigationStrategy {
    override fun accepts(name: String): Boolean = name == ELLIPSIS_VAR

    override fun locate(context: PsiElement, name: String): PsiElement? {
        val funcBody = PsiTreeUtil.getParentOfType(context, LuaFuncBody::class.java) ?: return null
        return funcBody.findVarargElement()
    }
}

private object LocalVariableNavigationStrategy : VariableNavigationStrategy {
    override fun accepts(name: String): Boolean = true

    override fun locate(context: PsiElement, name: String): PsiElement? =
        findLocalDeclaration(context, name)
}

private val VARIABLE_NAVIGATION_STRATEGIES = listOf(VarargNavigationStrategy, LocalVariableNavigationStrategy)

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
