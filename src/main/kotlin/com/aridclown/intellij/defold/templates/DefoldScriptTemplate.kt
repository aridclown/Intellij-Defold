package com.aridclown.intellij.defold.templates

import com.aridclown.intellij.defold.DefoldScriptType
import com.aridclown.intellij.defold.ui.DefoldIcons.toIcon
import javax.swing.Icon

/**
 * Defold script file templates in order of popularity
 */
enum class DefoldScriptTemplate(
    val displayName: String,
    val templateName: String,
    val scriptType: DefoldScriptType
) {
    SCRIPT(
        displayName = "Script",
        templateName = "Script.script",
        scriptType = DefoldScriptType.SCRIPT
    ),
    GUI_SCRIPT(
        displayName = "GUI Script",
        templateName = "GUI Script.gui_script",
        scriptType = DefoldScriptType.GUI_SCRIPT
    ),
    RENDER_SCRIPT(
        displayName = "Render Script",
        templateName = "Render Script.render_script",
        scriptType = DefoldScriptType.RENDER_SCRIPT
    ),
    LUA(
        displayName = "Lua Module",
        templateName = "Lua Module.lua",
        scriptType = DefoldScriptType.LUA
    ),
    EDITOR_SCRIPT(
        displayName = "Editor Script",
        templateName = "Editor Script.editor_script",
        scriptType = DefoldScriptType.EDITOR_SCRIPT
    );

    val icon: Icon?
        get() = scriptType.extension.toIcon()
}