package com.aridclown.intellij.defold.debugger

import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagConstraints.HORIZONTAL
import java.awt.GridBagLayout
import java.awt.event.KeyEvent
import javax.swing.JComponent

class DefoldMobDebugSettingsEditor : SettingsEditor<DefoldMobDebugRunConfiguration>() {
    private val portField = JBTextField("8172")

    override fun resetEditorFrom(configuration: DefoldMobDebugRunConfiguration) {
        portField.text = configuration.port.toString()
    }

    override fun applyEditorTo(configuration: DefoldMobDebugRunConfiguration) {
        configuration.port = portField.text.toIntOrNull() ?: 8172
    }

    override fun createEditor(): JComponent = JBPanel<JBPanel<*>>().apply {
        layout = GridBagLayout()
        val cbc = GridBagConstraints()

        // Port label with mnemonic and labelFor (equivalent to &Port: in .form)
        val portLabel = JBLabel("Port:")
        portLabel.displayedMnemonic = KeyEvent.VK_P
        portLabel.displayedMnemonicIndex = 0
        portLabel.labelFor = portField

        cbc.gridx = 0
        cbc.gridy = 0
        cbc.weightx = 0.1
        cbc.fill = HORIZONTAL
        add(portLabel, cbc)

        // Port field
        portField.columns = 8
        cbc.gridx = 1
        cbc.gridy = 0
        cbc.weightx = 0.9
        cbc.fill = HORIZONTAL
        add(portField, cbc)
    }
}
