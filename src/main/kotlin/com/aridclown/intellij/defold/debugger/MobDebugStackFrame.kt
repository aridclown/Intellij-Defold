package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.impl.XSourcePositionImpl

/**
 * Single Lua stack frame for MobDebug.
 */
class MobDebugStackFrame(
    private val project: Project,
    private val filePath: String?,
    private val line: Int,
    private val variables: List<MobVariable> = emptyList()
) : XStackFrame() {

    override fun getSourcePosition(): XSourcePosition? {
        val path = filePath ?: return null
        val vFile = LocalFileSystem.getInstance().findFileByPath(path) ?: return null
        return XSourcePositionImpl.create(vFile, line - 1)
    }

    override fun computeChildren(node: XCompositeNode) {
        fun XValueChildrenList.addChildren() = apply {
            variables.forEach { v ->
                add(v.name, MobDebugValue(v))
            }
        }

        node.addChildren(XValueChildrenList().addChildren(), true)
    }
}
