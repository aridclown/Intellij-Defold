package com.aridclown.intellij.defold.util

import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Utility class for loading and managing plugin resources.
 */
object ResourceUtil {

    /**
     * Loads a text resource from the plugin's resources and returns its content as a string.
     *
     * @param resourcePath The path to the resource file
     * @param classLoader The class loader to use for loading the resource. If null, uses the current class's loader.
     * @return The content of the resource file as a string
     * @throws IllegalStateException if the resource cannot be found or loaded
     */
    fun loadTextResource(resourcePath: String, classLoader: ClassLoader? = null): String {
        val loader = classLoader ?: ResourceUtil::class.java.classLoader
        return loader.getResourceAsStream(resourcePath)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IllegalStateException("Could not load $resourcePath resource")
    }

    /**
     * Copies multiple resource files to the project directory.
     * Only copies files that don't already exist in the target location.
     *
     * @param project The IntelliJ project
     * @param classLoader The class loader to use for loading resources. If null, uses the current class's loader.
     * @param resourcePaths List of resource paths to copy (e.g., "debugger/mobdebug.lua")
     * @throws IllegalStateException if any resource cannot be found
     */
    fun copyResourcesToProject(
        project: Project,
        classLoader: ClassLoader? = null,
        vararg resourcePaths: String,
    ) {
        val projectRoot = project.basePath?.let(Path::of) ?: return
        val loader = classLoader ?: ResourceUtil::class.java.classLoader

        resourcePaths.forEach { resourcePath ->
            val targetPath = projectRoot.resolve(resourcePath)

            if (Files.notExists(targetPath)) {
                loader.getResourceAsStream(resourcePath)?.use { inputStream ->
                    targetPath.parent?.let(Files::createDirectories)
                    Files.copy(inputStream, targetPath, REPLACE_EXISTING)
                } ?: throw IllegalStateException("$resourcePath resource not found in plugin")
            }
        }
    }

    /**
     * Loads a Lua script resource, replaces placeholders, and returns the processed script.
     *
     * @param resourcePath The path to the Lua script resource
     * @param replacements Map of placeholder strings to their replacement values
     * @param compactWhitespace Whether to compact whitespace in the result
     * @return The processed Lua script content
     */
    fun loadAndProcessLuaScript(
        resourcePath: String,
        compactWhitespace: Boolean = true,
        vararg replacements: Pair<String, String>
    ): String {
        var script = loadTextResource(resourcePath)

        replacements.forEach { (placeholder, replacement) ->
            script = script.replace(placeholder, replacement)
        }

        if (compactWhitespace) {
            script = script.replace(Regex("\\s+"), " ")
        }

        return script
    }
}
