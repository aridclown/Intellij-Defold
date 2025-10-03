package com.aridclown.intellij.defold.debugger

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import java.nio.file.Path

/**
 * MobDebug-specific implementation of PathResolver that handles path mapping
 * between local IDE paths and remote debugging paths.
 */
class MobDebugPathResolver(
    private val project: Project,
    private val pathMapper: MobDebugPathMapper
) : PathResolver {

    override fun computeRemoteCandidates(absoluteLocalPath: String): List<String> {
        val mapped = pathMapper.toRemote(absoluteLocalPath)
            ?.let(::toSystemIndependentName)
        val rel = computeRelativeToProject(absoluteLocalPath)
            ?.let(::toSystemIndependentName)

        val primary = mapped ?: rel ?: return listOf()
        val candidates = buildSet {
            add(primary)
            add("@$primary")
        }

        return candidates.toList()
    }

    override fun resolveLocalPath(remotePath: String): String? {
        // Try explicit mapping first
        val deChunked = if (remotePath.startsWith("@")) remotePath.substring(1) else remotePath
        val mapped = pathMapper.toLocal(deChunked)
        if (mapped != null) return mapped

        // If the remote path looks relative, try relative to the project base dir
        val base = project.basePath
        val si = toSystemIndependentName(deChunked)
        if (!si.startsWith("/") && base != null) {
            val local = Path.of(base).normalize().resolve(si).normalize()
            return toSystemIndependentName(local.toString())
        }

        return null
    }

    private fun computeRelativeToProject(absoluteLocalPath: String): String? {
        val base = project.basePath ?: return null
        val basePath = Path.of(base).normalize()
        val absPath = Path.of(absoluteLocalPath).normalize()

        return when {
            absPath.startsWith(basePath) -> {
                val rel = basePath.relativize(absPath)
                toSystemIndependentName(rel.toString()).trimStart('/')
            }

            else -> null
        }
    }
}
