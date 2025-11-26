package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.value.MobDebugValue
import com.aridclown.intellij.defold.debugger.value.MobDebugVarargValue
import com.aridclown.intellij.defold.debugger.value.MobRValue.Companion.fromRawLuaValue
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.aridclown.intellij.defold.debugger.value.MobVariable.Kind.LOCAL
import com.aridclown.intellij.defold.debugger.value.MobVariable.Kind.PARAMETER
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator.XEvaluationCallback
import com.intellij.xdebugger.frame.XValue
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.assertj.core.groups.Tuple
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaValue.NIL
import org.luaj.vm2.LuaValue.valueOf

class MobDebugXDebuggerEvaluatorTest {
    private val project = mockk<Project>(relaxed = true)
    private val mobEvaluator = mockk<MobDebugEvaluator>(relaxed = true)
    private val sourcePosition = mockk<XSourcePosition>(relaxed = true)
    private lateinit var evaluator: MobDebugXDebuggerEvaluator

    @BeforeEach
    fun setUp() {
        evaluator = MobDebugXDebuggerEvaluator(project, mobEvaluator, frameIndex = 0, framePosition = sourcePosition)
    }

    @AfterEach
    fun tearDown() = clearAllMocks()

    @Nested
    inner class ExpressionNormalization {
        @Test
        fun `normalizes colon to dot when not a call`() = assertExpressionEvaluatedAs("obj:method", "obj.method")

        @Test
        fun `preserves colon in method calls`() = assertExpressionEvaluatedAs("obj:method()", "obj:method()")

        @Test
        fun `preserves dot access unchanged`() = assertExpressionEvaluatedAs("obj.field", "obj.field")

        @Test
        fun `trims whitespace from expression`() = assertExpressionEvaluatedAs("  value  ", "value")
    }

    @Nested
    inner class FrameIndexHandling {
        @Test
        fun `uses correct frame index`() {
            val evaluatorWithFrame =
                MobDebugXDebuggerEvaluator(
                    project,
                    mobEvaluator,
                    frameIndex = 5,
                    framePosition = sourcePosition
                )

            every { mobEvaluator.evaluateExpr(any(), any(), any(), any()) } answers {
                assertThat(arg<Int>(0)).isEqualTo(5)
            }

            evaluatorWithFrame.evaluate("x", mockCallback(), null)
        }
    }

    @Nested
    inner class VarargsHandling {
        @ParameterizedTest
        @MethodSource("com.aridclown.intellij.defold.debugger.MobDebugXDebuggerEvaluatorTest#varargsUseCases")
        fun `handles varargs as table`(
            evaluatedTable: LuaTable,
            expectedResult: List<Tuple>
        ) {
            every { mobEvaluator.evaluateExpr(any(), any(), any(), any()) } answers {
                arg<(LuaValue) -> Unit>(2).invoke(evaluatedTable)
            }

            evaluator.evaluate(
                "...",
                mockCallback { result ->
                    result.asVarargValue().assertVariablesMatch(expectedResult)
                },
                null
            )
        }

        @Test
        fun `handles regular expression as value`() {
            every { mobEvaluator.evaluateExpr(any(), any(), any(), any()) } answers {
                arg<(LuaValue) -> Unit>(2).invoke(valueOf(42))
            }

            evaluator.evaluate(
                "value",
                mockCallback { result ->
                    result.asDebugValue().assertVariableMatches("value", LOCAL, "42")
                },
                null
            )
        }
    }

    @Nested
    inner class ErrorHandling {
        @Test
        fun `strips debugger prefix`() = assertErrorMessageIs("[string \"debug\"]:1: syntax error near 'end'", "syntax error near 'end'")

        @Test
        fun `handles error without prefix`() = assertErrorMessageIs("simple error", "simple error")

        @Test
        fun `handles empty error message`() = assertErrorMessageIs("", "")
    }

    @Nested
    inner class LocalKindsTracking {
        @Test
        fun `tracks parameter kinds`() {
            evaluator =
                MobDebugXDebuggerEvaluator(
                    project,
                    mobEvaluator,
                    frameIndex = 0,
                    framePosition = sourcePosition,
                    locals =
                    listOf(
                        MobVariable("param1", fromRawLuaValue("param1", NIL), kind = PARAMETER),
                        MobVariable("local1", fromRawLuaValue("local1", NIL), kind = LOCAL)
                    )
                )

            every { mobEvaluator.evaluateExpr(any(), any(), any(), any()) } answers {
                arg<(LuaValue) -> Unit>(2).invoke(valueOf("test"))
            }

            evaluator.evaluate(
                "param1",
                mockCallback { result ->
                    result.asDebugValue().assertVariableMatches("param1", PARAMETER, "test")
                },
                null
            )

            verify(exactly = 1) { mobEvaluator.evaluateExpr(any(), eq("param1"), any(), any()) }
        }

        @Test
        fun `prefers parameter over local for duplicates`() {
            val evaluatorWithLocals =
                MobDebugXDebuggerEvaluator(
                    project,
                    mobEvaluator,
                    frameIndex = 0,
                    framePosition = sourcePosition,
                    locals =
                    listOf(
                        MobVariable("x", fromRawLuaValue("x", NIL), kind = LOCAL),
                        MobVariable("x", fromRawLuaValue("x", NIL), kind = PARAMETER)
                    )
                )

            every { mobEvaluator.evaluateExpr(any(), any(), any(), any()) } answers {
                arg<(LuaValue) -> Unit>(2).invoke(valueOf(42))
            }

            evaluatorWithLocals.evaluate(
                "x",
                mockCallback { result ->
                    result.asDebugValue().assertVariableMatches("x", PARAMETER, "42")
                },
                null
            )
        }
    }

    private fun XValue.asDebugValue(): MobDebugValue {
        assertThat(this).isInstanceOf(MobDebugValue::class.java)
        return this as MobDebugValue
    }

    private fun XValue.asVarargValue(): MobDebugVarargValue {
        assertThat(this).isInstanceOf(MobDebugVarargValue::class.java)
        return this as MobDebugVarargValue
    }

    private fun MobDebugValue.assertVariableMatches(
        name: String,
        kind: MobVariable.Kind,
        preview: String
    ) {
        assertThat(variable)
            .extracting({ it.name }, { it.kind }, { it.value.preview })
            .contains(name, kind, preview)
    }

    private fun MobDebugVarargValue.assertVariablesMatch(expected: List<Tuple>) {
        assertThat(varargs)
            .extracting({ it.name }, { it.kind }, { it.value.preview })
            .containsExactlyElementsOf(expected)
    }

    private fun mockCallback(onComplete: (XValue) -> Unit = {}): XEvaluationCallback = object : XEvaluationCallback {
        override fun evaluated(result: XValue) = onComplete(result)

        override fun errorOccurred(errorMessage: String) = Unit
    }

    private fun assertExpressionEvaluatedAs(
        input: String,
        expected: String
    ) {
        every { mobEvaluator.evaluateExpr(any(), any(), any(), any()) } answers {
            assertThat(arg<String>(1)).isEqualTo(expected)
        }
        evaluator.evaluate(input, mockCallback(), null)
    }

    private fun assertErrorMessageIs(
        errorInput: String,
        expected: String
    ) {
        every { mobEvaluator.evaluateExpr(any(), any(), any(), any()) } answers {
            arg<(String) -> Unit>(3).invoke(errorInput)
        }

        evaluator.evaluate(
            "expr",
            object : XEvaluationCallback {
                override fun evaluated(result: XValue) = Unit

                override fun errorOccurred(errorMessage: String) {
                    assertThat(errorMessage).isEqualTo(expected)
                }
            },
            null
        )
    }

    companion object {
        @JvmStatic
        fun varargsUseCases() = listOf(
            arguments(
                LuaTable(),
                emptyList<Tuple>()
            ),
            arguments(
                LuaTable().apply { set(1, valueOf("arg1")) },
                listOf(tuple("(*vararg 1)", LOCAL, "arg1"))
            ),
            arguments(
                LuaTable().apply {
                    set(1, valueOf("arg1"))
                    set(2, valueOf("arg2"))
                },
                listOf(tuple("(*vararg 1)", LOCAL, "arg1"), tuple("(*vararg 2)", LOCAL, "arg2"))
            )
        )
    }
}
