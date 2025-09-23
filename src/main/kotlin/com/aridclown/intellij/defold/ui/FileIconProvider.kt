package com.aridclown.intellij.defold.ui

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.aridclown.intellij.defold.DefoldScriptType.*
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon
import kotlin.collections.get

private val FILE_EXTENSION_TO_ICON = mapOf(
    // Specific file names
    GAME_PROJECT_FILE to "game_project",

    // Scripts
    LUA to "script",
    SCRIPT to "script_type",
    EDITOR_SCRIPT to "script_type",
    GUI_SCRIPT to "script_type",
    RENDER_SCRIPT to "script_type",

    // Shaders
    "fp" to "fragment_shader",
    "vp" to "vertex_shader",
    "glsl" to "vertex_shader",
    "cp" to "vertex_shader",
    "compute" to "vertex_shader",

    // Assets and resources
    "animationset" to "animation_set",
    "appmanifest" to "app_manifest",
    "atlas" to "atlas",
    "buffer" to "texture",
    "camera" to "camera",
    "collection" to "collection",
    "collectionfactory" to "collection_factory",
    "collectionproxy" to "collection_proxy",
    "collisionobject" to "collision_object",
    "collisiongroups" to "collision_group",
    "cubemap" to "cubemap",
    "display_profiles" to "display_profiles",
    "displayprofiles" to "display_profiles",
    "emitter" to "particlefx_emitter",
    "factory" to "factory",
    "font" to "font",
    "gamepads" to "gamepad",
    "go" to "game_object",
    "gui" to "gui",
    "input_binding" to "input_binding",
    "label" to "gui_text_node",
    "material" to "material",
    "mesh" to "mesh",
    "model" to "model",
    "particlefx" to "particlefx",
    "render" to "render",
    "render_target" to "layers",
    "sound" to "sound",
    "sprite" to "sprite",
    "texture_profiles" to "texture_profile",
    "textureprofiles" to "texture_profile",
    "tilemap" to "tilemap",
    "tilesource" to "tilesource"
)

class DefoldFileIconProvider : FileIconProvider {

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        // Check for specific file names first
        FILE_EXTENSION_TO_ICON[file.name]?.let { iconName ->
            return DefoldIcons.getDefoldIconByName(iconName)
        }

        // Then check by extension
        val extension = file.extension?.lowercase() ?: return null
        val iconName = FILE_EXTENSION_TO_ICON[extension] ?: return null
        return DefoldIcons.getDefoldIconByName(iconName)
    }
}
