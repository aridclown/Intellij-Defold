package com.aridclown.intellij.defold.templates

import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.Icon

class CreateDefoldScriptFileActionTest {

    private val project = mockk<Project>(relaxed = true)
    private val directory = mockk<PsiDirectory>(relaxed = true)
    private val builder = mockk<CreateFileFromTemplateDialog.Builder>(relaxed = true)
    private val action = CreateDefoldScriptFileAction()

    @Test
    fun `dialog lists every defold script template`() {
        val names = mutableListOf<String>()
        val icons = mutableListOf<Icon?>()
        val templateNames = mutableListOf<String>()

        every { builder.addKind(any(), anyNullable(), any()) } answers {
            names += firstArg<String>()
            icons += secondArg<Icon?>()
            templateNames += thirdArg<String>()
            builder
        }

        action.buildDialog(project, directory, builder)

        assertThat(names).containsExactlyElementsOf(DefoldScriptTemplate.entries.map { it.displayName })
        assertThat(templateNames).containsExactlyElementsOf(DefoldScriptTemplate.entries.map { it.templateName })
        DefoldScriptTemplate.entries.forEachIndexed { index, template ->
            assertThat(icons[index]).isEqualTo(template.icon)
        }
    }

    @Test
    fun `dialog shows defold specific title`() {
        action.buildDialog(project, directory, builder)

        verify { builder.setTitle("New Defold Script File") }
    }

    @Test
    fun `action name describes defold script creation`() {
        val name = action.getActionName(directory, "player", "Script.script")

        assertThat(name).isEqualTo("Defold Script File")
    }
}
