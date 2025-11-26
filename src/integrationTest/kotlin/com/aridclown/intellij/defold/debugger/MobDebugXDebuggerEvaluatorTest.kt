package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.impl.XSourcePositionImpl
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat

/**
 * Integration tests for MobDebugXDebuggerEvaluator expression range selection.
 */
class MobDebugXDebuggerEvaluatorTest : BasePlatformTestCase() {
    private fun createEvaluator(
        virtualFile: VirtualFile,
        frameLine: Int
    ): MobDebugXDebuggerEvaluator = MobDebugXDebuggerEvaluator(
        project,
        MobDebugEvaluator(mockk<MobDebugProtocol>(relaxed = true)),
        frameIndex = 0,
        framePosition = XSourcePositionImpl.create(virtualFile, frameLine)
    )

    private fun assertExpressionRangeIs(
        code: String,
        searchText: String,
        expected: String?
    ) {
        val file = myFixture.configureByText("script.lua", code)
        val document = myFixture.editor.document
        val offset = document.text.indexOf(searchText)
        val frameLine = document.getLineNumber(offset)
        val evaluator = createEvaluator(file.virtualFile, frameLine)

        val range = evaluator.getExpressionRangeAtOffset(project, document, offset, false)

        if (expected == null) {
            assertThat(range).isNull()
        } else {
            assertThat(range?.substring(document)).isEqualTo(expected)
        }
    }

    // Tests that should return null

    fun `test skips callee identifier`() = assertExpressionRangeIs(
        """
            function foo()
                hash("foo")
            end
        """.trimIndent(),
        "hash",
        null
    )

    fun `test skips method name`() = assertExpressionRangeIs(
        """
            function foo()
                local go = msg.url()
                go.get_position()
            end
        """.trimIndent(),
        "get_position",
        null
    )

    fun `test skips function name in call`() = assertExpressionRangeIs(
        """
            function foo()
                local result = math.sqrt(16)
            end
        """.trimIndent(),
        "sqrt",
        null
    )

    // Tests that should return expressions

    fun `test evaluates call receiver`() = assertExpressionRangeIs(
        """
            function foo()
                local go = msg.url()
                go.get_position()
            end
        """.trimIndent(),
        "go.get_position",
        "go"
    )

    fun `test evaluates variable identifier`() = assertExpressionRangeIs(
        """
            function foo()
                local value = 42
                print(value)
            end
        """.trimIndent(),
        "value)",
        "value"
    )

    fun `test evaluates table field access`() = assertExpressionRangeIs(
        """
            function foo()
                local t = {x = 10}
                print(t.x)
            end
        """.trimIndent(),
        "t.x",
        "t"
    )

    fun `test evaluates self reference in method`() = assertExpressionRangeIs(
        """
            function Obj:method()
                self.value = 10
            end
        """.trimIndent(),
        "self",
        "self"
    )

    fun `test evaluates global variable`() = assertExpressionRangeIs(
        """
            function foo()
                print(_G.some_global)
            end
        """.trimIndent(),
        "_G",
        "_G"
    )

    fun `test evaluates Defold API object`() = assertExpressionRangeIs(
        """
            function foo()
                local v = vmath.vector3()
            end
        """.trimIndent(),
        "vmath",
        "vmath"
    )

    fun `test evaluates array index base`() = assertExpressionRangeIs(
        """
            function foo()
                local arr = {1, 2, 3}
                print(arr[1])
            end
        """.trimIndent(),
        "arr[",
        "arr"
    )

    private fun TextRange.substring(document: Document): String = document.getText(this)
}
