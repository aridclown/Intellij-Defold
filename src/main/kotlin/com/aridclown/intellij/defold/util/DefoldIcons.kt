package com.aridclown.intellij.defold.util

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object DefoldIcons {
    @JvmField
    val defoldOutlineIcon = icon("/icons/icon-ver-outline-white.png")

    private val iconCache = mutableMapOf<String, Icon?>()

    fun getIconByName(name: String): Icon? = iconCache.getOrPut(name) {
        defoldIcon("$name.svg")
    }

    private fun icon(name: String) = IconLoader.getIcon(name, javaClass)

    private fun defoldIcon(name: String) = IconLoader.getIcon("/icons/defold/$name", javaClass)
}
