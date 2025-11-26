package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.value.MobRValue
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.tang.intellij.lua.lang.LuaFileType
import com.tang.intellij.lua.lang.LuaLanguage
import com.tang.intellij.lua.psi.LuaExprCodeFragment
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.assertj.core.api.Assertions.assertThat

class MobDebugEditorsProviderTest : BasePlatformTestCase() {
    fun `test supported languages returns Lua language`() {
        val languages: Collection<Language> = MobDebugEditorsProvider.getSupportedLanguages(project, null)

        assertThat(languages)
            .hasSize(1)
            .containsExactly(LuaLanguage.INSTANCE)
    }

    fun `test file type returns Lua file type`() {
        val fileType: FileType = MobDebugEditorsProvider.getFileType()

        assertThat(fileType).isEqualTo(LuaFileType.INSTANCE)
    }

    fun `test expression code fragment is created with correct properties`() {
        val text = "localVar + 10"
        val fragment = createFragment(text = text)

        assertThat(fragment).isInstanceOfSatisfying(LuaExprCodeFragment::class.java) {
            assertThat(it.name).isEqualTo("defold_debugger_expr.lua")
            assertThat(it.text).isEqualTo(text)
        }
    }

    fun `test expression code fragment includes debugger locals when debugging session active`() {
        val mockVariables =
            listOf(
                MobVariable("localVar", MobRValue.Num("42")),
                MobVariable("anotherVar", MobRValue.Str("test"))
            )

        val fragment =
            createFragment(
                session =
                sessionWithFrame(
                    mockk<MobDebugStackFrame> {
                        every { visibleLocals() } returns mockVariables
                    }
                )
            )
        val locals = fragment.getUserData(DEBUGGER_LOCALS_KEY)

        assertThat(locals).isEqualTo(mockVariables)
    }

    fun `test expression code fragment has no debugger locals when no debugging session`() {
        val fragment = createFragment()
        val locals = fragment.getUserData(DEBUGGER_LOCALS_KEY)

        assertThat(locals).isNull()
    }

    fun `test expression code fragment has no debugger locals when current frame is not MobDebugStackFrame`() {
        val fragment =
            createFragment(
                session = sessionWithFrame(mockk<XStackFrame>())
            )
        val locals = fragment.getUserData(DEBUGGER_LOCALS_KEY)

        assertThat(locals).isNull()
    }

    fun `test expression code fragment has no debugger locals when visible locals is empty`() {
        val fragment =
            createFragment(
                session =
                sessionWithFrame(
                    mockk<MobDebugStackFrame> {
                        every { visibleLocals() } returns emptyList()
                    }
                )
            )
        val locals = fragment.getUserData(DEBUGGER_LOCALS_KEY)

        assertThat(locals).isNull()
    }

    private fun createFragment(
        text: String = "test",
        session: XDebugSessionImpl? = null
    ): LuaExprCodeFragment = withDebuggerManager(session) {
        MobDebugEditorsProvider.createExpressionCodeFragment(project, text, null, false) as LuaExprCodeFragment
    }

    private fun sessionWithFrame(frame: XStackFrame): XDebugSessionImpl = mockk<XDebugSessionImpl> {
        every { currentStackFrame } returns frame
    }

    private fun <T> withDebuggerManager(
        session: XDebugSessionImpl?,
        block: () -> T
    ): T {
        mockkStatic(XDebuggerManager::class)
        val manager = mockk<XDebuggerManager>()
        every { XDebuggerManager.getInstance(project) } returns manager
        every { manager.currentSession } returns session

        return try {
            block()
        } finally {
            unmockkStatic(XDebuggerManager::class)
        }
    }
}
