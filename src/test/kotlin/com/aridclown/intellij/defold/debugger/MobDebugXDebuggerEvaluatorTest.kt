package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.value.MobDebugValue
import com.aridclown.intellij.defold.debugger.value.MobRValue
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator.XEvaluationCallback
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import io.mockk.mockk
import javax.swing.Icon

class MobDebugXDebuggerEvaluatorTest {

    private val logger = mockk<Logger>(relaxed = true)
    private val server = MobDebugServer("127.0.0.1", 0, logger)
    private val protocol = MobDebugProtocol(server, logger)
    private val evaluator = MobDebugEvaluator(protocol)
    private val xEval = MobDebugXDebuggerEvaluator(
        project = mockk(relaxed = true),
        evaluator = evaluator,
        frameIndex = 3,
        framePosition = null
    )

    @Test
    fun `hover children use base expression without wrapping`() {
        // Root value is a table; expression is the base path used to fetch children.
        val rootExpr = "root.el1"
        val variable = MobVariable("el1", MobRValue.Table("table"), rootExpr)
        val value = MobDebugValue(mockk(relaxed = true), variable, evaluator, frameIndex = 3)

        // Act: trigger children computation; this issues an EXEC against the base expression.
        value.computeChildren(node = xCompositeNodeStubbed())

        // Assert: capture the last queued command on the server and verify it wasn't wrapped as ["expr"].
        val queued = lastQueued(server)

        assertThat(queued).isEqualTo("EXEC return $rootExpr -- { stack = 3, maxlevel = 1 }")
    }

    @ParameterizedTest
    @MethodSource("expressionTestCases")
    fun `evaluate expressions with various syntax patterns`(input: String, expectedCommand: String) {
        xEval.evaluate(input, object : XEvaluationCallback {
            override fun evaluated(result: XValue) {}
            override fun errorOccurred(errorMessage: String) {}
        }, null)

        val last = lastQueued(server)
        assertThat(last).isEqualTo(expectedCommand)
    }

    companion object {
        @JvmStatic
        fun expressionTestCases() = listOf(
            // Basic identifier evaluation
            arguments("msg", "EXEC return msg -- { stack = 3, maxlevel = 1 }"),

            // Colon normalization (method sugar without parentheses)
            arguments("obj:method", "EXEC return obj.method -- { stack = 3, maxlevel = 1 }"),
            arguments("player.weapon:getAmmo", "EXEC return player.weapon.getAmmo -- { stack = 3, maxlevel = 1 }"),
            arguments("a.b:c.d:e", "EXEC return a.b:c.d.e -- { stack = 3, maxlevel = 1 }"),

            // Colon preservation (method calls with parentheses)
            arguments("obj:method()", "EXEC return obj:method() -- { stack = 3, maxlevel = 1 }"),
            arguments("obj:method(arg1, arg2)", "EXEC return obj:method(arg1, arg2) -- { stack = 3, maxlevel = 1 }"),
            arguments("table.insert()", "EXEC return table.insert() -- { stack = 3, maxlevel = 1 }"),

            // Whitespace handling
            arguments("  foo.bar  ", "EXEC return foo.bar -- { stack = 3, maxlevel = 1 }"),
            arguments("obj . prop . value", "EXEC return obj . prop . value -- { stack = 3, maxlevel = 1 }"),
            arguments("obj : method", "EXEC return obj . method -- { stack = 3, maxlevel = 1 }"),

            // Complex member chains and identifiers
            arguments(
                "self.data.config.settings",
                "EXEC return self.data.config.settings -- { stack = 3, maxlevel = 1 }"
            ),
            arguments("_G._VERSION", "EXEC return _G._VERSION -- { stack = 3, maxlevel = 1 }"),
            arguments("player1.health", "EXEC return player1.health -- { stack = 3, maxlevel = 1 }"),
            arguments("local_var", "EXEC return local_var -- { stack = 3, maxlevel = 1 }"),

            // Edge cases
            arguments("", "EXEC return  -- { stack = 3, maxlevel = 1 }"),

            // Varargs
            arguments("...", "EXEC return ... -- { stack = 3, maxlevel = 1 }")
        )
    }

    @Test
    fun `evaluate different frame index in evaluator`() {
        val xEvalFrame5 = MobDebugXDebuggerEvaluator(
            project = mockk(relaxed = true),
            evaluator = evaluator,
            frameIndex = 5,
            framePosition = null
        )

        xEvalFrame5.evaluate("variable", object : XEvaluationCallback {
            override fun evaluated(result: XValue) {}
            override fun errorOccurred(errorMessage: String) {}
        }, null)

        val last = lastQueued(server)
        assertThat(last).isEqualTo("EXEC return variable -- { stack = 5, maxlevel = 1 }")
    }

    @Test
    fun `evaluator with different allowed roots configuration`() {
        val xEvalWithRoots = MobDebugXDebuggerEvaluator(
            project = mockk(relaxed = true),
            evaluator = evaluator,
            frameIndex = 3,
            framePosition = null
        )

        xEvalWithRoots.evaluate("self.property", object : XEvaluationCallback {
            override fun evaluated(result: XValue) {}
            override fun errorOccurred(errorMessage: String) {}
        }, null)

        val last = lastQueued(server)
        assertThat(last).isEqualTo("EXEC return self.property -- { stack = 3, maxlevel = 1 }")
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
