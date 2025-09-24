package com.aridclown.intellij.defold.templates

import com.aridclown.intellij.defold.ui.DefoldIcons
import com.intellij.icons.AllIcons
import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory

class DefoldFileTemplateGroupDescriptorFactory : FileTemplateGroupDescriptorFactory {
    override fun getFileTemplatesDescriptor(): FileTemplateGroupDescriptor {
        val groupIcon = DefoldIcons.defoldIcon ?: AllIcons.FileTypes.Any_type
        val group = FileTemplateGroupDescriptor("Defold", groupIcon)

        DefoldScriptTemplate.entries.forEach {
            val templateIcon = it.icon ?: groupIcon
            FileTemplateDescriptor(it.templateName, templateIcon)
                .let(group::addTemplate)
        }

        return group
    }
}
