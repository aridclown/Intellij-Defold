package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.value.MobRValue
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.tang.intellij.lua.lang.LuaLanguage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat

class MobDebugCompletionContributorIntegrationTest : BasePlatformTestCase() {

    fun `test completion suggests debugger locals`() {
        val locals = listOf(
            MobVariable("player", MobRValue.Table()),
            MobVariable("position", MobRValue.Str("10, 20"))
        )

        completeWithLocals("p<caret>", locals) { lookupStrings ->
            assertThat(lookupStrings).contains("player", "position")
        }
    }

    fun `test completion does not suggest duplicates`() {
        val locals = listOf(
            MobVariable("player", MobRValue.Str("one")),
            MobVariable("player", MobRValue.Str("two")),
            MobVariable("position", MobRValue.Str("10, 20"))
        )

        completeWithLocals("p<caret>", locals) { lookupStrings ->
            assertThat(lookupStrings)
                .contains("position")
                .filteredOn { it == "player" }
                .hasSize(1)
        }
    }

    fun `test completion uses locals from position file when original file has none`() {
        val originalFile = mockk<PsiFile> {
            every { getUserData(DEBUGGER_LOCALS_KEY) } returns null
        }

        val locals = listOf(
            MobVariable("player", MobRValue.Table()),
            MobVariable("points", MobRValue.Num("10"))
        )

        val positionFile = mockk<PsiFile> {
            every { getUserData(DEBUGGER_LOCALS_KEY) } returns locals
        }

        val position = mockk<PsiElement> {
            every { containingFile } returns positionFile
            every { language } returns LuaLanguage.INSTANCE
        }

        val document = mockk<Document>()
        val editor = mockk<Editor> {
            every { this@mockk.document } returns document
        }

        val parameters = mockk<CompletionParameters>()
        every { parameters.originalFile } returns originalFile
        every { parameters.completionType } returns CompletionType.BASIC
        every { parameters.position } returns position
        every { parameters.editor } returns editor
        every { parameters.offset } returns 1

        val prefixMatcher = mockk<PrefixMatcher> {
            every { prefix } returns "p"
        }

        val addedElements = mutableListOf<LookupElement>()
        val resultSet = mockk<CompletionResultSet>(relaxed = true)
        every { resultSet.prefixMatcher } returns prefixMatcher

        MobDebugCompletionContributor().fillCompletionVariants(parameters, resultSet)

        verify(exactly = locals.size) { resultSet.addElement(capture(addedElements)) }
        assertThat(addedElements)
            .isNotEmpty()
            .extracting<String> { it.lookupString }
            .containsExactlyInAnyOrderElementsOf(locals.map { it.name })
    }

    fun `test completion returns empty when no locals`() {
        completeWithLocals("p<caret>", emptyList()) { lookupStrings ->
            assertThat(lookupStrings).doesNotContain("player", "position")
        }
    }

    fun `test completion skips member access context with dot or colon`() {
        val locals = listOf(
            MobVariable("print", MobRValue.Func("function"))
        )

        completeWithLocals("table.p<caret>", locals) { lookupStrings ->
            // Should not add debugger locals in member access context
            assertThat(lookupStrings).doesNotContain("print")
        }
    }

    fun `test completion skips member access context with colon`() {
        val locals = listOf(
            MobVariable("print", MobRValue.Func("function"))
        )

        completeWithLocals("table:p<caret>", locals) { lookupStrings ->
            // Should not add debugger locals in member access context
            assertThat(lookupStrings).doesNotContain("print")
        }
    }

    fun `test completion includes variables with various value types`() {
        val locals = listOf(
            MobVariable("varString", MobRValue.Str("hello")),
            MobVariable("varNumber", MobRValue.Num("42.0")),
            MobVariable("varBoolean", MobRValue.Bool(true)),
            MobVariable("varFunction", MobRValue.Func("function")),
            MobVariable("varTable", MobRValue.Table()),
            MobVariable("varNil", MobRValue.Nil)
        )

        completeWithLocals("v<caret>", locals) { lookupStrings ->
            assertThat(lookupStrings).containsExactlyInAnyOrderElementsOf(locals.map { it.name })
        }
    }

    private fun completeWithLocals(
        code: String,
        locals: List<MobVariable>? = null,
        assert: (List<String>) -> Unit
    ) {
        val file = myFixture.configureByText(TEST_FILE_NAME, code)
        locals?.let { file.putUserData(DEBUGGER_LOCALS_KEY, it) }

        myFixture.completeBasic()

        assert(myFixture.lookupElementStrings ?: emptyList())
    }

    private companion object {
        const val TEST_FILE_NAME = "test.lua"
    }
}
