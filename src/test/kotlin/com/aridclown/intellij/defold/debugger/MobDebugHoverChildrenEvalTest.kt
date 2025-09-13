package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.value.MobRValue
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XValueChildrenList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.swing.Icon

class MobDebugHoverChildrenEvalTest {

    private val logger = Logger.getInstance(MobDebugHoverChildrenEvalTest::class.java)
    private val server = MobDebugServer("127.0.0.1", 0, logger)
    private val protocol = MobDebugProtocol(server, logger)

    @Test
    fun `should hover children using base expression without wrapping`() {
        val evaluator = MobDebugEvaluator(protocol)

        // Root value is a table; expression is the base path used to fetch children.
        val rootExpr = "root.el1"
        val variable = MobVariable("el1", MobRValue.Table("table"))
        val value = MobDebugValue(variable, evaluator, frameIndex = 3, expr = rootExpr)

        // Act: trigger children computation; this issues an EXEC against the base expression.
        value.computeChildren(node = xCompositeNodeStubbed())

        // Assert: capture the last queued command on the server and verify it wasn't wrapped as ["expr"].
        val queued = readPrivateField<List<String>>(server, "pendingCommands")
        val last = queued.lastOrNull() ?: error("No EXEC command was queued")

        assertEquals("EXEC return $rootExpr -- { stack = 3, maxlevel = 1 }", last)
    }

    private fun xCompositeNodeStubbed(): XCompositeNode = object : XCompositeNode {
        override fun addChildren(children: XValueChildrenList, last: Boolean) {}
        override fun tooManyChildren(remaining: Int) {}
        override fun setAlreadySorted(alreadySorted: Boolean) {}
        override fun setErrorMessage(errorMessage: String) {}
        override fun setErrorMessage(errorMessage: String, link: XDebuggerTreeNodeHyperlink?) {}
        override fun setMessage(
            message: String,
            icon: Icon?,
            attributes: SimpleTextAttributes,
            link: XDebuggerTreeNodeHyperlink?
        ) {
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> readPrivateField(target: Any, fieldName: String): T {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target) as T
    }
}
