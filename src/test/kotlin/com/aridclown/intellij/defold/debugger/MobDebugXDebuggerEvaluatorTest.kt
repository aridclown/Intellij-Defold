package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.value.MobRValue
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator.XEvaluationCallback
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import javax.swing.Icon

class MobDebugXDebuggerEvaluatorTest {

    private val logger = mock<Logger>()
    private val server = MobDebugServer("127.0.0.1", 0, logger)
    private val protocol = MobDebugProtocol(server, logger)
    private val evaluator = MobDebugEvaluator(protocol)
    private val xEval = MobDebugXDebuggerEvaluator(
        evaluator = evaluator,
        frameIndex = 3,
        framePosition = null,
        allowedRoots = emptySet()
    )

    @Test
    fun `hover children use base expression without wrapping`() {
        // Root value is a table; expression is the base path used to fetch children.
        val rootExpr = "root.el1"
        val variable = MobVariable("el1", MobRValue.Table("table"))
        val value = MobDebugValue(variable, evaluator, frameIndex = 3, expr = rootExpr)

        // Act: trigger children computation; this issues an EXEC against the base expression.
        value.computeChildren(node = xCompositeNodeStubbed())

        // Assert: capture the last queued command on the server and verify it wasn't wrapped as ["expr"].
        val queued = lastQueued(server)

        assertEquals("EXEC return $rootExpr -- { stack = 3, maxlevel = 1 }", queued)
    }

    @Test
    fun `evaluate sends EXEC with plain identifier`() {
        xEval.evaluate("msg", object : XEvaluationCallback {
            override fun evaluated(result: XValue) {}
            override fun errorOccurred(errorMessage: String) {}
        }, null)

        val last = lastQueued(server)
        assertEquals("EXEC return msg -- { stack = 3, maxlevel = 1 }", last)
    }

    @Test
    fun `evaluate normalizes method sugar without call`() {
        xEval.evaluate("obj:method", object : XEvaluationCallback {
            override fun evaluated(result: XValue) {}
            override fun errorOccurred(errorMessage: String) {}
        }, null)

        val last = lastQueued(server)
        assertEquals("EXEC return obj.method -- { stack = 3, maxlevel = 1 }", last)
    }

    @Test
    fun `evaluate keeps method call with colon`() {
        xEval.evaluate("obj:method()", object : XEvaluationCallback {
            override fun evaluated(result: XValue) {}
            override fun errorOccurred(errorMessage: String) {}
        }, null)

        val last = lastQueued(server)
        assertEquals("EXEC return obj:method() -- { stack = 3, maxlevel = 1 }", last)
    }

    @Test
    fun `evaluate trims expression`() {
        xEval.evaluate("  foo.bar  ", object : XEvaluationCallback {
            override fun evaluated(result: XValue) {}
            override fun errorOccurred(errorMessage: String) {}
        }, null)

        val last = lastQueued(server)
        assertEquals("EXEC return foo.bar -- { stack = 3, maxlevel = 1 }", last)
    }

    private fun xCompositeNodeStubbed(): XCompositeNode = object : XCompositeNode {
        override fun addChildren(children: XValueChildrenList, last: Boolean) {}

        @Deprecated("Deprecated in Java")
        override fun tooManyChildren(remaining: Int) {
        }

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

    private fun lastQueued(server: MobDebugServer): String =
        server.getPendingCommands().lastOrNull() ?: error("No command queued")
}
