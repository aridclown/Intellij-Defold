package com.aridclown.intellij.defold.atlas

import com.aridclown.intellij.defold.util.DefoldIcons
import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import javax.swing.Icon

class AtlasFileType private constructor() : LanguageFileType(PlainTextLanguage.INSTANCE) {

    companion object {
        @JvmField
        val INSTANCE = AtlasFileType()
    }

    override fun getName(): String = "Defold Atlas"

    override fun getDescription(): String = "Defold atlas resource"

    override fun getDefaultExtension(): String = "atlas"

    override fun getIcon(): Icon? = DefoldIcons.getDefoldIconByName("atlas")
}
