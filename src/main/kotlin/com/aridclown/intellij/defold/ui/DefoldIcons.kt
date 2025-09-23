package com.aridclown.intellij.defold.ui

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object DefoldIcons {
    @JvmField
    val defoldIcon = icon("logo.svg")

    @JvmField
    val defoldOutlineDarkIcon = icon("logo-ver-outline-dark.svg")
    @JvmField
    val defoldOutlineLightIcon = icon("logo-ver-outline-white.svg")

    private val iconCache = mutableMapOf<String, Icon?>()

    fun getDefoldIconByName(name: String): Icon? = iconCache.getOrPut(name) {
        defoldIcon("$name.svg")
    }

    private fun icon(name: String) = IconLoader.getIcon("/icons/$name", javaClass)

    private fun defoldIcon(name: String) = IconLoader.getIcon("/icons/defold/$name", javaClass)
}