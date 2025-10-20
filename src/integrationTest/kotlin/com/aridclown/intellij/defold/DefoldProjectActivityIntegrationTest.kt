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
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
@TestFixtures
class DefoldProjectActivityIntegrationTest {

    private val projectPathFixture = tempPathFixture()
    private val projectFixture = projectFixture(projectPathFixture, openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture(projectPathFixture)

    @AfterEach
    fun tearDown() {
        unmockkObject(DefoldAnnotationsManager)
    }

    @Test
    fun `should activate Defold tooling when game project present`(): Unit = timeoutRunBlocking {
        val rootDir = projectPathFixture.get()
            .also(::createGameProjectFile)

        val project = projectFixture.get()
        val module = moduleFixture.get()

        val contentRoot = refreshVirtualFile(rootDir)
        val gameProjectFile = refreshVirtualFile(rootDir.resolve(GAME_PROJECT_FILE))

        initContentEntries(module, contentRoot)

        replaceDefoldService(project)

        mockkObject(DefoldAnnotationsManager)
        coJustRun { DefoldAnnotationsManager.ensureAnnotationsAttached(any(), any()) }

        DefoldProjectActivity().execute(project)

        val service = project.defoldProjectService()
        assertThat(project.isDefoldProject)
            .describedAs("Defold project file should be detected")
            .isTrue()
        assertThat(project.rootProjectFolder)
            .describedAs("Defold project folder should match content root")
            .isEqualTo(contentRoot)
        assertThat(service.gameProjectFile)
            .describedAs("Game project file should be registered")
            .isEqualTo(gameProjectFile)

        coVerify(exactly = 1) { DefoldAnnotationsManager.ensureAnnotationsAttached(project, any()) }

        val notifications = NotificationsManager.getNotificationsManager()
            .getNotificationsOfType(Notification::class.java, project)

        assertThat(notifications)
            .describedAs("Defold detection notification should be shown")
            .anySatisfy { notification ->
                assertThat(notification.title).isEqualTo("Defold project detected")
                assertThat(notification.content).startsWith("Defold project detected")
            }

        assertThat(contentRootIsSourcesRoot(module, contentRoot))
            .describedAs("ensureRootIsSourcesRoot should add the project root as a sources root")
            .isTrue()
    }

    @Test
    fun `should detect Defold project with no content roots yet`(): Unit = timeoutRunBlocking {
        val rootDir = projectPathFixture.get()
            .also(::createGameProjectFile)

        val project = projectFixture.get()
        val module = moduleFixture.get()

        ModuleRootModificationUtil.updateModel(module) { model ->
            model.contentEntries.toList().forEach(model::removeContentEntry)
        }

        refreshVirtualFile(rootDir)
        val gameProjectFile = refreshVirtualFile(rootDir.resolve(GAME_PROJECT_FILE))

        replaceDefoldService(project)

        mockkObject(DefoldAnnotationsManager)
        coJustRun { DefoldAnnotationsManager.ensureAnnotationsAttached(any(), any()) }

        DefoldProjectActivity().execute(project)

        val service = project.defoldProjectService()
        assertThat(service.gameProjectFile)
            .describedAs("Game project file should be detected from the project base directory")
            .isEqualTo(gameProjectFile)
        assertThat(project.isDefoldProject)
            .describedAs("Project should be treated as Defold even before content roots are configured")
            .isTrue()
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
        val utilClass = Class.forName("com.intellij.testFramework.ServiceContainerUtil")
        val componentManagerClass = Class.forName("com.intellij.openapi.components.ComponentManager")
        val disposableClass = Class.forName("com.intellij.openapi.Disposable")
        val method = utilClass.getMethod(
            "replaceService",
            componentManagerClass,
            Class::class.java,
            Any::class.java,
            disposableClass
        )

        method.invoke(null, project, DefoldProjectService::class.java, DefoldProjectService(project), project)
    }
}