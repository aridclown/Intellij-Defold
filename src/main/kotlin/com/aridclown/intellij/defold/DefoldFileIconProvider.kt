package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.util.DefoldIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.*
import javax.swing.Icon

class DefoldFileIconProvider : FileIconProvider {

    private val iconByFileName: Map<String, Icon> = mapOf(
        DefoldConstants.GAME_PROJECT_FILE to DefoldIcons.gameProject
    )

    private val scriptExtensions = setOf("lua")
    private val scriptedResourceExtensions = setOf("script", "editor_script", "gui_script", "render_script")
    private val shaderExtensions = setOf("vp", "glsl", "cp", "compute")

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        iconByFileName[file.name]?.let { return it }

        val extension = file.extension?.lowercase(Locale.ROOT) ?: return null

        return when (extension) {
            in scriptExtensions -> DefoldIcons.script
            in scriptedResourceExtensions -> DefoldIcons.scriptType
            in shaderExtensions -> DefoldIcons.vertexShader
            "fp" -> DefoldIcons.fragmentShader
            "animationset" -> DefoldIcons.animationSet
            "appmanifest" -> DefoldIcons.appManifest
            "atlas" -> DefoldIcons.atlas
            "buffer" -> DefoldIcons.texture
            "camera" -> DefoldIcons.camera
            "collection" -> DefoldIcons.collection
            "collectionfactory" -> DefoldIcons.collectionFactory
            "collectionproxy" -> DefoldIcons.collectionProxy
            "collisionobject" -> DefoldIcons.collisionObject
            "collisiongroups" -> DefoldIcons.collisionGroup
            "cubemap" -> DefoldIcons.cubemap
            "display_profiles" -> DefoldIcons.displayProfiles
            "emitter" -> DefoldIcons.particlefxEmitter
            "factory" -> DefoldIcons.factory
            "font" -> DefoldIcons.font
            "gamepads" -> DefoldIcons.gamepad
            "go" -> DefoldIcons.gameObject
            "gui" -> DefoldIcons.gui
            "input_binding" -> DefoldIcons.inputBinding
            "label" -> DefoldIcons.guiTextNode
            "material" -> DefoldIcons.material
            "mesh" -> DefoldIcons.mesh
            "model" -> DefoldIcons.model
            "particlefx" -> DefoldIcons.particlefx
            "render" -> DefoldIcons.render
            "render_target" -> DefoldIcons.layers
            "sound" -> DefoldIcons.sound
            "sprite" -> DefoldIcons.sprite
            "texture_profiles" -> DefoldIcons.textureProfile
            "tilemap" -> DefoldIcons.tilemap
            "tilesource" -> DefoldIcons.tilesource
            "displayprofiles" -> DefoldIcons.displayProfiles
            "textureprofiles" -> DefoldIcons.textureProfile
            else -> null
        }
    }
}
