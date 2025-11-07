package com.aridclown.intellij.defold.debugger

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class DefoldRunConfigurationUtilIntegrationTest {

    private lateinit var project: Project
    private lateinit var runManager: RunManager

    @JvmField
    @RegisterExtension
    val projectModel = ProjectModelExtension()

    @BeforeEach
    fun setUp() {
        project = projectModel.project
        runManager = RunManager.getInstance(project)
    }

    @Test
    fun `creates new configuration when none exists`() {
        assertThat(runManager.allSettings).isEmpty()
        assertThat(runManager.selectedConfiguration).isNull()

        val settings = DefoldRunConfigurationUtil.getOrCreate(project)

        assertThat(settings.configuration).isInstanceOf(MobDebugRunConfiguration::class.java)
        assertThat(settings.name).isEqualTo("Defold")
        assertThat(runManager.allSettings).hasSize(1)
        assertThat(runManager.selectedConfiguration).isEqualTo(settings)
    }

    @Test
    fun `does not override selected configuration if one already exists`() {
        val type = ConfigurationTypeUtil.findConfigurationType(DefoldMobDebugConfigurationType::class.java)
        val factory = type.configurationFactories.single()
        val firstSettings = runManager.createConfiguration("DefoldTest", factory).apply {
            runManager.addConfiguration(this)
            runManager.selectedConfiguration = this
        }

        val secondSettings = DefoldRunConfigurationUtil.getOrCreate(project)

        assertThat(secondSettings).isSameAs(firstSettings)
        assertThat(secondSettings.name).isEqualTo("DefoldTest")
        assertThat(runManager.allSettings).hasSize(1)
        assertThat(runManager.selectedConfiguration).isEqualTo(firstSettings)
    }

    @Test
    fun `returns existing configuration when one exists`() {
        val firstSettings = DefoldRunConfigurationUtil.getOrCreate(project)
        val secondSettings = DefoldRunConfigurationUtil.getOrCreate(project)

        assertThat(firstSettings).isSameAs(secondSettings)
        assertThat(runManager.allSettings).hasSize(1)
    }
}
