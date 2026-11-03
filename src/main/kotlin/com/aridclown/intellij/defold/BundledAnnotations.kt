package com.aridclown.intellij.defold

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.zip.ZipFile

/** Reads the annotations shipped with newer Defold editors without changing their contents. */
class BundledAnnotations {
    fun extract(editorJar: Path, destination: Path): Boolean = ZipFile(editorJar.toFile()).use { archive ->
        val prefix = "_unpack/shared/lua-annotations/"
        val entries = archive.entries().asSequence()
            .filter { !it.isDirectory && it.name.startsWith(prefix) }
            .toList()
        if (entries.none { it.name.endsWith(".lua") }) return false

        val root = destination.resolve("defold_api").toAbsolutePath().normalize()
        val outputs = entries.map { entry ->
            val output = root.resolve(entry.name.removePrefix(prefix)).normalize()
            require(output.startsWith(root)) { "Annotation entry points outside the destination: ${entry.name}" }
            entry to output
        }
        outputs.forEach { (entry, output) ->
            Files.createDirectories(output.parent)
            archive.getInputStream(entry).use { Files.copy(it, output, REPLACE_EXISTING) }
        }
        true
    }
}
