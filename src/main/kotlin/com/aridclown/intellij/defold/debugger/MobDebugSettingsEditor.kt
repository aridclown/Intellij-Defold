package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldConstants.DEFAULT_MOBDEBUG_PORT
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.JComponent

class MobDebugSettingsEditor : SettingsEditor<MobDebugRunConfiguration>() {
    private val portField = JBTextField()
    private val localRootField = JBTextField()
    private val remoteRootField = JBTextField()
    private val envField = EnvironmentVariablesTextFieldWithBrowseButton()

    @VisibleForTesting
    public override fun resetEditorFrom(configuration: MobDebugRunConfiguration) {
        portField.text = configuration.port.toString()
        localRootField.text =
            configuration.localRoot.ifBlank {
                configuration.project.basePath ?: ""
            }
        remoteRootField.text = configuration.remoteRoot
        envField.data = configuration.envData
    }

    @VisibleForTesting
    public override fun applyEditorTo(configuration: MobDebugRunConfiguration) = with(configuration) {
        port = portField.text.toIntOrNull() ?: DEFAULT_MOBDEBUG_PORT
        localRoot = localRootField.text.trim()
        remoteRoot = remoteRootField.text.trim()
        envData = envField.data
    }

    @VisibleForTesting
    public override fun createEditor(): JComponent {
        val portLabel =
            JBLabel("Port:").apply {
                displayedMnemonicIndex = 0
                labelFor = portField
            }

        val localLabel =
            JBLabel("Local root:").apply {
                displayedMnemonicIndex = 0
                labelFor = localRootField
            }

        val remoteLabel =
            JBLabel("Remote root:").apply {
                displayedMnemonicIndex = 0
                labelFor = remoteRootField
            }

        val envLabel =
            JBLabel("Environment:").apply {
                displayedMnemonicIndex = 0
                labelFor = envField.textField
            }

        return panel {
            row(portLabel) {
                cell(portField)
                    .align(Align.FILL)
                    .resizableColumn()
            }
            row(localLabel) {
                cell(localRootField)
                    .align(Align.FILL)
                    .resizableColumn()
            }
            row(remoteLabel) {
                cell(remoteRootField)
                    .align(Align.FILL)
                    .resizableColumn()
            }
            row(envLabel) {
                cell(envField)
                    .align(Align.FILL)
                    .resizableColumn()
            }
        }
    }
}
