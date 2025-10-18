package com.aridclown.intellij.defold.debugger.value.navigation

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XNavigatable
import com.intellij.xdebugger.impl.XSourcePositionImpl
import org.assertj.core.api.Assertions.assertThat

private class NavigatableCaptor : XNavigatable {
    var position: XSourcePosition? = null

    override fun setSourcePosition(sourcePosition: XSourcePosition?) {
        position = sourcePosition
    }
}

class SourceNavigationTest : BasePlatformTestCase() {

    fun `test navigates to local variable declaration`() {
        val luaFile = myFixture.configureByText(
            "script.lua",
            """
            function foo()
                local testVar = 1
                print(testVar)
            end
            """.trimIndent()
        )
        myFixture.openFileInEditor(luaFile.virtualFile)

        val sourcePosition = XSourcePositionImpl.create(luaFile.virtualFile, 2)!!
        val navigatable = NavigatableCaptor()

        navigateToLocalDeclaration(project, sourcePosition, "testVar", navigatable)

        val declarationPosition = navigatable.position ?: error("Declaration not found")
        val declarationLine = myFixture.editor.document.getLineNumber(declarationPosition.offset)
        assertThat(declarationLine).isEqualTo(1)
    }

    fun `test navigates to vararg parameter`() {
        val luaFile = myFixture.configureByText(
            "script.lua",
            """
            function foo(...)
                local first = ...
                print(first)
            end
            """.trimIndent()
        )
        myFixture.openFileInEditor(luaFile.virtualFile)

        val sourcePosition = XSourcePositionImpl.create(luaFile.virtualFile, 2)!!
        val navigatable = NavigatableCaptor()

        navigateToLocalDeclaration(project, sourcePosition, "...", navigatable)

        val declarationPosition = navigatable.position ?: error("Vararg declaration not found")
        val declarationLine = myFixture.editor.document.getLineNumber(declarationPosition.offset)
        assertThat(declarationLine).isEqualTo(0)
}
}
