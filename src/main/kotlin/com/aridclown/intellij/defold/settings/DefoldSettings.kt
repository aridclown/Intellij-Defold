package com.aridclown.intellij.defold.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "DefoldSettings", storages = [Storage("defold.xml")])
class DefoldSettings : PersistentStateComponent<DefoldSettings.State> {
    data class State(
        var installPath: String? = null
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun installPath(): String? = state.installPath?.takeIf { it.isNotBlank() }

    fun setInstallPath(path: String) {
        state.installPath = path.trim()
    }

    fun clearInstallPath() {
        state.installPath = null
    }

    companion object {
        private val fallbackInstance by lazy { DefoldSettings() }

        fun getInstance(): DefoldSettings {
            val application = ApplicationManager.getApplication()
            return application?.getService(DefoldSettings::class.java) ?: fallbackInstance
        }
    }
}
