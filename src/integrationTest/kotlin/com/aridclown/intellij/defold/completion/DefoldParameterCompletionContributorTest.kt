package com.aridclown.intellij.defold.completion

import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat

class DefoldParameterCompletionContributorTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        disableLspCompletionContributor()
    }

    fun `test completes action table fields in on_input`() {
        myFixture.configureByText(
            "test.lua",
            """
            function on_input(self, action_id, action)
                action.<caret>
            end
            """.trimIndent()
        )

        val lookups = myFixture.completeBasic().map { it.lookupString }

        assertThat(lookups)
            .contains("pressed", "released", "repeated", "x", "y", "dx", "dy")
            .contains("screen_x", "screen_y", "value", "touch", "gamepad")
    }

    fun `test completes action fields with custom parameter name`() {
        myFixture.configureByText(
            "test.lua",
            """
            function on_input(self, action_id, input_action)
                input_action.<caret>
            end
            """.trimIndent()
        )

        val lookups = myFixture.completeBasic().map { it.lookupString }

        assertThat(lookups)
            .contains("pressed", "released", "x", "y")
    }

    fun `test completes sender fields in on_message`() {
        myFixture.configureByText(
            "test.lua",
            """
            function on_message(self, message_id, message, sender)
                sender.<caret>
            end
            """.trimIndent()
        )

        val lookups = myFixture.completeBasic().map { it.lookupString }

        assertThat(lookups)
            .contains("socket", "path", "fragment")
    }

    fun `test completes message fields in on_message`() {
        myFixture.configureByText(
            "test.lua",
            """
            function on_message(self, message_id, message, sender)
                message.<caret>
            end
            """.trimIndent()
        )

        val lookups = myFixture.completeBasic()

        assertThat(lookups).isNotNull()
    }

    fun `test does not complete when not in function parameter context`() {
        myFixture.configureByText(
            "test.lua",
            """
            function on_input(self, action_id, action)
                local my_action = {}
                my_action.<caret>
            end
            """.trimIndent()
        )

        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()

        assertThat(lookups)
            .doesNotContain("pressed", "released")
    }

    fun `test completes in nested function calls`() {
        myFixture.configureByText(
            "test.lua",
            """
            function on_input(self, action_id, action)
                if action.<caret> then
                    print("pressed")
                end
            end
            """.trimIndent()
        )

        val lookups = myFixture.completeBasic().map { it.lookupString }

        assertThat(lookups)
            .contains("pressed", "released")
    }

    fun `test completes self parameter in update`() {
        myFixture.configureByText(
            "test.lua",
            """
            function update(self, dt)
                self.<caret>
            end
            """.trimIndent()
        )

        val lookups = myFixture.completeBasic()

        assertThat(lookups).isNotNull()
    }

    fun `test completes self parameter in init`() {
        myFixture.configureByText(
            "test.lua",
            """
            function init(self)
                self.<caret>
            end
            """.trimIndent()
        )

        val lookups = myFixture.completeBasic()

        assertThat(lookups).isNotNull()
    }

    fun `test completes in GUI script on_input`() {
        myFixture.configureByText(
            "test.lua",
            """
            function on_input(self, action_id, action)
                action.<caret>
            end
            """.trimIndent()
        )

        val lookups = myFixture.completeBasic().map { it.lookupString }

        assertThat(lookups)
            .contains("pressed", "released", "x", "y")
    }

    fun `test does not complete in unrelated function`() {
        myFixture.configureByText(
            "test.lua",
            """
            function my_custom_function(self, action_id, action)
                action.<caret>
            end
            """.trimIndent()
        )

        val lookups = myFixture.completeBasic()?.map { it.lookupString } ?: emptyList()

        assertThat(lookups)
            .doesNotContain("pressed", "released")
    }

    private fun disableLspCompletionContributor() {
        val contributors = COMPLETION_EP_NAME.extensionList
        val filtered = contributors.filterNot { contributor ->
            contributor.pluginDescriptor.pluginId.idString == LSP_PLUGIN_ID
        }
        if (filtered.size == contributors.size) return

        ExtensionTestUtil.maskExtensions(COMPLETION_EP_NAME, filtered.toMutableList(), testRootDisposable)
    }

    private companion object {
        const val LSP_PLUGIN_ID = "com.redhat.devtools.lsp4ij"
        val COMPLETION_EP_NAME: ExtensionPointName<CompletionContributorEP> =
            ExtensionPointName.create("com.intellij.completion.contributor")
    }
}
