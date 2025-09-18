package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.tang.intellij.lua.lang.LuaLanguage

/**
 * Adds debugger locals to completion results inside MobDebug expression editors.
 */
class MobDebugCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(LuaLanguage.INSTANCE),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val locals = parameters.originalFile.getUserData(DEBUGGER_LOCALS_KEY).orEmpty()
                    if (locals.isEmpty() || parameters.isMemberAccessContext(result.prefixMatcher.prefix)) return

                    locals.distinctBy(MobVariable::name)
                        .map(::toLookupElement)
                        .forEach(result::addElement)
                }
            }
        )
    }

    private fun CompletionParameters.isMemberAccessContext(prefix: String): Boolean {
        if (prefix.isEmpty()) return false

        val document = editor.document
        val prefixStart = offset - prefix.length
        if (prefixStart <= 0 || prefixStart > document.textLength) return false

        return document.charsSequence[prefixStart - 1].let { it == '.' || it == ':' }
    }

    private fun toLookupElement(variable: MobVariable): LookupElementBuilder =
        LookupElementBuilder.create(variable.name)
            .let { builder -> variable.value.icon?.let(builder::withIcon) ?: builder }
            .let { builder -> variable.value.typeLabel?.let { builder.withTypeText(it, true) } ?: builder }
            .let { builder ->
                val preview = variable.value.preview.takeUnless { it.isBlank() || it == variable.name }
                preview?.let { builder.withTailText(" = $it", true) } ?: builder
            }
}
