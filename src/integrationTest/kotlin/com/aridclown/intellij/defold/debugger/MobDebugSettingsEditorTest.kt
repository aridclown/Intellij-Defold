package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldConstants.DEFAULT_MOBDEBUG_PORT
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JTextField

class MobDebugSettingsEditorTest : BasePlatformTestCase() {

    private lateinit var editor: MobDebugSettingsEditor
    private lateinit var configuration: MobDebugRunConfiguration

    override fun setUp() {
        super.setUp()
        editor = MobDebugSettingsEditor()
        configuration = MobDebugRunConfiguration(
            project, DefoldMobDebugConfigurationType().configurationFactories.first()
        )
    }

    fun `test populates editor fields from configuration`() {
        configuration.apply {
            port = 8172
            localRoot = "/local/path"
            remoteRoot = "/remote/path"
            envData = EnvironmentVariablesData.create(mapOf("FOO" to "bar"), true)
        }

        editor.resetEditorFrom(configuration)

        assertThat(portField().text).isEqualTo("8172")
        assertThat(localField().text).isEqualTo("/local/path")
        assertThat(remoteField().text).isEqualTo("/remote/path")
    }

    fun `test falls back to project base path when local root blank`() {
        configuration.localRoot = ""

        editor.resetEditorFrom(configuration)

        assertThat(localField().text).isEqualTo(project.basePath)
    }

    fun `test falls back to project base path when local root whitespace`() {
        configuration.localRoot = "   "

        editor.resetEditorFrom(configuration)

        assertThat(localField().text).isEqualTo(project.basePath)
    }

    fun `test writes editor changes back to configuration`() {
        editor.resetEditorFrom(configuration)

        portField().text = "9999"
        localField().text = "/new/local"
        remoteField().text = "/new/remote"

        editor.applyEditorTo(configuration)

        assertThat(configuration.port).isEqualTo(9999)
        assertThat(configuration.localRoot).isEqualTo("/new/local")
        assertThat(configuration.remoteRoot).isEqualTo("/new/remote")
    }

    fun `test restores default port when user input is invalid`() {
        editor.resetEditorFrom(configuration)
        portField().text = "not-a-number"

        editor.applyEditorTo(configuration)

        assertThat(configuration.port).isEqualTo(DEFAULT_MOBDEBUG_PORT)
    }

    fun `test restores default port when user clears port field`() {
        editor.resetEditorFrom(configuration)
        portField().text = ""

        editor.applyEditorTo(configuration)

        assertThat(configuration.port).isEqualTo(DEFAULT_MOBDEBUG_PORT)
    }

    fun `test strips whitespace from edited roots`() {
        editor.resetEditorFrom(configuration)

        localField().text = "  /local/path  "
        remoteField().text = "  /remote/path  "

        editor.applyEditorTo(configuration)

        assertThat(configuration.localRoot).isEqualTo("/local/path")
        assertThat(configuration.remoteRoot).isEqualTo("/remote/path")
    }

    fun `test exposes labelled inputs for all configuration fields`() {
        val component = editor.createEditor()

        val labels = collectComponents<JLabel>(component)
        assertThat(labels)
            .extracting<String> { it.text }
            .containsExactlyInAnyOrder("Port:", "Local root:", "Remote root:", "Environment:")
        assertThat(labels)
            .extracting<Int> { it.displayedMnemonicIndex }
            .containsOnly(0)

        val labelByText = labels.associateBy { it.text }
        assertThat(labelByText.values)
            .extracting<Component?> { it.labelFor }
            .doesNotContainNull()

        val portInput = labelByText.getValue("Port:").labelFor as JTextField
        val localInput = labelByText.getValue("Local root:").labelFor as JTextField
        val remoteInput = labelByText.getValue("Remote root:").labelFor as JTextField
        assertThat(collectComponents<JTextField>(component))
            .contains(portInput, localInput, remoteInput)

        val envControl = collectComponents<EnvironmentVariablesTextFieldWithBrowseButton>(component).single()
        val envInput = labelByText.getValue("Environment:").labelFor as JTextField
        assertThat(envInput).isSameAs(envControl.textField)
    }

    fun `test preserves environment variables round trip`() {
        val envData = EnvironmentVariablesData.create(mapOf("KEY1" to "value1", "KEY2" to "value2"), true)
        configuration.envData = envData

        editor.resetEditorFrom(configuration)
        editor.applyEditorTo(configuration)

        assertThat(configuration.envData.envs).containsEntry("KEY1", "value1")
        assertThat(configuration.envData.envs).containsEntry("KEY2", "value2")
        assertThat(configuration.envData.isPassParentEnvs).isTrue
    }

    fun `test preserves pass parent envs when disabled`() {
        val envData = EnvironmentVariablesData.create(mapOf("KEY" to "value"), false)
        configuration.envData = envData

        editor.resetEditorFrom(configuration)
        editor.applyEditorTo(configuration)

        assertThat(configuration.envData.envs).containsEntry("KEY", "value")
        assertThat(configuration.envData.isPassParentEnvs).isFalse
    }

    private fun portField() = textFields()[0]
    private fun localField() = textFields()[1]
    private fun remoteField() = textFields()[2]
    private fun textFields() = editor.component.components.filterIsInstance<JTextField>()

    private inline fun <reified T : Component> collectComponents(root: Component): List<T> {
        val matches = mutableListOf<T>()
        val queue = ArrayDeque<Component>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node is T) {
                matches += node
            }
            if (node is Container) {
                node.components.forEach { queue.add(it) }
            }
        }
        return matches
    }
}
