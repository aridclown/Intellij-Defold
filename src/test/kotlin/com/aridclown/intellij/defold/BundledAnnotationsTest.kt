package com.aridclown.intellij.defold

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BundledAnnotationsTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun `extracts runtime and editor annotations without unrelated editor files`() {
        val jar = archive(
            "_unpack/shared/lua-annotations/go.lua" to "---@meta\n go = {}",
            "_unpack/shared/lua-annotations/editor.editor_script" to "---@meta\n editor = {}",
            "unrelated.txt" to "ignored"
        )
        val destination = temp.resolve("cache")

        assertThat(BundledAnnotations().extract(jar, destination)).isTrue()
        assertThat(Files.readString(destination.resolve("defold_api/go.lua"))).isEqualTo("---@meta\n go = {}")
        assertThat(Files.readString(destination.resolve("defold_api/editor.editor_script"))).isEqualTo("---@meta\n editor = {}")
        assertThat(destination.resolve("unrelated.txt")).doesNotExist()
    }

    @Test
    fun `older editors without bundled annotations allow download fallback`() {
        assertThat(BundledAnnotations().extract(archive("other.txt" to "text"), temp.resolve("cache"))).isFalse()
        assertThat(temp.resolve("cache")).doesNotExist()
    }

    @Test
    fun `rejects annotation paths outside the cache`() {
        val jar = archive("_unpack/shared/lua-annotations/../../escape.lua" to "bad")
        assertThrows<IllegalArgumentException> { BundledAnnotations().extract(jar, temp.resolve("cache")) }
        assertThat(temp.resolve("escape.lua")).doesNotExist()
    }

    @Test
    fun `missing editor archive reports an IO failure`() {
        assertThrows<IOException> {
            BundledAnnotations().extract(temp.resolve("missing.jar"), temp.resolve("cache"))
        }
    }

    @Test
    fun `invalid editor archive reports an IO failure`() {
        val jar = temp.resolve("invalid.jar")
        Files.writeString(jar, "not a zip")
        assertThrows<IOException> { BundledAnnotations().extract(jar, temp.resolve("cache")) }
    }

    private fun archive(vararg files: Pair<String, String>): Path = temp.resolve("editor.jar").also { path ->
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
