package com.aridclown.intellij.defold.debugger.eval

import com.aridclown.intellij.defold.DefoldConstants.EXEC_MAXLEVEL
import com.aridclown.intellij.defold.debugger.Event
import com.aridclown.intellij.defold.debugger.MobDebugProtocol
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.luaj.vm2.LuaValue

class MobDebugEvaluatorTest {
    private val protocol = mockk<MobDebugProtocol>()
    private val evaluator = MobDebugEvaluator(protocol)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `returns number result for expression`() {
        val result = evaluateExpr(frame = 0, expr = "x", body = "return {\"42\"}")

        assertThat(result.toint()).isEqualTo(42)
    }

    @Test
    fun `returns string result for expression`() {
        val result = evaluateExpr(frame = 1, expr = "name", body = "return {\"\\\"hello\\\"\"}")

        assertThat(result.tojstring()).isEqualTo("hello")
    }

    @Test
    fun `returns boolean result for expression`() {
        val result = evaluateExpr(frame = 0, expr = "flag", body = "return {\"true\"}")

        assertThat(result.toboolean()).isTrue
    }

    @Test
    fun `reconstructs table when expression yields table`() {
        val result = evaluateExpr(frame = 0, expr = "tbl", body = "return {\"{a=1,b=2}\"}")

        assertThat(result.istable()).isTrue
        assertThat(result.get("a").toint()).isEqualTo(1)
        assertThat(result.get("b").toint()).isEqualTo(2)
    }

    @Test
    fun `reconstructs all values when expression targets varargs`() {
        val result = evaluateExpr(frame = 0, expr = "...", body = "return {\"1\",\"2\",\"3\"}")

        assertThat(result.istable()).isTrue
        val table = result.checktable()
        assertThat(table.length()).isEqualTo(3)
        assertThat(table.get(1).toint()).isEqualTo(1)
        assertThat(table.get(2).toint()).isEqualTo(2)
        assertThat(table.get(3).toint()).isEqualTo(3)
    }

    @Test
    fun `reports evaluation error details`() {
        val error =
            evaluateExprError(
                frame = 0,
                expr = "invalid",
                event = Event.Error("Runtime error", "Variable not found")
            )

        assertThat(error).isEqualTo("Variable not found")
    }

    @Test
    fun `reports evaluation message when error lacks details`() {
        val error =
            evaluateExprError(
                frame = 0,
                expr = "invalid",
                event = Event.Error("Runtime error", null)
            )

        assertThat(error).isEqualTo("Runtime error")
    }

    @Test
    fun `reports reconstruction failure message`() {
        val error =
            evaluateExprError(
                frame = 0,
                expr = "x",
                body = "invalid lua code"
            )

        assertThat(error).contains("Failed to evaluate")
    }

    @Test
    fun `confirms successful statement execution`() {
        val succeeded = executeStatement(statement = "x = 42")

        assertThat(succeeded).isTrue
    }

    @Test
    fun `reports statement error details`() {
        val error =
            executeStatementError(
                statement = "invalid syntax",
                event = Event.Error("Syntax error", "Unexpected symbol")
            )

        assertThat(error).isEqualTo("Unexpected symbol")
    }

    @Test
    fun `reports statement message when error lacks details`() {
        val error =
            executeStatementError(
                statement = "invalid syntax",
                event = Event.Error("Syntax error", null)
            )

        assertThat(error).isEqualTo("Syntax error")
    }

    @Test
    fun `evaluates expression in requested frame`() {
        stubEvaluateSuccess(frame = 5, expr = "value", body = "return {\"100\"}")

        var result: LuaValue? = null
        evaluator.evaluateExpr(5, "value", { result = it }, { fail("Unexpected error: $it") })

        assertThat(result).isNotNull
        assertThat(result!!.toint()).isEqualTo(100)
        verify {
            protocol.exec(
                "return value",
                frame = 5,
                options = MAXLEVEL_OPTIONS,
                onResult = any(),
                onError = any()
            )
        }
    }

    @Test
    fun `executes statement in requested frame`() {
        stubExecuteSuccess(frame = 3, statement = "y = 10")

        var succeeded = false
        evaluator.executeStatement(3, "y = 10", { succeeded = true }, { fail("Unexpected error: $it") })

        assertThat(succeeded).isTrue
        verify { protocol.exec("y = 10", frame = 3, options = MAXLEVEL_OPTIONS, onResult = any(), onError = any()) }
    }

    @Test
    fun `treats nil result as lua nil`() {
        val result = evaluateExpr(frame = 0, expr = "nothing", body = "return {\"nil\"}")

        assertThat(result.isnil()).isTrue
    }

    @Test
    fun `produces empty table when varargs return no values`() {
        val result = evaluateExpr(frame = 0, expr = "...", body = "return {}")

        assertThat(result.istable()).isTrue
        assertThat(result.checktable().length()).isEqualTo(0)
    }

    private fun evaluateExpr(
        frame: Int,
        expr: String,
        body: String
    ): LuaValue {
        stubEvaluateSuccess(frame, expr, body)

        var result: LuaValue? = null
        evaluator.evaluateExpr(frame, expr, { result = it }, { fail("Unexpected error: $it") })

        return result ?: fail("Expected evaluation result for $expr")
    }

    private fun evaluateExprError(
        frame: Int,
        expr: String,
        event: Event.Error? = null,
        body: String? = null
    ): String {
        event?.let { stubEvaluateError(frame, expr, it) }
        body?.let { stubEvaluateSuccess(frame, expr, it) }

        var errorMessage: String? = null
        evaluator.evaluateExpr(frame, expr, { fail("Expected failure for $expr") }) { errorMessage = it }

        return errorMessage ?: fail("Expected error message for $expr")
    }

    private fun executeStatement(
        frame: Int = 0,
        statement: String
    ): Boolean {
        stubExecuteSuccess(frame, statement)

        var succeeded = false
        evaluator.executeStatement(frame, statement, { succeeded = true }, { fail("Unexpected error: $it") })
        return succeeded
    }

    private fun executeStatementError(
        frame: Int = 0,
        statement: String,
        event: Event.Error
    ): String {
        stubExecuteError(frame, statement, event)

        var errorMessage: String? = null
        evaluator.executeStatement(frame, statement, { fail("Expected failure for $statement") }) { errorMessage = it }

        return errorMessage ?: fail("Expected error message for $statement")
    }

    private fun stubEvaluateSuccess(
        frame: Int,
        expr: String,
        body: String
    ) {
        val resultSlot = slot<(String) -> Unit>()
        every {
            protocol.exec(
                chunk = "return $expr",
                frame = frame,
                options = MAXLEVEL_OPTIONS,
                onResult = capture(resultSlot),
                onError = any()
            )
        } answers {
            resultSlot.captured(body)
        }
    }

    private fun stubEvaluateError(
        frame: Int,
        expr: String,
        event: Event.Error
    ) {
        val errorSlot = slot<(Event.Error) -> Unit>()
        every {
            protocol.exec(
                chunk = "return $expr",
                frame = frame,
                options = MAXLEVEL_OPTIONS,
                onResult = any(),
                onError = capture(errorSlot)
            )
        } answers {
            errorSlot.captured(event)
        }
    }

    private fun stubExecuteSuccess(
        frame: Int,
        statement: String
    ) {
        val resultSlot = slot<(String) -> Unit>()
        every {
            protocol.exec(
                chunk = statement,
                frame = frame,
                options = MAXLEVEL_OPTIONS,
                onResult = capture(resultSlot),
                onError = any()
            )
        } answers {
            resultSlot.captured("")
        }
    }

    private fun stubExecuteError(
        frame: Int,
        statement: String,
        event: Event.Error
    ) {
        val errorSlot = slot<(Event.Error) -> Unit>()
        every {
            protocol.exec(
                chunk = statement,
                frame = frame,
                options = MAXLEVEL_OPTIONS,
                onResult = any(),
                onError = capture(errorSlot)
            )
        } answers {
            errorSlot.captured(event)
        }
    }
}

private const val MAXLEVEL_OPTIONS = "maxlevel = $EXEC_MAXLEVEL"
