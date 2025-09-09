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
    private val localRootField = JBTextField()
    private val remoteRootField = JBTextField()
    private val workingDirField = JBTextField()

    override fun resetEditorFrom(configuration: DefoldMobDebugRunConfiguration) {
        portField.text = configuration.port.toString()
        localRootField.text = configuration.localRoot.ifBlank {
            configuration.project.basePath ?: ""
        }
        remoteRootField.text = configuration.remoteRoot
        workingDirField.text = configuration.workingDir.ifBlank { configuration.project.basePath ?: "" }
    }

    override fun applyEditorTo(configuration: DefoldMobDebugRunConfiguration) {
        configuration.port = portField.text.toIntOrNull() ?: 8172
        configuration.localRoot = localRootField.text.trim()
        configuration.remoteRoot = remoteRootField.text.trim()
        configuration.workingDir = workingDirField.text.trim()
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
        portField.columns = 8
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

        // Working Dir label
        val workDirLabel = JBLabel("Working dir:")
        cbc.gridx = 0
        cbc.gridy = 5
        cbc.weightx = 0.1
        cbc.fill = HORIZONTAL
        add(workDirLabel, cbc)

        // Working Dir field
        workingDirField.columns = 30
        cbc.gridx = 1
        cbc.gridy = 5
        cbc.weightx = 0.9
        cbc.fill = HORIZONTAL
        add(workingDirField, cbc)
    }
}
