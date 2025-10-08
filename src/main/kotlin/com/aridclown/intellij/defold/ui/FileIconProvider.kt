package com.aridclown.intellij.defold.ui

import com.aridclown.intellij.defold.ui.DefoldIcons.toIcon
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

class DefoldFileIconProvider : FileIconProvider {
    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? = file.extension?.toIcon()
}
