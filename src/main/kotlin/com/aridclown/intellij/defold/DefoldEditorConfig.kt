package com.aridclown.intellij.defold

import com.aridclown.intellij.defold.DefoldConstants.CONFIG_FILE_NAME
import com.aridclown.intellij.defold.DefoldConstants.INI_EDITOR_SHA1_KEY
import com.aridclown.intellij.defold.DefoldConstants.INI_JAR_KEY
import com.aridclown.intellij.defold.DefoldConstants.INI_JAVA_KEY
import com.aridclown.intellij.defold.DefoldConstants.INI_JDK_KEY
import com.aridclown.intellij.defold.DefoldConstants.INI_RESOURCESPATH_KEY
import com.aridclown.intellij.defold.DefoldConstants.INI_VERSION_KEY
import com.aridclown.intellij.defold.DefoldConstants.MACOS_RESOURCES_PATH
import com.aridclown.intellij.defold.DefoldConstants.INI_BOOTSTRAP_SECTION
import com.aridclown.intellij.defold.DefoldConstants.INI_BUILD_SECTION
import com.aridclown.intellij.defold.DefoldConstants.INI_LAUNCHER_SECTION
import com.aridclown.intellij.defold.Platform.*
import com.intellij.openapi.util.text.StringUtil
import org.ini4j.Ini
import java.nio.file.Files
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
            val ini = Files.newInputStream(configFile).use(::Ini)
            val propertyResolver = PropertyResolver(ini)
            val resourcesDir = configFile.parent

            // Extract basic properties
            val version = propertyResolver.get(INI_BUILD_SECTION, INI_VERSION_KEY)
            if (version.isEmpty()) return null

            val bootstrapResources = propertyResolver.get(INI_BOOTSTRAP_SECTION, INI_RESOURCESPATH_KEY)
            val editorSha = propertyResolver.get(INI_BUILD_SECTION, INI_EDITOR_SHA1_KEY)

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
            val launcherJdk = resolver.get(INI_LAUNCHER_SECTION, INI_JDK_KEY)
                .substituteVariables(variables)

            val javaBinTemplate = resolver.get(INI_LAUNCHER_SECTION, INI_JAVA_KEY)
                .substituteVariables(variables + (TEMPLATE_LAUNCHER_JDK to launcherJdk))

            val editorJarTemplate = resolver.get(INI_LAUNCHER_SECTION, INI_JAR_KEY)
                .substituteVariables(variables)

            if (javaBinTemplate.isEmpty() || editorJarTemplate.isEmpty()) return null

            // Combine paths and normalize them for the current system
            val javaBinPath = resourcesDir.resolve(javaBinTemplate.removePrefix("/")).normalize()
            val editorJarPath = resourcesDir.resolve(editorJarTemplate.removePrefix("/")).normalize()

            // For jarBin, get parent directory of javaBin and add jar executable
            val jarBinPath = javaBinPath.parent?.resolve(INI_JAR_KEY)?.normalize()
                ?: return null

            return ResolvedPaths(
                editorJar = editorJarPath.toString(),
                javaBin = javaBinPath.toString(),
                jarBin = jarBinPath.toString()
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
