package com.aridclown.intellij.defold.actions

import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import java.util.concurrent.atomic.AtomicBoolean

private const val NEW_GROUP_ID = "NewGroup"
private const val DEFOLD_NEW_SCRIPT_ACTION_ID = "Defold.NewScript"

object DefoldNewGroupActionManager {
    private val registered = AtomicBoolean(false)

    fun register() {
        if (!registered.compareAndSet(false, true)) return

        val manager = ActionManager.getInstance()
        val originalGroup = manager.getAction(NEW_GROUP_ID) as? DefaultActionGroup ?: return
        val defoldAction = manager.getAction(DEFOLD_NEW_SCRIPT_ACTION_ID) ?: return

        manager.unregisterAction(NEW_GROUP_ID)
        manager.registerAction(NEW_GROUP_ID, DefoldNewGroup(originalGroup, defoldAction))
    }
}

private class DefoldNewGroup(
    private val delegate: DefaultActionGroup,
    private val defoldAction: AnAction
) : DefaultActionGroup(), DumbAware {

    init {
        isPopup = delegate.isPopup
        templatePresentation.copyFrom(delegate.templatePresentation)
    }

    override fun getChildren(event: AnActionEvent?): Array<AnAction> = when {
        event == null -> delegate.getChildren(null)
        event.isNotDefoldProject() -> delegate.getChildren(event)
        else -> buildDefoldMenu(event)
    }

    private fun AnActionEvent.isNotDefoldProject(): Boolean = !project.isDefoldProject

    private fun buildDefoldMenu(event: AnActionEvent): Array<AnAction> {
        val remaining = delegate.getChildren(event)
            .filterNot { it === defoldAction }
            .mapNotNull { it.sanitized(event) }
            .normalized()

        return assembleMenu(defoldAction, remaining)
    }
}

private fun assembleMenu(defoldAction: AnAction, others: List<AnAction>): Array<AnAction> = buildList {
    add(defoldAction)
    if (others.isNotEmpty()) {
        add(Separator.create())
        addAll(others)
    }
}.toTypedArray()

private val FILTERED_KEYWORDS = setOf("java", "kotlin", "module", "scratch", "lua")

private fun AnAction.sanitized(event: AnActionEvent): AnAction? {
    if (isFiltered()) return null

    val group = this as? ActionGroup ?: return this
    val originalChildren = group.getChildren(event)
    val cleanedChildren = originalChildren.mapNotNull { it.sanitized(event) }
    if (cleanedChildren.isEmpty()) return null
    if (cleanedChildren.size == originalChildren.size) return this

    return DefaultActionGroup().apply {
        isPopup = group.isPopup
        templatePresentation.copyFrom(group.templatePresentation)
        cleanedChildren.forEach(::add)
    }
}

private fun AnAction.isFiltered(): Boolean {
    if (this is Separator) return false
    return matchesKeyword(templatePresentation.textWithMnemonic)
            || matchesKeyword(ActionManager.getInstance().getId(this))
}

private fun matchesKeyword(source: String?): Boolean =
    source?.lowercase()?.let { text -> FILTERED_KEYWORDS.any(text::contains) } ?: false

private fun List<AnAction>.normalized(): List<AnAction> =
    fold(mutableListOf<AnAction>()) { acc, action ->
        val isSeparator = action is Separator
        if (isSeparator && (acc.isEmpty() || acc.last() is Separator)) acc
        else acc.apply { add(action) }
    }.apply {
        if (lastOrNull() is Separator) removeAt(lastIndex)
    }
