package com.aridclown.intellij.defold.settings

import com.aridclown.intellij.defold.DefoldDefaults
import com.aridclown.intellij.defold.Platform
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.singleDir
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.Align.Companion.FILL
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class DefoldSettingsConfigurable :
    SearchableConfigurable,
    Configurable.NoScroll {
    private val settings = DefoldSettings.getInstance()
    private var currentPath: String = ""
    private lateinit var textField: Cell<TextFieldWithBrowseButton>

    override fun getId(): String = "com.aridclown.intellij.defold.settings"

    override fun getDisplayName(): String = "Defold"

    override fun getHelpTopic(): String? = null

    override fun createComponent(): JComponent = panel {
        row("Install directory:") {
            textField =
                textFieldWithBrowseButton(singleDir())
                    .align(FILL)
                    .bindText(
                        { settings.installPath() ?: defaultSuggestion().orEmpty() },
                        { currentPath = it }
                    )
        }
    }

    override fun isModified(): Boolean {
        if (!::textField.isInitialized) return false
        val edited = textField.component.text.trim()
        val stored = settings.installPath()
        return if (stored == null) edited.isNotEmpty() else stored != edited
    }

    override fun apply() {
        currentPath = textField.component.text.trim()
        settings.setInstallPath(currentPath)
    }

    private fun defaultSuggestion(): String? = DefoldDefaults.installPathSuggestion(Platform.current())
}
