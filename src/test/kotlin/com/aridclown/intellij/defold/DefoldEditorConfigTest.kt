package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.settings.DefoldSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DefoldEditorConfigTest {

    @BeforeEach
    fun resetInstallPathOverride() {
        DefoldDefaults.clearStoredInstallPath()
    }

    @ParameterizedTest
    @CsvSource(
        "Windows 11,WINDOWS",
        "Windows 10,WINDOWS",
        "Darwin,MACOS",
        "Mac OS X,MACOS",
        "Linux,LINUX",
        "Unknown OS,UNKNOWN"
    )
    fun `should detect current OS correctly`(osName: String, expected: Platform) {
        System.setProperty("os.name", osName)
        assertThat(Platform.current()).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "Windows 11,C:\\Program Files\\Defold",
        "Darwin,/Applications/Defold.app",
        "Linux,/usr/bin/Defold"
    )
    fun `should provide correct install paths per platform`(osName: String, expectedPath: String) {
        System.setProperty("os.name", osName)
        assertThat(DefoldDefaults.getDefoldInstallPath()).isEqualTo(expectedPath)
    }

    @Test
    fun `should return stored install path when configured`() {
        System.setProperty("os.name", "Windows 11")
        val customPath = "D:/Apps/Defold"

        DefoldSettings.getInstance().setInstallPath(customPath)

        assertThat(DefoldDefaults.getDefoldInstallPath()).isEqualTo(customPath)
    }

    @Test
    fun `should return current config for Windows`() {
        System.setProperty("os.name", "Windows 11")

        assertThat(LaunchConfigs.getCurrent())
            .extracting(
                LaunchConfigs.Config::buildPlatform,
                LaunchConfigs.Config::libexecBinPath,
                LaunchConfigs.Config::executable
            )
            .containsExactly(
                "x86_64-win32",
                "libexec/x86_64-win32",
                "dmengine.exe"
            )
    }

    @Test
    fun `should return current config for macOS`() {
        System.setProperty("os.name", "Mac OS X")
        val config = LaunchConfigs.getCurrent()

        // Architecture is detected at LaunchConfigs object initialization,
        // so we verify the config matches the current system architecture
        assertThat(config.executable).isEqualTo("dmengine")
        assertThat(config.buildPlatform).isIn("arm64-osx", "x86_64-osx")
        assertThat(config.libexecBinPath).isIn("libexec/arm64-macos", "libexec/x86_64-macos")
    }

    @Test
    fun `should return current config for Linux`() {
        System.setProperty("os.name", "Linux")

        assertThat(LaunchConfigs.getCurrent())
            .extracting(
                LaunchConfigs.Config::buildPlatform,
                LaunchConfigs.Config::libexecBinPath,
                LaunchConfigs.Config::executable
            )
            .containsExactly(
                "x86_64-linux",
                "libexec/x86_64-linux",
                "dmengine"
            )
    }
}
