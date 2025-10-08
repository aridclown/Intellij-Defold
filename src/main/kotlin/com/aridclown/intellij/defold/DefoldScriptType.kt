package com.aridclown.intellij.defold

enum class DefoldScriptType(val extension: String) {
    LUA("lua"),
    SCRIPT("script"),
    GUI_SCRIPT("gui_script"),
    RENDER_SCRIPT("render_script"),
    EDITOR_SCRIPT("editor_script");

    companion object {
        fun fromExtension(extension: String?) = entries.firstOrNull { it.extension == extension }
    }
}