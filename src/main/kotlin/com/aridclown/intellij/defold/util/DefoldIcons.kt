package com.aridclown.intellij.defold.util

import com.intellij.openapi.util.IconLoader.getIcon

object DefoldIcons {
    @JvmField
    val defoldIcon = getIcon("/icons/icon-ver-outline-white.png", javaClass)

    val animationSet = icon("animation_set.svg")
    val appManifest = icon("app_manifest.svg")
    val atlas = icon("atlas.svg")
    val camera = icon("camera.svg")
    val collection = icon("collection.svg")
    val collectionFactory = icon("collection_factory.svg")
    val collectionProxy = icon("collection_proxy.svg")
    val collisionGroup = icon("collision_group.svg")
    val collisionObject = icon("collision_object.svg")
    val cubemap = icon("cubemap.svg")
    val displayProfiles = icon("display_profiles.svg")
    val factory = icon("factory.svg")
    val font = icon("font.svg")
    val fragmentShader = icon("fragment_shader.svg")
    val gameObject = icon("game_object.svg")
    val gameProject = icon("game_project.svg")
    val gamepad = icon("gamepad.svg")
    val gui = icon("gui.svg")
    val guiTextNode = icon("gui_text_node.svg")
    val inputBinding = icon("input_binding.svg")
    val layers = icon("layers.svg")
    val material = icon("material.svg")
    val mesh = icon("mesh.svg")
    val model = icon("model.svg")
    val particlefx = icon("particlefx.svg")
    val particlefxEmitter = icon("particlefx_emitter.svg")
    val render = icon("render.svg")
    val sound = icon("sound.svg")
    val script = icon("script.svg")
    val scriptType = icon("script_type.svg")
    val sprite = icon("sprite.svg")
    val texture = icon("texture.svg")
    val textureProfile = icon("texture_profile.svg")
    val tilemap = icon("tilemap.svg")
    val tilesource = icon("tilesource.svg")
    val unknown = icon("unknown.svg")
    val vertexShader = icon("vertex_shader.svg")

    private fun icon(name: String) = getIcon("/icons/defold/$name", javaClass)
}
