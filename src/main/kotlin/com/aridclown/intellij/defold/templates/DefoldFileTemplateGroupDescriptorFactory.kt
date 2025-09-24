package com.aridclown.intellij.defold.templates

import com.aridclown.intellij.defold.ui.DefoldIcons
import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory

class DefoldFileTemplateGroupDescriptorFactory : FileTemplateGroupDescriptorFactory {
    override fun getFileTemplatesDescriptor(): FileTemplateGroupDescriptor {
        val group = FileTemplateGroupDescriptor("Defold", DefoldIcons.defoldIcon)

        DefoldScriptTemplate.entries.forEach {
            FileTemplateDescriptor(it.templateName, it.icon)
                .let(group::addTemplate)
        }

        return group
    }
}
