package com.aridclown.intellij.defold.templates

import com.aridclown.intellij.defold.DefoldScriptType
import com.aridclown.intellij.defold.ui.getIconByType
import javax.swing.Icon

enum class DefoldScriptTemplate(
    val displayName: String,
    val templateName: String,
    val icon: Icon?
) {
    SCRIPT(
        displayName = "Script",
        templateName = "Script.script",
        icon = getIconByType(DefoldScriptType.SCRIPT)
    ),
    GUI_SCRIPT(
        displayName = "GUI Script",
        templateName = "GUI Script.gui_script",
        icon = getIconByType(DefoldScriptType.GUI_SCRIPT)
    ),
    RENDER_SCRIPT(
        displayName = "Render Script",
        templateName = "Render Script.render_script",
        icon = getIconByType(DefoldScriptType.RENDER_SCRIPT)
    ),
    EDITOR_SCRIPT(
        displayName = "Editor Script",
        templateName = "Editor Script.editor_script",
        icon = getIconByType(DefoldScriptType.EDITOR_SCRIPT)
    );
}
