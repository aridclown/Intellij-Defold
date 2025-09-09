package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.Platform.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import org.ini4j.Ini
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists

enum class Platform() {
    MACOS,
    WINDOWS,
    LINUX,
    UNKNOWN;

    companion object {
        fun current(): Platform {
            val osName = System.getProperty("os.name").lowercase()
            return when {
                osName.contains("mac") || osName.contains("darwin") -> MACOS
                osName.contains("win") -> WINDOWS
                osName.contains("linux") -> LINUX
                else -> UNKNOWN
            }
        }

        fun fromOsName(osName: String): Platform = when (osName) {
            "win32" -> WINDOWS
            "darwin" -> MACOS
            "linux" -> LINUX
            else -> UNKNOWN
        }
    }
}

object DefoldDefaults {
    private val defoldInstallPath = mapOf(
        WINDOWS to "C:\\Program Files\\Defold",
        MACOS to "/Applications/Defold.app",
        LINUX to "/usr/bin/Defold"
    )

    private val defoldProcess = mapOf(
        WINDOWS to "Defold.exe",
        MACOS to "Defold",
        LINUX to "Defold"
    )

    fun getDefoldInstallPath(): String {
        val platform = Platform.current()
        return defoldInstallPath[platform] ?: throw IllegalArgumentException("Unsupported platform: $platform")
    }
}

object LaunchConfigs {
    private val osArch = System.getProperty("os.arch")
    private val isArm64 = listOf("aarch64", "arm64").any {
        osArch?.contains(it, true) == true
    }

    private val configs = mapOf(
        WINDOWS to Config(
            buildPlatform = "x86_64-win32",
            libexecBinPath = "libexec/x86_64-win32",
            executable = "dmengine.exe"
        ),
        MACOS to Config(
            buildPlatform = if (isArm64) "arm64-osx" else "x86_64-osx",
            libexecBinPath = if (isArm64) "libexec/arm64-macos" else "libexec/x86_64-macos"
        ),
        LINUX to Config(
            buildPlatform = "x86_64-linux",
            libexecBinPath = "libexec/x86_64-linux"
        )
    )

    data class Config(
        val buildPlatform: String,
        val libexecBinPath: String,
        val executable: String = "dmengine",
        val requiredFiles: List<String> = emptyList()
    )

    fun get(): Config {
        val platform = Platform.current()
        return configs[platform]
            ?: throw IllegalArgumentException("Unsupported platform: $platform")
    }
}

/**
 * Configuration parser for Defold editor installations.
 * Parses the config file from Defold.app/Contents/Resources/config to extract
 * version information and executable paths.
 */
data class DefoldEditorConfig(
    val version: String,
    val editorJar: String,
    val javaBin: String,
    val jarBin: String,
    val launchConfig: LaunchConfigs.Config
) {
    companion object {
        // Config file structure constants
        private const val CONFIG_FILE_NAME = "config"
        private const val MACOS_RESOURCES_PATH = "Contents/Resources"

        // INI section names
        private const val SECTION_BUILD = "build"
        private const val SECTION_BOOTSTRAP = "bootstrap"
        private const val SECTION_LAUNCHER = "launcher"

        // INI property keys
        private const val KEY_VERSION = "version"
        private const val KEY_EDITOR_SHA1 = "editor_sha1"
        private const val KEY_RESOURCESPATH = "resourcespath"
        private const val KEY_JDK = "jdk"
        private const val KEY_JAVA = "java"
        private const val KEY_JAR = "jar"

        // Template variable patterns
        private const val TEMPLATE_BOOTSTRAP_RESOURCESPATH = "\${bootstrap.resourcespath}"
        private const val TEMPLATE_LAUNCHER_JDK = "\${launcher.jdk}"
        private const val TEMPLATE_BUILD_EDITOR_SHA1 = "\${build.editor_sha1}"

        /**
         * Creates a DefoldEditorConfig from the Defold editor installation path.
         */
        internal fun loadEditorConfig(): DefoldEditorConfig? {
            val defoldPath = DefoldDefaults.getDefoldInstallPath()
            if (StringUtil.isEmptyOrSpaces(defoldPath)) return null

            return try {
                val editorDir = Path(defoldPath)
                val configFile = resolveConfigFile(editorDir) ?: return null

                parseConfigFile(configFile)
            } catch (e: Exception) {
                println("Failed to parse Defold config: ${e.message}")
                null
            }
        }

        /**
         * Resolves the config file path for different platform layouts.
         * - On macOS: `Defold.app/Contents/Resources/config`
         * - On other platforms: `<editorPath>/config`
         */
        private fun resolveConfigFile(editorDir: Path): Path? {
            // Check for macOS app bundle structure first
            val macOSResourcesDir = editorDir / MACOS_RESOURCES_PATH
            val macOSConfigFile = macOSResourcesDir / CONFIG_FILE_NAME

            return when {
                macOSResourcesDir.exists() && macOSConfigFile.exists() -> macOSConfigFile
                else -> {
                    val directConfigFile = editorDir / CONFIG_FILE_NAME
                    if (directConfigFile.exists()) directConfigFile else null
                }
            }
        }

        /**
         * Parses the INI config file and constructs the DefoldEditorConfig.
         */
        private fun parseConfigFile(configFile: Path): DefoldEditorConfig? {
            val ini = Ini(configFile.toFile())
            val propertyResolver = PropertyResolver(ini)
            val resourcesDir = configFile.parent

            // Extract basic properties
            val version = propertyResolver.get(SECTION_BUILD, KEY_VERSION)
            if (version.isEmpty()) return null

            val bootstrapResources = propertyResolver.get(SECTION_BOOTSTRAP, KEY_RESOURCESPATH)
            val editorSha = propertyResolver.get(SECTION_BUILD, KEY_EDITOR_SHA1)

            // Resolve template variables and build paths
            val variableContext = mapOf(
                TEMPLATE_BOOTSTRAP_RESOURCESPATH to bootstrapResources,
                TEMPLATE_BUILD_EDITOR_SHA1 to editorSha
            )

            val resolvedPaths = resolveExecutablePaths(propertyResolver, variableContext, resourcesDir)
                ?: return null

            return DefoldEditorConfig(
                version = version,
                editorJar = resolvedPaths.editorJar,
                javaBin = resolvedPaths.javaBin,
                jarBin = resolvedPaths.jarBin,
                launchConfig = LaunchConfigs.get()
            )
        }

        /**
         * Resolves executable paths with template variable substitution.
         */
        private fun resolveExecutablePaths(
            resolver: PropertyResolver,
            variables: Map<String, String>,
            resourcesDir: Path
        ): ResolvedPaths? {
            // Get launcher properties with template substitution
            val launcherJdk = resolver.get(SECTION_LAUNCHER, KEY_JDK)
                .substituteVariables(variables)

            val javaBinTemplate = resolver.get(SECTION_LAUNCHER, KEY_JAVA)
                .substituteVariables(variables + (TEMPLATE_LAUNCHER_JDK to launcherJdk))

            val editorJarTemplate = resolver.get(SECTION_LAUNCHER, KEY_JAR)
                .substituteVariables(variables)

            if (javaBinTemplate.isEmpty() || editorJarTemplate.isEmpty()) return null

            // Combine paths and convert to system-dependent format
            val javaBinPath = FileUtil.toSystemDependentName("${resourcesDir}/${javaBinTemplate.removePrefix("/")}")
            val editorJarPath = FileUtil.toSystemDependentName("${resourcesDir}/${editorJarTemplate.removePrefix("/")}")

            // For jarBin, get parent directory of javaBin and add jar executable
            val javaBinFile = File(javaBinPath)
            val jarBinPath = FileUtil.toSystemDependentName("${javaBinFile.parent}/$KEY_JAR")

            return ResolvedPaths(
                editorJar = editorJarPath,
                javaBin = javaBinPath,
                jarBin = jarBinPath
            )
        }

        /**
         * Substitutes template variables in a string.
         */
        private fun String.substituteVariables(variables: Map<String, String>): String =
            variables.entries.fold(this) { acc, (template, value) ->
                acc.replace(template, value)
            }
    }

    /**
     * Helper class for reading INI properties with null safety.
     */
    private class PropertyResolver(private val ini: Ini) {
        fun get(section: String, key: String): String =
            ini.get(section, key)?.trim() ?: ""
    }

    /**
     * Data class for holding resolved executable paths.
     */
    private data class ResolvedPaths(
        val editorJar: String,
        val javaBin: String,
        val jarBin: String
    )
}
