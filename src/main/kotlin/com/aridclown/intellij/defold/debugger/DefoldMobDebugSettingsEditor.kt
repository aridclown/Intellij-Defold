package com.aridclown.intellij.defold.debugger

import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class DefoldMobDebugSettingsEditor : SettingsEditor<DefoldMobDebugRunConfiguration>() {
    private val hostField = JBTextField()
    private val portField = JSpinner(SpinnerNumberModel(8172, 1, 65535, 1))
    private val localField = JBTextField()
    private val remoteField = JBTextField()

    override fun resetEditorFrom(s: DefoldMobDebugRunConfiguration) {
        hostField.text = s.host
        portField.value = s.port
        localField.text = s.localRoot
        remoteField.text = s.remoteRoot
    }

    override fun applyEditorTo(s: DefoldMobDebugRunConfiguration) {
        s.host = hostField.text
        s.port = (portField.value as Number).toInt()
        s.localRoot = localField.text
        s.remoteRoot = remoteField.text
    }

    override fun createEditor(): JComponent {
        val panel = JBPanel<JBPanel<*>>(GridLayout(4, 2))
        panel.add(JBLabel("Host:"))
        panel.add(hostField)
        panel.add(JBLabel("Port:"))
        panel.add(portField)
        panel.add(JBLabel("Local root:"))
        panel.add(localField)
        panel.add(JBLabel("Remote root:"))
        panel.add(remoteField)
        return panel
    }
}
