package com.aridclown.intellij.defold.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.tang.intellij.lua.lang.LuaLanguage
import com.tang.intellij.lua.psi.LuaFuncBody
import com.tang.intellij.lua.psi.LuaFuncDef
import com.tang.intellij.lua.psi.LuaIndexExpr
import com.tang.intellij.lua.psi.LuaLocalFuncDef

/**
 * Provides autocompletion for standard Defold lifecycle function parameters without requiring annotations.
 * Detects context (e.g., inside on_input function) and suggests appropriate table fields.
 */
class DefoldParameterCompletionContributor : CompletionContributor() {
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
                    val position = parameters.position

                    // Only complete if we're in a member access context (after a dot)
                    val indexExpr = position.parentOfType<LuaIndexExpr>() ?: return
                    val prefix = indexExpr.firstChild?.text ?: return

                    // Find the enclosing function body
                    val funcBody = PsiTreeUtil.getParentOfType(position, LuaFuncBody::class.java) ?: return

                    // Get parameter names from the function body
                    val paramNames = funcBody.paramNameDefList.map { it.text }

                    // Check if prefix matches one of the parameters
                    if (prefix !in paramNames) return

                    // Get the function definition (either regular or local)
                    // Extract function name from the text (simpler than navigating PSI)
                    val functionName: String? = when (val functionDef = funcBody.parent) {
                        is LuaFuncDef -> {
                            // For "function on_input(...)" or "function module.on_input(...)"
                            // Extract the function name from the text
                            val text = functionDef.text
                            val match = Regex("""function\s+(\w+)""").find(text)
                            match?.groupValues?.get(1)
                        }

                        is LuaLocalFuncDef -> functionDef.id?.text

                        else -> null
                    }
                    if (functionName == null) return

                    // Get completions based on function name and parameter
                    val completions = when (functionName) {
                        "on_input" -> getOnInputCompletions(prefix, paramNames)
                        "on_message" -> getOnMessageCompletions(prefix, paramNames)
                        "update" -> getUpdateCompletions(prefix, paramNames)
                        "fixed_update" -> getFixedUpdateCompletions(prefix, paramNames)
                        "init" -> getInitCompletions(prefix, paramNames)
                        "final" -> getFinalCompletions(prefix, paramNames)
                        else -> emptyList()
                    }

                    completions.forEach { result.addElement(it) }
                }
            }
        )
    }

    private fun getOnInputCompletions(prefix: String, paramNames: List<String>): List<LookupElementBuilder> {
        // on_input(self, action_id, action)
        return when (prefix) {
            paramNames.getOrNull(2) -> ACTION_TABLE_FIELDS

            // "action" parameter
            paramNames.getOrNull(0) -> SELF_FIELDS

            // "self" parameter
            else -> emptyList()
        }
    }

    private fun getOnMessageCompletions(prefix: String, paramNames: List<String>): List<LookupElementBuilder> {
        // on_message(self, message_id, message, sender)
        return when (prefix) {
            paramNames.getOrNull(2) -> MESSAGE_TABLE_FIELDS

            // "message" parameter
            paramNames.getOrNull(3) -> SENDER_FIELDS

            // "sender" parameter
            paramNames.getOrNull(0) -> SELF_FIELDS

            // "self" parameter
            else -> emptyList()
        }
    }

    private fun getUpdateCompletions(prefix: String, paramNames: List<String>): List<LookupElementBuilder> {
        // update(self, dt)
        return when (prefix) {
            paramNames.getOrNull(0) -> SELF_FIELDS

            // "self" parameter
            else -> emptyList()
        }
    }

    private fun getFixedUpdateCompletions(prefix: String, paramNames: List<String>): List<LookupElementBuilder> {
        // fixed_update(self, dt)
        return when (prefix) {
            paramNames.getOrNull(0) -> SELF_FIELDS

            // "self" parameter
            else -> emptyList()
        }
    }

    private fun getInitCompletions(prefix: String, paramNames: List<String>): List<LookupElementBuilder> {
        // init(self)
        return when (prefix) {
            paramNames.getOrNull(0) -> SELF_FIELDS

            // "self" parameter
            else -> emptyList()
        }
    }

    private fun getFinalCompletions(prefix: String, paramNames: List<String>): List<LookupElementBuilder> {
        // final(self)
        return when (prefix) {
            paramNames.getOrNull(0) -> SELF_FIELDS

            // "self" parameter
            else -> emptyList()
        }
    }

    companion object {
        private val ACTION_TABLE_FIELDS = listOf(
            LookupElementBuilder.create("pressed")
                .withTypeText("boolean", true)
                .withTailText(" - true if the input was pressed", true),
            LookupElementBuilder.create("released")
                .withTypeText("boolean", true)
                .withTailText(" - true if the input was released", true),
            LookupElementBuilder.create("repeated")
                .withTypeText("boolean", true)
                .withTailText(" - true if the input was repeated", true),
            LookupElementBuilder.create("value")
                .withTypeText("number", true)
                .withTailText(" - amount of input (for analog inputs)", true),
            LookupElementBuilder.create("x")
                .withTypeText("number", true)
                .withTailText(" - x coordinate (touch/mouse)", true),
            LookupElementBuilder.create("y")
                .withTypeText("number", true)
                .withTailText(" - y coordinate (touch/mouse)", true),
            LookupElementBuilder.create("dx")
                .withTypeText("number", true)
                .withTailText(" - change in x coordinate", true),
            LookupElementBuilder.create("dy")
                .withTypeText("number", true)
                .withTailText(" - change in y coordinate", true),
            LookupElementBuilder.create("screen_x")
                .withTypeText("number", true)
                .withTailText(" - screen space x coordinate", true),
            LookupElementBuilder.create("screen_y")
                .withTypeText("number", true)
                .withTailText(" - screen space y coordinate", true),
            LookupElementBuilder.create("screen_dx")
                .withTypeText("number", true)
                .withTailText(" - change in screen x coordinate", true),
            LookupElementBuilder.create("screen_dy")
                .withTypeText("number", true)
                .withTailText(" - change in screen y coordinate", true),
            LookupElementBuilder.create("acc_x")
                .withTypeText("number", true)
                .withTailText(" - accelerometer x value", true),
            LookupElementBuilder.create("acc_y")
                .withTypeText("number", true)
                .withTailText(" - accelerometer y value", true),
            LookupElementBuilder.create("acc_z")
                .withTypeText("number", true)
                .withTailText(" - accelerometer z value", true),
            LookupElementBuilder.create("touch")
                .withTypeText("table[]", true)
                .withTailText(" - multi-touch data", true),
            LookupElementBuilder.create("gamepad")
                .withTypeText("number", true)
                .withTailText(" - gamepad index", true)
        )

        private val MESSAGE_TABLE_FIELDS = listOf(
            LookupElementBuilder.create("...")
                .withTypeText("any", true)
                .withTailText(" - message-specific fields", true)
        )

        private val SENDER_FIELDS = listOf(
            LookupElementBuilder.create("socket")
                .withTypeText("number", true)
                .withTailText(" - socket identifier", true),
            LookupElementBuilder.create("path")
                .withTypeText("string", true)
                .withTailText(" - path to sender object", true),
            LookupElementBuilder.create("fragment")
                .withTypeText("string", true)
                .withTailText(" - fragment identifier", true)
        )

        private val SELF_FIELDS = listOf(
            LookupElementBuilder.create("...")
                .withTypeText("any", true)
                .withTailText(" - script-specific instance data", true)
        )
    }
}
