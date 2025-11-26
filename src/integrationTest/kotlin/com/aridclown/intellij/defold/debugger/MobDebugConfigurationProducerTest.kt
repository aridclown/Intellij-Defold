package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldConstants
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat

class MobDebugConfigurationProducerTest : BasePlatformTestCase() {
    private lateinit var producer: MobDebugConfigurationProducer

    override fun setUp() {
        super.setUp()
        producer = MobDebugConfigurationProducer()
    }

    fun `test configuration factory is DefoldMobDebugConfigurationType`() {
        val factory = producer.configurationFactory

        assertThat(factory.type).isInstanceOf(DefoldMobDebugConfigurationType::class.java)
    }

    fun `test setup configuration from context when project root directory is selected in Defold project`(): Unit = withDefoldProject {
        val (result, configuration) = setupConfiguration(rootDirectory())

        assertThat(result).isTrue
        assertThat(configuration.name).isEqualTo(project.name)
        assertThat(configuration.localRoot).isEqualTo(project.basePath)
        assertThat(configuration.remoteRoot).isEqualTo("")
    }

    fun `test setup configuration from context preserves existing remote root`(): Unit = withDefoldProject {
        val configuration =
            createConfiguration().apply {
                remoteRoot = "/some/remote/path"
            }

        val (result, updated) = setupConfiguration(rootDirectory(), configuration)

        assertThat(result).isTrue
        assertThat(updated.remoteRoot).isEqualTo("/some/remote/path")
    }

    fun `test setup configuration from context returns false when project base path is null`() {
        val projectWithoutBasePath =
            mockk<Project> {
                every { basePath } returns null
            }
        val context =
            mockk<ConfigurationContext> {
                every { project } returns projectWithoutBasePath
            }

        val configuration = createConfiguration()
        val sourceElement = Ref<PsiElement>()

        val result = producer.setupConfigurationFromContext(configuration, context, sourceElement)

        assertThat(result).isFalse
    }

    fun `test setup configuration from context returns false when virtual file is null`() {
        val dataContext = mockk<DataContext>()
        every { dataContext.getData(VIRTUAL_FILE) } returns null

        val context = mockk<ConfigurationContext>(relaxed = true)
        every { context.project } returns project
        every { context.location } returns null
        every { context.dataContext } returns dataContext
        val configuration = createConfiguration()
        val sourceElement = Ref<PsiElement>()

        val result = producer.setupConfigurationFromContext(configuration, context, sourceElement)

        assertThat(result).isFalse
    }

    fun `test setup configuration from context returns false when virtual file is not a directory`() {
        withDefoldProject {
            val (result, _) = setupConfiguration(nonDirectory())

            assertThat(result).isFalse
        }
    }

    fun `test setup configuration from context returns false when virtual file is not project root`() {
        withDefoldProject {
            val (result, _) = setupConfiguration(nonProjectRoot())

            assertThat(result).isFalse
        }
    }

    fun `test setup configuration from context returns false when not a Defold project`() {
        val (result, _) = setupConfiguration(rootDirectory())

        assertThat(result).isFalse
    }

    fun `test is configuration from context returns true when configuration matches context`() {
        val configuration =
            createConfiguration().apply {
                localRoot = project.basePath!!
            }

        val result = isFromContext(rootDirectory(), configuration)

        assertThat(result).isTrue
    }

    fun `test is configuration from context returns false when local root does not match`() {
        val configuration =
            createConfiguration().apply {
                localRoot = "/different/path"
            }

        val result = isFromContext(rootDirectory(), configuration)

        assertThat(result).isFalse
    }

    fun `test is configuration from context returns false when virtual file is not directory`() {
        val configuration =
            createConfiguration().apply {
                localRoot = project.basePath!!
            }

        val result = isFromContext(nonDirectory(), configuration)

        assertThat(result).isFalse
    }

    fun `test is configuration from context returns false when virtual file is not project root`() {
        val configuration =
            createConfiguration().apply {
                localRoot = project.basePath!!
            }

        val result = isFromContext(nonProjectRoot(), configuration)

        assertThat(result).isFalse
    }

    fun `test virtual file is resolved from location first`(): Unit = withDefoldProject {
        val (result, _) = setupConfiguration(rootDirectory())

        assertThat(result).isTrue
    }

    fun `test virtual file is resolved from data context when location is null`(): Unit = withDefoldProject {
        val (result, _) = setupConfiguration(rootDirectory(), contextFactory = ::contextFromData)

        assertThat(result).isTrue
    }

    fun `test project root matches using canonical path`(): Unit = withDefoldProject {
        val (result, _) = setupConfiguration(canonicalRoot())

        assertThat(result).isTrue
    }

    private fun setupConfiguration(
        virtualFile: VirtualFile,
        configuration: MobDebugRunConfiguration = createConfiguration(),
        contextFactory: (VirtualFile) -> ConfigurationContext = ::contextWithLocation
    ): Pair<Boolean, MobDebugRunConfiguration> {
        val context = contextFactory(virtualFile)
        val result = producer.setupConfigurationFromContext(configuration, context, Ref<PsiElement>())
        return result to configuration
    }

    private fun contextWithLocation(file: VirtualFile): ConfigurationContext = mockk {
        every { project } returns this@MobDebugConfigurationProducerTest.project
        every { location } returns
            mockk {
                every { virtualFile } returns file
            }
    }

    private fun isFromContext(
        virtualFile: VirtualFile,
        configuration: MobDebugRunConfiguration
    ): Boolean = producer.isConfigurationFromContext(configuration, contextWithLocation(virtualFile))

    private fun contextFromData(file: VirtualFile): ConfigurationContext = mockk {
        every { project } returns this@MobDebugConfigurationProducerTest.project
        every { location } returns null
        every { dataContext } returns
            mockk {
                every { getData(VIRTUAL_FILE) } returns file
            }
    }

    private fun rootDirectory(): VirtualFile = directory(
        path = project.basePath!!,
        canonicalPath = project.basePath!!
    )

    private fun nonProjectRoot(): VirtualFile = directory(
        path = "/some/other/path"
    )

    private fun canonicalRoot(): VirtualFile = directory(
        path = "/different/path",
        canonicalPath = project.basePath!!
    )

    private fun nonDirectory(): VirtualFile = mockk {
        every { isDirectory } returns false
    }

    private fun directory(
        path: String,
        canonicalPath: String = path
    ): VirtualFile = mockk {
        every { isDirectory } returns true
        every { this@mockk.path } returns path
        every { this@mockk.canonicalPath } returns canonicalPath
    }

    private fun <T> withDefoldProject(block: () -> T): T {
        val marker = myFixture.tempDirFixture.createFile(DefoldConstants.GAME_PROJECT_FILE)

        return try {
            block()
        } finally {
            runWriteAction { marker.delete(this@MobDebugConfigurationProducerTest) }
        }
    }

    private fun createConfiguration() = MobDebugRunConfiguration(project, producer.configurationFactory)
}
