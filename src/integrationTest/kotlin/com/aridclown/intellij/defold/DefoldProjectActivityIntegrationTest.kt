package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldProjectService
import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.aridclown.intellij.defold.DefoldProjectService.Companion.rootProjectFolder
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
@TestFixtures
class DefoldProjectActivityIntegrationTest {
    private val projectPathFixture = tempPathFixture()
    private val projectFixture = projectFixture(projectPathFixture, openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture(projectPathFixture)
    private val mockManager = mockk<DefoldAnnotationsManager>(relaxed = true)

    private lateinit var project: Project
    private lateinit var module: Module
    private lateinit var contentRoot: VirtualFile
    private lateinit var gameProjectFile: VirtualFile

    @BeforeEach
    fun setUp() {
        val rootDir = projectPathFixture.get().also(::createGameProjectFile)

        project = projectFixture.get()
        module = moduleFixture.get()

        contentRoot = refreshVirtualFile(rootDir)
        gameProjectFile = refreshVirtualFile(rootDir.resolve(GAME_PROJECT_FILE))

        mockkObject(DefoldAnnotationsManager.Companion)
        coJustRun { mockManager.ensureAnnotationsAttached() }
        every { DefoldAnnotationsManager.getInstance(any()) } returns mockManager
        mockkObject(DependencyResolver)
        coEvery { DependencyResolver.resolve(any(), any()) } returns Unit
    }

    @Test
    fun `should activate Defold tooling when game project present`(): Unit = timeoutRunBlocking {
        initContentEntries(module, contentRoot)

        DefoldProjectActivity().execute(project)

        val service = project.defoldProjectService()
        assertThat(project.isDefoldProject).isTrue
        assertThat(project.rootProjectFolder).isEqualTo(contentRoot)
        assertThat(service.gameProjectFile).isEqualTo(gameProjectFile)

        coVerify(exactly = 1) { mockManager.ensureAnnotationsAttached() }
        coVerify(exactly = 1) { DependencyResolver.resolve(project, any()) }
    }

    @Test
    fun `should resolve dependencies on project open`(): Unit = timeoutRunBlocking {
        initContentEntries(module, contentRoot)

        DefoldProjectActivity().execute(project)

        coVerify(exactly = 1) { DependencyResolver.resolve(project, any()) }
    }

    @Test
    fun `should detect Defold project with no content roots yet`(): Unit = timeoutRunBlocking {
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.contentEntries.toList().forEach(model::removeContentEntry)
        }

        DefoldProjectActivity().execute(project)

        val service = project.defoldProjectService()
        assertThat(service.gameProjectFile).isEqualTo(gameProjectFile)
        assertThat(project.isDefoldProject).isTrue
    }

    @Test
    fun `should configure exclude patterns for standard folders`(): Unit = timeoutRunBlocking {
        initContentEntries(module, contentRoot)

        DefoldProjectActivity().execute(project)

        val excludePatterns =
            ModuleRootManager
                .getInstance(module)
                .contentEntries
                .first()
                .excludePatterns

        assertThat(excludePatterns).containsAll(listOf(".git", ".idea", "build", ".internal", "debugger"))
    }

    @Test
    fun `should mark content root as source folder`(): Unit = timeoutRunBlocking {
        initContentEntries(module, contentRoot)

        DefoldProjectActivity().execute(project)

        assertThat(contentRootIsSourcesRoot(module, contentRoot)).isTrue
    }

    @Test
    fun `should register Defold script file type patterns`(): Unit = timeoutRunBlocking {
        initContentEntries(module, contentRoot)

        DefoldProjectActivity().execute(project)

        val fileTypeManager = FileTypeManager.getInstance()

        DefoldScriptType.entries.forEach { entry ->
            val fileType = fileTypeManager.getFileTypeByFileName("test.${entry.extension}")
            val associations = fileTypeManager.getAssociations(fileType)

            assertThat(fileType)
                .extracting { it.defaultExtension }
                .isEqualTo("lua")
            assertThat(associations)
                .extracting<String> { it.presentableString }
                .contains("*.${entry.extension}")
        }
    }

    @Test
    fun `should create project icon in idea directory`(): Unit = timeoutRunBlocking {
        initContentEntries(module, contentRoot)
        val ideaDir = projectPathFixture.get().resolve(".idea")
        Files.createDirectories(ideaDir)

        DefoldProjectActivity().execute(project)

        val iconFile = ideaDir.resolve("icon.png")
        assertThat(Files.exists(iconFile)).isTrue
        assertThat(Files.size(iconFile)).isGreaterThan(0)
    }

    @Test
    fun `should not overwrite existing project icon`(): Unit = timeoutRunBlocking {
        initContentEntries(module, contentRoot)
        val ideaDir = projectPathFixture.get().resolve(".idea")
        Files.createDirectories(ideaDir)
        val iconFile = ideaDir.resolve("icon.png")
        Files.writeString(iconFile, "existing content")

        DefoldProjectActivity().execute(project)

        assertThat(Files.readString(iconFile)).isEqualTo("existing content")
    }

    private fun createGameProjectFile(projectDir: Path) {
        val gameProjectPath = projectDir.resolve(GAME_PROJECT_FILE)
        if (Files.exists(gameProjectPath)) return

        Files.createFile(gameProjectPath)
    }

    private fun initContentEntries(
        module: Module,
        contentRoot: VirtualFile
    ) = ModuleRootModificationUtil.updateModel(module) { model ->
        model.contentEntries.find { it.file == contentRoot }
            ?: model.addContentEntry(contentRoot)
    }

    private fun contentRootIsSourcesRoot(
        module: Module,
        contentRoot: VirtualFile
    ): Boolean = ModuleRootManager.getInstance(module).contentEntries.any { entry ->
        entry.file == contentRoot &&
            entry.sourceFolders.any { folder ->
                folder.file == contentRoot && !folder.isTestSource
            }
    }

    private fun refreshVirtualFile(path: Path): VirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
        ?: error("Virtual file not found for path: $path")
}
