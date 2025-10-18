package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.impl.XSourcePositionImpl
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat

class MobDebugXDebuggerEvaluatorTest : BasePlatformTestCase() {

    fun `test skips callee identifier`() {
        val file = myFixture.configureByText(
            "script.lua",
            """
            function foo()
                hash("foo")
            end
            """.trimIndent()
        )
        val document = myFixture.editor.document
        val frameLine = document.getLineNumber(document.text.indexOf("hash"))
        val evaluator = MobDebugXDebuggerEvaluator(
            project,
            MobDebugEvaluator(mockk<MobDebugProtocol>(relaxed = true)),
            frameIndex = 0,
            framePosition = XSourcePositionImpl.create(file.virtualFile, frameLine)
        )

        val offset = document.text.indexOf("hash")
        val range = evaluator.getExpressionRangeAtOffset(project, document, offset, false)

        assertThat(range).isNull()
    }

    fun `test evaluates call receiver`() {
        val file = myFixture.configureByText(
            "script.lua",
            """
            function foo()
                local go = msg.url()
                go.get_position()
            end
            """.trimIndent()
        )
        val document = myFixture.editor.document
        val callOffset = document.text.indexOf("go.get_position")
        val frameLine = document.getLineNumber(callOffset)
        val evaluator = MobDebugXDebuggerEvaluator(
            project,
            MobDebugEvaluator(mockk<MobDebugProtocol>(relaxed = true)),
            frameIndex = 0,
            framePosition = XSourcePositionImpl.create(file.virtualFile, frameLine)
        )

        val range = evaluator.getExpressionRangeAtOffset(project, document, callOffset, false)

        assertThat(range?.substring(document)).isEqualTo("go")
    }

    fun `test skips method name`() {
        val file = myFixture.configureByText(
            "script.lua",
            """
            function foo()
                local go = msg.url()
                go.get_position()
            end
            """.trimIndent()
        )
        val document = myFixture.editor.document
        val methodOffset = document.text.indexOf("get_position")
        val frameLine = document.getLineNumber(document.text.indexOf("go.get_position"))
        val evaluator = MobDebugXDebuggerEvaluator(
            project,
            MobDebugEvaluator(mockk<MobDebugProtocol>(relaxed = true)),
            frameIndex = 0,
            framePosition = XSourcePositionImpl.create(file.virtualFile, frameLine)
        )

        val range = evaluator.getExpressionRangeAtOffset(project, document, methodOffset, false)

        assertThat(range).isNull()
    }

    private fun TextRange.substring(document: com.intellij.openapi.editor.Document): String =
        document.getText(this)
}
