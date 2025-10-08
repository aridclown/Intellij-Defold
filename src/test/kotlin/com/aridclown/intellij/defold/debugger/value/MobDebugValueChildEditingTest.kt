package com.aridclown.intellij.defold.debugger.value

import com.aridclown.intellij.defold.debugger.MobDebugProcess
import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XSourcePosition
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple.tuple
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach

class MobDebugValueChildEditingTest {

    private lateinit var mockProject: Project
    private lateinit var mockEvaluator: MobDebugEvaluator
    private lateinit var mockDebugProcess: MobDebugProcess
    private lateinit var mockDebuggerManager: XDebuggerManager
    private lateinit var mockSession: XDebugSession

    @BeforeEach
    fun setUp() {
        mockProject = mockk()
        mockEvaluator = mockk()
        mockDebugProcess = mockk()
        mockDebuggerManager = mockk()
        mockSession = mockk()
        
        every { XDebuggerManager.getInstance(mockProject) } returns mockDebuggerManager
        every { mockDebuggerManager.currentSession } returns mockSession
        every { mockSession.debugProcess } returns mockDebugProcess
    }

    @Test
    fun `vector children should be editable`() {
        // Given a vector3 value
        val vector3Variable = MobVariable(
            name = "myVector",
            value = MobRValue.VectorN("vmath.vector3(1, 2, 3)", listOf(1.0, 2.0, 3.0)),
            expression = "myVector"
        )

        val vectorValue = MobDebugValue(
            project = mockProject,
            variable = vector3Variable,
            evaluator = mockEvaluator,
            frameIndex = 0
        )

        // When getting the children
        val children = vector3Variable.value.let { it as MobRValue.VectorN }
            .toMobVarList("myVector")

        // Then children should have correct properties
        assertThat(children)
            .hasSize(3)
            .extracting(MobVariable::name, MobVariable::expression)
            .containsExactly(
                tuple("x", "myVector.x"),
                tuple("y", "myVector.y"), 
                tuple("z", "myVector.z")
            )
        
        assertThat(children)
            .extracting<Class<out MobRValue>> { it.value::class.java }
            .allMatch { it == MobRValue.Num::class.java }

        // And child values should have modifiers available
        val xChildValue = MobDebugValue(mockProject, children[0], mockEvaluator, 0)
        val xModifier = xChildValue.getModifier()
        assertThat(xModifier).isNotNull()
        assertThat(xModifier).isInstanceOf(MobDebugValueModifier::class.java)
    }

    @Test
    fun `url children should be editable`() {
        // Given a URL value
        val urlVariable = MobVariable(
            name = "myUrl",
            value = MobRValue.Url("url: [main:/path#fragment]", "main", "/path", "fragment"),
            expression = "myUrl"
        )

        // When getting the children
        val children = (urlVariable.value as MobRValue.Url).toMobVarList("myUrl")

        // Then children should have correct properties
        assertThat(children)
            .hasSize(3)
            .extracting(MobVariable::name, MobVariable::expression, MobVariable::value)
            .containsExactly(
                tuple("socket", "myUrl.socket", MobRValue.Str("main")),
                tuple("path", "myUrl.path", MobRValue.Str("/path")),
                tuple("fragment", "myUrl.fragment", MobRValue.Str("fragment"))
            )

        // And child values should have modifiers available
        val socketChildValue = MobDebugValue(mockProject, children[0], mockEvaluator, 0)
        val socketModifier = socketChildValue.getModifier()
        assertThat(socketModifier).isNotNull()
    }

    @Test
    fun `hash values should be editable`() {
        // Given a hash variable
        val hashVariable = MobVariable(
            name = "myHash",
            value = MobRValue.Hash("hash: [example]", "example"),
            expression = "myHash"
        )

        val hashValue = MobDebugValue(
            project = mockProject,
            variable = hashVariable,
            evaluator = mockEvaluator,
            frameIndex = 0
        )

        // When getting the modifier
        val modifier = hashValue.getModifier()

        // Then it should be available
        assertThat(modifier).isNotNull()
        assertThat(modifier).isInstanceOf(MobDebugValueModifier::class.java)
        
        // And should provide proper initial editor text
        assertThat(modifier?.initialValueEditorText).isEqualTo("hash(\"example\")")
    }

    @Test
    fun `matrix children should not be editable`() {
        // Given a matrix variable
        val matrixVariable = MobVariable(
            name = "myMatrix",
            value = MobRValue.Matrix(
                "vmath.matrix4(1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1)", 
                listOf(
                    listOf(1.0, 0.0, 0.0, 0.0),
                    listOf(0.0, 1.0, 0.0, 0.0),
                    listOf(0.0, 0.0, 1.0, 0.0),
                    listOf(0.0, 0.0, 0.0, 1.0)
                )
            ),
            expression = "myMatrix"
        )

        val matrixValue = MobDebugValue(
            project = mockProject,
            variable = matrixVariable,
            evaluator = mockEvaluator,
            frameIndex = 0
        )

        // When getting the modifier
        val modifier = matrixValue.getModifier()

        // Then it should not be available (matrices are complex and shouldn't be directly edited)
        assertThat(modifier).isNull()
    }

    @Test
    fun `nil value with blank expression should be editable`() {
        // Given a nil variable with blank expression (common debugger scenario)
        val nilVariable = MobVariable(
            name = "someVar",
            value = MobRValue.Nil,
            expression = "" // Blank expression should not prevent editing
        )

        val nilValue = MobDebugValue(
            project = mockProject,
            variable = nilVariable,
            evaluator = mockEvaluator,
            frameIndex = 0
        )

        // When getting the modifier
        val modifier = nilValue.getModifier()

        // Then it should be available even with blank expression
        assertThat(modifier).isNotNull()
        assertThat(modifier).isInstanceOf(MobDebugValueModifier::class.java)
        
        // And should provide proper initial editor text
        assertThat(modifier?.initialValueEditorText).isEqualTo("nil")
    }

    @Test
    fun `primitive values should remain editable`() {
        // Given primitive values
        val stringVar = MobVariable("myString", MobRValue.Str("hello"), "myString")
        val numberVar = MobVariable("myNumber", MobRValue.Num("42.5"), "myNumber")
        val boolVar = MobVariable("myBool", MobRValue.Bool(true), "myBool")
        val nilVar = MobVariable("myNil", MobRValue.Nil, "myNil")

        val primitiveValues = listOf(stringVar, numberVar, boolVar, nilVar).map { 
            MobDebugValue(mockProject, it, mockEvaluator, 0)
        }

        // When getting modifiers
        val modifiers = primitiveValues.map { it.getModifier() }

        // Then all should have modifiers available
        assertThat(modifiers).allMatch { it != null }

        // And should provide proper initial editor text
        assertThat(primitiveValues)
            .extracting<String> { it.getModifier()?.initialValueEditorText }
            .containsExactly("\"hello\"", "42.5", "true", "nil")
    }
}