package com.aridclown.intellij.defold.templates

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DefoldFileTemplateGroupDescriptorFactoryTest {

    private val factory = DefoldFileTemplateGroupDescriptorFactory()

    @Test
    fun `includes template for each defold script type`() {
        val descriptor = factory.fileTemplatesDescriptor
        assertThat(descriptor.displayName).isEqualTo("Defold")

        val templateIcons = descriptor.templates.associateBy(
            { it.fileName },
            { it.icon }
        )

        assertThat(templateIcons)
            .containsExactlyEntriesOf(DefoldScriptTemplate.entries.associate {
                it.templateName to (it.icon ?: descriptor.icon)
            })
    }
}
