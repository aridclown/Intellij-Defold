package com.aridclown.intellij.defold.ui

import com.aridclown.intellij.defold.DefoldScriptType.*
import com.intellij.openapi.util.IconLoader.getIcon
import javax.swing.Icon

private val FILE_EXTENSION_TO_ICON = mapOf(
    "project" to "game_project",

    // Scripts
    LUA.extension to "script",
    SCRIPT.extension to "script_type",
    EDITOR_SCRIPT.extension to "script_type",
    GUI_SCRIPT.extension to "script_type",
    RENDER_SCRIPT.extension to "script_type",

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

object DefoldIcons {

    @JvmField
    val defoldIcon = icon("logo.svg")

    @JvmField
    val defoldBlueIcon = icon("logo-blue.png")

    private val iconCache = mutableMapOf<String, Icon?>()

    /**
     * Get icon for a given file extension.
     */
    fun String.toIcon(): Icon? = FILE_EXTENSION_TO_ICON[this.lowercase()]
        ?.let(::getDefoldIconByName)

    fun getDefoldIconByName(name: String): Icon? = iconCache.getOrPut(name) {
        defoldIcon("$name.svg")
    }

    private fun icon(name: String) = getIcon("/icons/$name", javaClass)

    private fun defoldIcon(name: String) = getIcon("/icons/defold/$name", javaClass)
}