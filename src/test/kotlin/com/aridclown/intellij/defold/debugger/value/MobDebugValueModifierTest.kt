package com.aridclown.intellij.defold.debugger.value

import com.aridclown.intellij.defold.debugger.MobDebugProcess
import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.frame.XValueModifier
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach

class MobDebugValueModifierTest {

    private lateinit var mockEvaluator: MobDebugEvaluator
    private lateinit var mockDebugProcess: MobDebugProcess

    @BeforeEach
    fun setUp() {
        mockEvaluator = mockk()
        mockDebugProcess = mockk()
    }

    @Test
    fun `should allow editing vector component`() {
        // Given a vector component variable (e.g., vector.x)
        val vectorXVariable = MobVariable(
            name = "x",
            value = MobRValue.Num("1.0"),
            expression = "myVector.x"
        )

        val modifier = MobDebugValueModifier(
            evaluator = mockEvaluator,
            frameIndex = 0,
            variable = vectorXVariable,
            debugProcess = mockDebugProcess
        )

        // Mock successful statement execution
        every { 
            mockEvaluator.executeStatement(
                frameIndex = 0,
                statement = "myVector.x = 2.0",
                onSuccess = any<() -> Unit>(),
                onError = any<(String) -> Unit>()
            )
        } answers {
            val onSuccess = arg<() -> Unit>(2)
            onSuccess()
        }

        every { 
            mockDebugProcess.refreshCurrentStackFrame(any<() -> Unit>()) 
        } answers {
            val callback = arg<() -> Unit>(0)
            callback()
        }

        // When setting a new value
        val expression = mockk<XExpression> {
            every { this@mockk.expression } returns "2.0"
        }
        
        val callback = mockk<XValueModifier.XModificationCallback>(relaxed = true)
        modifier.setValue(expression, callback)

        // Then the correct assignment statement should be executed
        verify { 
            mockEvaluator.executeStatement(
                frameIndex = 0,
                statement = "myVector.x = 2.0",
                onSuccess = any<() -> Unit>(),
                onError = any<(String) -> Unit>()
            )
        }
        verify { callback.valueModified() }
    }

    @Test
    fun `should allow editing table element`() {
        // Given a table element variable (e.g., table[1])
        val tableElementVariable = MobVariable(
            name = "1",
            value = MobRValue.Str("hello"),
            expression = "myTable[1]"
        )

        val modifier = MobDebugValueModifier(
            evaluator = mockEvaluator,
            frameIndex = 0,
            variable = tableElementVariable,
            debugProcess = mockDebugProcess
        )

        // Mock successful statement execution
        every { 
            mockEvaluator.executeStatement(
                frameIndex = 0,
                statement = "myTable[1] = \"world\"",
                onSuccess = any<() -> Unit>(),
                onError = any<(String) -> Unit>()
            )
        } answers {
            val onSuccess = arg<() -> Unit>(2)
            onSuccess()
        }

        every { 
            mockDebugProcess.refreshCurrentStackFrame(any<() -> Unit>()) 
        } answers {
            val callback = arg<() -> Unit>(0)
            callback()
        }

        // When setting a new value
        val expression = mockk<XExpression> {
            every { this@mockk.expression } returns "\"world\""
        }
        
        val callback = mockk<XValueModifier.XModificationCallback>(relaxed = true)
        modifier.setValue(expression, callback)

        // Then the correct assignment statement should be executed
        verify { 
            mockEvaluator.executeStatement(
                frameIndex = 0,
                statement = "myTable[1] = \"world\"",
                onSuccess = any<() -> Unit>(),
                onError = any<(String) -> Unit>()
            )
        }
        verify { callback.valueModified() }
    }

    @Test
    fun `should allow editing url component`() {
        // Given a URL component variable (e.g., url.socket)
        val urlSocketVariable = MobVariable(
            name = "socket",
            value = MobRValue.Str("main"),
            expression = "myUrl.socket"
        )

        val modifier = MobDebugValueModifier(
            evaluator = mockEvaluator,
            frameIndex = 0,
            variable = urlSocketVariable,
            debugProcess = mockDebugProcess
        )

        // Mock successful statement execution  
        every { 
            mockEvaluator.executeStatement(
                frameIndex = 0,
                statement = "myUrl.socket = \"other\"",
                onSuccess = any<() -> Unit>(),
                onError = any<(String) -> Unit>()
            )
        } answers {
            val onSuccess = arg<() -> Unit>(2)
            onSuccess()
        }

        every { 
            mockDebugProcess.refreshCurrentStackFrame(any<() -> Unit>()) 
        } answers {
            val callback = arg<() -> Unit>(0)
            callback()
        }

        // When setting a new value
        val expression = mockk<XExpression> {
            every { this@mockk.expression } returns "\"other\""
        }
        
        val callback = mockk<XValueModifier.XModificationCallback>(relaxed = true)
        modifier.setValue(expression, callback)

        // Then the correct assignment statement should be executed
        verify { 
            mockEvaluator.executeStatement(
                frameIndex = 0,
                statement = "myUrl.socket = \"other\"",
                onSuccess = any<() -> Unit>(),
                onError = any<(String) -> Unit>()
            )
        }
        verify { callback.valueModified() }
    }

    @Test
    fun `should provide initial editor text for hash values`() {
        // Given a hash variable
        val hashVariable = MobVariable(
            name = "myHash",
            value = MobRValue.Hash("hash: [example]", "example"),
            expression = "myHash"
        )

        val modifier = MobDebugValueModifier(
            evaluator = mockEvaluator,
            frameIndex = 0,
            variable = hashVariable,
            debugProcess = mockDebugProcess
        )

        // When getting initial editor text
        val initialText = modifier.initialValueEditorText

        // Then it should provide a hash constructor
        assertThat(initialText).isEqualTo("hash(\"example\")")
    }

    @Test
    fun `should handle error during value modification`() {
        // Given a variable
        val variable = MobVariable(
            name = "x",
            value = MobRValue.Num("1.0"),
            expression = "myVector.x"
        )

        val modifier = MobDebugValueModifier(
            evaluator = mockEvaluator,
            frameIndex = 0,
            variable = variable,
            debugProcess = mockDebugProcess
        )

        // Mock failed statement execution
        every { 
            mockEvaluator.executeStatement(
                frameIndex = 0,
                statement = "myVector.x = invalid",
                onSuccess = any<() -> Unit>(),
                onError = any<(String) -> Unit>()
            )
        } answers {
            val onError = arg<(String) -> Unit>(3)
            onError("Syntax error")
        }

        // When setting an invalid value
        val expression = mockk<XExpression> {
            every { this@mockk.expression } returns "invalid"
        }
        
        val callback = mockk<XValueModifier.XModificationCallback>(relaxed = true)
        modifier.setValue(expression, callback)

        // Then error should be reported
        verify { callback.errorOccurred("Failed to set value: Syntax error") }
    }

    @Test
    fun `should handle editing nil to non-nil value`() {
        // Given a nil variable
        val nilVariable = MobVariable(
            name = "myVar",
            value = MobRValue.Nil,
            expression = "myVar"
        )

        val modifier = MobDebugValueModifier(
            evaluator = mockEvaluator,
            frameIndex = 0,
            variable = nilVariable,
            debugProcess = mockDebugProcess
        )

        // Mock successful statement execution
        every { 
            mockEvaluator.executeStatement(
                frameIndex = 0,
                statement = "myVar = 42",
                onSuccess = any<() -> Unit>(),
                onError = any<(String) -> Unit>()
            )
        } answers {
            val onSuccess = arg<() -> Unit>(2)
            onSuccess()
        }

        every { 
            mockDebugProcess.refreshCurrentStackFrame(any<() -> Unit>()) 
        } answers {
            val callback = arg<() -> Unit>(0)
            callback()
        }

        // When setting a non-nil value
        val expression = mockk<XExpression> {
            every { this@mockk.expression } returns "42"
        }
        
        val callback = mockk<XValueModifier.XModificationCallback>(relaxed = true)
        modifier.setValue(expression, callback)

        // Then the correct assignment statement should be executed
        verify { 
            mockEvaluator.executeStatement(
                frameIndex = 0,
                statement = "myVar = 42",
                onSuccess = any<() -> Unit>(),
                onError = any<(String) -> Unit>()
            )
        }
        verify { callback.valueModified() }
    }

    @Test
    fun `should handle nil variable with blank expression`() {
        // Given a nil variable with no expression (common case from debugger)
        val nilVariable = MobVariable(
            name = "someVar",
            value = MobRValue.Nil,
            expression = "" // This might happen when expression is not set properly
        )

        val modifier = MobDebugValueModifier(
            evaluator = mockEvaluator,
            frameIndex = 0,
            variable = nilVariable,
            debugProcess = mockDebugProcess
        )

        // Mock successful statement execution
        every { 
            mockEvaluator.executeStatement(
                frameIndex = 0,
                statement = "someVar = \"hello\"", // Should use name when expression is blank
                onSuccess = any<() -> Unit>(),
                onError = any<(String) -> Unit>()
            )
        } answers {
            val onSuccess = arg<() -> Unit>(2)
            onSuccess()
        }

        every { 
            mockDebugProcess.refreshCurrentStackFrame(any<() -> Unit>()) 
        } answers {
            val callback = arg<() -> Unit>(0)
            callback()
        }

        // When setting a string value
        val expression = mockk<XExpression> {
            every { this@mockk.expression } returns "\"hello\""
        }
        
        val callback = mockk<XValueModifier.XModificationCallback>(relaxed = true)
        modifier.setValue(expression, callback)

        // Then should use variable name as fallback when expression is blank
        verify { 
            mockEvaluator.executeStatement(
                frameIndex = 0,
                statement = "someVar = \"hello\"",
                onSuccess = any<() -> Unit>(),
                onError = any<(String) -> Unit>()
            )
        }
        verify { callback.valueModified() }
    }
}