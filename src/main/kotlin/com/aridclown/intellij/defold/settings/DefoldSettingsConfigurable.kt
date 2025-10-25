package com.aridclown.intellij.defold.settings

import com.aridclown.intellij.defold.DefoldDefaults
import com.aridclown.intellij.defold.Platform
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class DefoldSettingsConfigurable : SearchableConfigurable, Configurable.NoScroll {

    private val settings = DefoldSettings.getInstance()
    private var currentPath: String = ""
    private var panel: DialogPanel? = null

    override fun getId(): String = "com.aridclown.intellij.defold.settings"

    override fun getDisplayName(): String = "Defold"

    override fun getHelpTopic(): String? = null

    override fun createComponent(): JComponent {
        panel = panel {
            group("Installation") {
                row("Install path") {
                    val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        .withTitle("Select Defold Installation")
                        .withDescription("Select the folder that contains the Defold editor.")

                    val cell = textFieldWithBrowseButton(
                        browseDialogTitle = "Select Defold Installation",
//                        fileChooserDescriptor = descriptor
                    ).bindText(object : MutableProperty<String> {
                        override fun get(): String = currentPath
                        override fun set(value: String) {
                            currentPath = value
                        }
                    })

                    defaultComment()?.let { cell.comment(it) }
                }
            }
        }

        reset()
        return panel as DialogPanel
    }

    override fun isModified(): Boolean {
        val stored = settings.installPath()?.trim()
        val candidate = currentPath.trim()
        val suggestion = defaultSuggestion()?.trim()

        val baseline = stored ?: suggestion.orEmpty()
        return candidate != baseline
    }

    override fun apply() {
        val value = currentPath.trim()
        if (value.isEmpty()) {
            settings.clearInstallPath()
        } else {
            settings.setInstallPath(value)
        }
    }

    override fun reset() {
        currentPath = settings.installPath() ?: defaultSuggestion().orEmpty()
        panel?.reset()
    }

    override fun disposeUIResources() {
        panel = null
    }

    private fun defaultSuggestion(): String? =
        DefoldDefaults.installPathSuggestion(Platform.current())

    private fun defaultComment(): String? =
        defaultSuggestion()?.takeIf { it.isNotBlank() }?.let { "Default: $it" }
}
