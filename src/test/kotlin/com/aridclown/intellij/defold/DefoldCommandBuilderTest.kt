package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.Platform.*
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DefoldCommandBuilderTest {
    private var builder = DefoldCommandBuilder()

    @BeforeEach
    fun setUp() {
        mockkObject(Platform)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(Platform)
        clearAllMocks()
    }

    @Test
    fun `creates Windows command with executable and game project file`() {
        every { Platform.current() } returns WINDOWS

        val command = builder.createLaunchCommand("C:\\workspace\\myproject")

        assertThat(command.exePath).endsWith("Defold.exe")
        // Windows paths might have mixed separators depending on which OS tests run
        assertThat(command.parametersList.list.first()).contains("C:\\workspace\\myproject", "game.project")
    }

    @Test
    fun `creates macOS command with open or osascript`() {
        every { Platform.current() } returns MACOS

        val command = builder.createLaunchCommand("/workspace/myproject")

        assertThat(command.exePath).isIn("open", "osascript")
        assertThat(command.parametersList.list).isNotEmpty()
    }

    @Test
    fun `creates Linux command with xdg-open`() {
        every { Platform.current() } returns LINUX

        val command = builder.createLaunchCommand("/workspace/myproject")

        assertThat(command.exePath).isEqualTo("xdg-open")
        assertThat(command.parametersList.list).containsExactly("/workspace/myproject")
    }

    @Test
    fun `throws error for unknown platform`() {
        every { Platform.current() } returns UNKNOWN

        val exception =
            assertThrows<IllegalStateException> {
                builder.createLaunchCommand("/workspace/myproject")
            }

        assertThat(exception.message).contains("Unknown platform")
    }
}
