package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldProjectService
import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.aridclown.intellij.defold.DefoldProjectService.Companion.rootProjectFolder
import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
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
import com.intellij.testFramework.replaceService
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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

        replaceDefoldService(project)

        mockkObject(DefoldAnnotationsManager)
        coJustRun { DefoldAnnotationsManager.ensureAnnotationsAttached(any(), any()) }
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(DefoldAnnotationsManager)
    }

    @Test
    fun `should activate Defold tooling when game project present`(): Unit = timeoutRunBlocking {
        initContentEntries(module, contentRoot)

        DefoldProjectActivity().execute(project)

        val service = project.defoldProjectService()
        assertThat(project.isDefoldProject).isTrue()
        assertThat(project.rootProjectFolder).isEqualTo(contentRoot)
        assertThat(service.gameProjectFile).isEqualTo(gameProjectFile)

        coVerify(exactly = 1) { DefoldAnnotationsManager.ensureAnnotationsAttached(project, any()) }

        NotificationsManager.getNotificationsManager()
            .getNotificationsOfType(Notification::class.java, project)

        assertThat(contentRootIsSourcesRoot(module, contentRoot)).isTrue()
    }

    @Test
    fun `should detect Defold project with no content roots yet`(): Unit = timeoutRunBlocking {
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.contentEntries.toList().forEach(model::removeContentEntry)
        }

        DefoldProjectActivity().execute(project)

        val service = project.defoldProjectService()
        assertThat(service.gameProjectFile).isEqualTo(gameProjectFile)
        assertThat(project.isDefoldProject).isTrue()
    }

    @Test
    fun `should configure exclude patterns for standard folders`(): Unit = timeoutRunBlocking {
        initContentEntries(module, contentRoot)

        DefoldProjectActivity().execute(project)

        val excludePatterns = ModuleRootManager.getInstance(module)
            .contentEntries.first().excludePatterns

        assertThat(excludePatterns).containsAll(listOf(".git", ".idea", "build", ".internal", "debugger"))
    }

    @Test
    fun `should mark content root as source folder`(): Unit = timeoutRunBlocking {
        initContentEntries(module, contentRoot)

        DefoldProjectActivity().execute(project)

        assertThat(contentRootIsSourcesRoot(module, contentRoot)).isTrue()
    }

    private fun createGameProjectFile(projectDir: Path) {
        val gameProjectPath = projectDir.resolve(GAME_PROJECT_FILE)
        if (Files.exists(gameProjectPath)) return

        Files.createFile(gameProjectPath)
    }

    private fun initContentEntries(module: Module, contentRoot: VirtualFile) =
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.contentEntries.find { it.file == contentRoot }
                ?: model.addContentEntry(contentRoot)
        }

    private fun contentRootIsSourcesRoot(module: Module, contentRoot: VirtualFile): Boolean {
        val model = ModuleRootManager.getInstance(module)
        return model.contentEntries.any { entry ->
            entry.file == contentRoot && entry.sourceFolders.any { folder ->
                folder.file == contentRoot && !folder.isTestSource
            }
        }
    }

    private fun refreshVirtualFile(path: Path): VirtualFile =
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
            ?: error("Virtual file not found for path: $path")

    private fun replaceDefoldService(project: Project) {
        project.replaceService(
            DefoldProjectService::class.java,
            DefoldProjectService(project),
            project
        )
    }
}
