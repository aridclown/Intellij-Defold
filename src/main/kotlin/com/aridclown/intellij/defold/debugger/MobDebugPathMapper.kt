package com.aridclown.intellij.defold.debugger

/**
 * Maps local file paths to remote Lua paths and vice versa.
 * Mapping is defined as pairs of local -> remote prefixes.
 */
class MobDebugPathMapper(private val mappings: Map<String, String>) {

    fun toRemote(local: String): String? {
        val normalized = local.replace('\\', '/')
        for ((localPrefix, remotePrefix) in mappings) {
            val normLocal = localPrefix.replace('\\', '/')
            if (normalized.startsWith(normLocal)) {
                return remotePrefix + normalized.removePrefix(normLocal)
            }
        }
        return null
    }

    fun toLocal(remote: String): String? {
        val normalized = remote.replace('\\', '/')
        for ((localPrefix, remotePrefix) in mappings) {
            val normRemote = remotePrefix.replace('\\', '/')
            if (normalized.startsWith(normRemote)) {
                return localPrefix + normalized.removePrefix(normRemote)
            }
        }
        return null
    }
}
