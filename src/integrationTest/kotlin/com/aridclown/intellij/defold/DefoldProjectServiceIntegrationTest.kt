package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.GAME_PROJECT_FILE
import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldProjectService
import com.aridclown.intellij.defold.DefoldProjectService.Companion.defoldVersion
import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.aridclown.intellij.defold.DefoldProjectService.Companion.rootProjectFolder
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
@TestFixtures
class DefoldProjectServiceIntegrationTest {

    private val projectPathFixture = tempPathFixture()
    private val projectFixture = projectFixture(projectPathFixture, openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture(projectPathFixture)

    private lateinit var project: Project
    private lateinit var module: Module

    @BeforeEach
    fun setUp() {
        project = projectFixture.get()
        module = moduleFixture.get()
    }

    @Test
    fun `provides access to game project configuration file when present`() {
        val rootDir = projectPathFixture.get()
        createGameProjectFile(rootDir)
        refreshVirtualFile(rootDir.resolve(GAME_PROJECT_FILE))

        val service = project.defoldProjectService()

        assertThat(service.gameProjectFile).isNotNull()
        assertThat(service.gameProjectFile?.name).isEqualTo(GAME_PROJECT_FILE)
    }

    @Test
    fun `handles missing game project configuration gracefully`() {
        val service = project.defoldProjectService()

        assertThat(service.gameProjectFile).isNull()
    }

    @Test
    fun `identifies project as Defold when game project exists`() {
        val rootDir = projectPathFixture.get()
        createGameProjectFile(rootDir)
        refreshVirtualFile(rootDir.resolve(GAME_PROJECT_FILE))

        assertThat(project.isDefoldProject).isTrue
    }

    @Test
    fun `identifies project as non-Defold when game project missing`() {
        assertThat(project.isDefoldProject).isFalse
    }

    @Test
    fun `provides project root directory when Defold project detected`() {
        val rootDir = projectPathFixture.get()
        createGameProjectFile(rootDir)
        refreshVirtualFile(rootDir.resolve(GAME_PROJECT_FILE))

        assertThat(project.rootProjectFolder).isNotNull()
        assertThat(project.rootProjectFolder?.path).isEqualTo(rootDir.toString())
    }

    @Test
    fun `handles missing project root gracefully`() {
        assertThat(project.rootProjectFolder).isNull()
    }

    @Test
    fun `provides Defold editor configuration`() {
        val rootDir = projectPathFixture.get()
        createGameProjectFile(rootDir)
        refreshVirtualFile(rootDir.resolve(GAME_PROJECT_FILE))

        val service = project.defoldProjectService()

        assertThat(service.editorConfig).isNotNull()
    }

    @Test
    fun `provides Defold engine version information`() {
        val rootDir = projectPathFixture.get()
        createGameProjectFile(rootDir)
        refreshVirtualFile(rootDir.resolve(GAME_PROJECT_FILE))

        val version = project.defoldVersion
        assertThat(version).isNotNull()
    }

    @Test
    fun `handles null project gracefully when accessing version`() {
        val nullProject: Project? = null
        assertThat(nullProject.defoldVersion).isNull()
    }

    @Test
    fun `maintains single service instance per project`() {
        val service1 = project.defoldProjectService()
        val service2 = project.defoldProjectService()

        assertThat(service1).isSameAs(service2)
    }

    @Test
    fun `provides consistent project metadata across multiple accesses`() {
        val rootDir = projectPathFixture.get()
        createGameProjectFile(rootDir)
        refreshVirtualFile(rootDir.resolve(GAME_PROJECT_FILE))

        val service = project.defoldProjectService()
        val file1 = service.gameProjectFile
        val file2 = service.gameProjectFile

        assertThat(file1).isNotNull()
        assertThat(file1).isEqualTo(file2)
    }

    private fun createGameProjectFile(projectDir: Path) {
        val gameProjectPath = projectDir.resolve(GAME_PROJECT_FILE)
        if (Files.exists(gameProjectPath)) return
        Files.createFile(gameProjectPath)
    }

    private fun refreshVirtualFile(path: Path) =
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
            ?: error("Virtual file not found for path: $path")
}