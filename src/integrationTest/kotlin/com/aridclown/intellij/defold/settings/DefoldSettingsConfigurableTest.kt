package com.aridclown.intellij.defold.settings

import com.aridclown.intellij.defold.DefoldDefaults
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.panels.Wrapper
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.assertj.core.api.Assertions.assertThat
import java.awt.Component
import java.awt.Container

class DefoldSettingsConfigurableTest : BasePlatformTestCase() {

    private val settings
        get() = DefoldSettings.getInstance()

    fun `test displays stored install path`() {
        settings.setInstallPath("/stored/path")

        val (configurable, field) = createConfigurable()

        val actual = field.text
        assertThat(actual).withFailMessage("expected stored path, but was '%s'", actual).isEqualTo("/stored/path")
        assertThat(configurable.isModified()).isFalse
    }

    fun `test displays default install path suggestion`() {
        mockkObject(DefoldDefaults)
        every { DefoldDefaults.installPathSuggestion(any()) } returns "/suggested/path"

        try {
            settings.clearInstallPath()

            val (_, field) = createConfigurable()

            assertThat(field.text).isEqualTo("/suggested/path")
        } finally {
            unmockkObject(DefoldDefaults)
        }
    }

    fun `test marks configurable modified after editing path`() {
        settings.setInstallPath("/stored/path")

        val (configurable, field) = createConfigurable()
        field.textField.text = "/new/path"

        assertThat(configurable.isModified()).isTrue
    }

    fun `test saves trimmed install path`() {
        val (configurable, field) = createConfigurable()
        field.textField.text = "  /applied/path  "

        configurable.apply()

        assertThat(settings.installPath()).isEqualTo("/applied/path")
        assertThat(configurable.isModified()).isFalse
    }

    private fun createConfigurable(): Pair<DefoldSettingsConfigurable, TextFieldWithBrowseButton> {
        val configurable = DefoldSettingsConfigurable()
        val component = configurable.createComponent()
        val field = collectComponents<TextFieldWithBrowseButton>(component).single()

        return configurable to field
    }

    private fun <T : Component> collectComponents(root: Component, type: Class<T>): List<T> {
        val matches = mutableListOf<T>()
        fun traverse(node: Component) {
            if (type.isInstance(node)) {
                @Suppress("UNCHECKED_CAST")
                matches += node as T
            }
            if (node is Container) {
                node.components.forEach(::traverse)
            }
            if (node is Wrapper) {
                node.targetComponent?.let(::traverse)
            }
        }
        traverse(root)
        return matches
    }

    private inline fun <reified T : Component> collectComponents(root: Component): List<T> =
        collectComponents(root, T::class.java)
}
