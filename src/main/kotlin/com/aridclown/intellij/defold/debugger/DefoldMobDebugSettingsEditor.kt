package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldConstants.DEFAULT_MOBDEBUG_PORT
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagConstraints.HORIZONTAL
import java.awt.GridBagLayout
import java.awt.event.KeyEvent
import javax.swing.JComponent

class DefoldMobDebugSettingsEditor : SettingsEditor<MobDebugRunConfiguration>() {
    private val portField = JBTextField()
    private val localRootField = JBTextField()
    private val remoteRootField = JBTextField()
    private val envField = EnvironmentVariablesTextFieldWithBrowseButton()

    override fun resetEditorFrom(configuration: MobDebugRunConfiguration) {
        portField.text = configuration.port.toString()
        localRootField.text = configuration.localRoot.ifBlank {
            configuration.project.basePath ?: ""
        }
        remoteRootField.text = configuration.remoteRoot
        envField.data = configuration.envData
    }

    override fun applyEditorTo(configuration: MobDebugRunConfiguration) = with(configuration) {
        port = portField.text.toIntOrNull() ?: DEFAULT_MOBDEBUG_PORT
        localRoot = localRootField.text.trim()
        remoteRoot = remoteRootField.text.trim()
        envData = envField.data
    }

    override fun createEditor(): JComponent = JBPanel<JBPanel<*>>().apply {
        layout = GridBagLayout()
        val cbc = GridBagConstraints()

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
        portField.columns = 10
        cbc.gridx = 1
        cbc.gridy = 0
        cbc.weightx = 0.9
        cbc.fill = HORIZONTAL
        add(portField, cbc)

        // Local Root label
        val localLabel = JBLabel("Local root:")
        cbc.gridx = 0
        cbc.gridy = 1
        cbc.weightx = 0.1
        cbc.fill = HORIZONTAL
        add(localLabel, cbc)

        // Local Root field
        localRootField.columns = 20
        cbc.gridx = 1
        cbc.gridy = 1
        cbc.weightx = 0.9
        cbc.fill = HORIZONTAL
        add(localRootField, cbc)

        // Remote Root label
        val remoteLabel = JBLabel("Remote root:")
        cbc.gridx = 0
        cbc.gridy = 2
        cbc.weightx = 0.1
        cbc.fill = HORIZONTAL
        add(remoteLabel, cbc)

        // Remote Root field
        remoteRootField.columns = 20
        cbc.gridx = 1
        cbc.gridy = 2
        cbc.weightx = 0.9
        cbc.fill = HORIZONTAL
        add(remoteRootField, cbc)

        // Environment label
        val envLabel = JBLabel("Environment:")
        envLabel.labelFor = envField.textField
        cbc.gridx = 0
        cbc.gridy = 3
        cbc.weightx = 0.1
        cbc.fill = HORIZONTAL
        add(envLabel, cbc)

        // Environment field
        cbc.gridx = 1
        cbc.gridy = 3
        cbc.weightx = 0.9
        cbc.fill = HORIZONTAL
        add(envField, cbc)
    }
}
