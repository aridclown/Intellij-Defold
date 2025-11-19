package com.aridclown.intellij.defold.atlas

import com.aridclown.intellij.defold.util.DefoldIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import javax.swing.Icon

object AtlasFileType : LanguageFileType(PlainTextLanguage.INSTANCE) {
    override fun getName(): String = "Defold Atlas"
    override fun getDescription(): String = "Defold atlas file"
    override fun getDefaultExtension(): String = "atlas"
    override fun getIcon(): Icon? = DefoldIcons.getDefoldIconByName("atlas")
}
