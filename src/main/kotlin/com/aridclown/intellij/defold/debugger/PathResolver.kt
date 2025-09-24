package com.aridclown.intellij.defold.debugger

/**
 * Strategy interface for resolving local paths to remote debugging paths.
 * Allows different path mapping strategies for different debugging scenarios.
 */
interface PathResolver {

    /**
     * Converts an absolute local path to all possible remote path candidates
     * that the debugger might use to identify the file.
     */
    fun computeRemoteCandidates(absoluteLocalPath: String): List<String>

    /**
     * Converts a remote debugging path back to a local file path.
     */
    fun resolveLocalPath(remotePath: String): String?
}
