package com.aridclown.intellij.defold.debugger

import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Path

/**
 * Maps local file paths to remote Lua paths and vice versa.
 * Mapping is defined as pairs of local -> remote prefixes.
 */
class MobDebugPathMapper(mappings: Map<String, String>) {

    private data class Mapping(val localRoot: Path, val remoteRootSi: String)

    private val normalized: List<Mapping> = mappings.entries
        .filter { it.key.isNotBlank() && it.value.isNotBlank() }
        .map { (local, remote) ->
            val localPath = Path.of(local).normalize()
            val remoteSi = FileUtil.toSystemIndependentName(remote).trimEnd('/')
            Mapping(localPath, remoteSi)
        }

    fun toRemote(local: String): String? {
        val abs = Path.of(local).normalize()
        for (m in normalized) {
            if (abs.startsWith(m.localRoot)) {
                val rel = m.localRoot.relativize(abs)
                val relSi = FileUtil.toSystemIndependentName(rel.toString()).trimStart('/')
                return if (m.remoteRootSi.isEmpty()) relSi else m.remoteRootSi + "/" + relSi
            }
        }
        return null
    }

    fun toLocal(remote: String): String? {
        val remoteSi = FileUtil.toSystemIndependentName(remote)
        for (m in normalized) {
            val rr = m.remoteRootSi
            if (rr.isNotEmpty() && remoteSi.startsWith(rr)) {
                val suffix = remoteSi.removePrefix(rr).trimStart('/')
                val local = m.localRoot.resolve(suffix).normalize()
                return FileUtil.toSystemIndependentName(local.toString())
            }
        }
        return null
    }
}
