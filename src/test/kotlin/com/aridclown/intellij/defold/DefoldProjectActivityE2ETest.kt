package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.aridclown.intellij.defold.DefoldProjectService.Companion.getService
import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.project.Project
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
import org.jetbrains.kotlin.idea.util.sourceRoots
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.pathString

@TestApplication
@TestFixtures
class DefoldProjectActivityE2ETest {

    private val projectPathFixture = tempPathFixture()
    private val projectFixture = projectFixture(projectPathFixture, openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture(projectPathFixture, addPathToSourceRoot = true)

    @AfterEach
    fun tearDown() {
        unmockkObject(DefoldAnnotationsManager)
    }

    @Test
    fun `should activate Defold tooling when game project present`() = timeoutRunBlocking {
        val rootDir = projectPathFixture.get()
        Files.createFile(rootDir.resolve(GAME_PROJECT_FILE))

        val project = projectFixture.get()
        val module = moduleFixture.get()

        replaceDefoldService(project)

        mockkObject(DefoldAnnotationsManager)
        coJustRun { DefoldAnnotationsManager.ensureAnnotationsAttached(any(), any()) }

        DefoldProjectActivity().execute(project)

        val service = project.getService()
        assertTrue(service.isDefoldProject, "Defold project file should be detected")
        assertEquals(rootDir.pathString, service.rootProjectFolder?.path, "Defold project folder should match content root")
        assertNotNull(service.gameProjectFile, "Game project file should be registered")

        coVerify(exactly = 1) { DefoldAnnotationsManager.ensureAnnotationsAttached(any(), any()) }
        val notifications = NotificationsManager.getNotificationsManager()
            .getNotificationsOfType(Notification::class.java, project)

        val sourceRoot = module.sourceRoots.firstOrNull()
        assertEquals(service.rootProjectFolder, sourceRoot, "Root folder should match source root")

        assertTrue(
            notifications.any { it.title == "Defold project detected" && it.content.startsWith("Defold project detected (version") },
            "Defold detection notification should be shown"
        )
    }

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
