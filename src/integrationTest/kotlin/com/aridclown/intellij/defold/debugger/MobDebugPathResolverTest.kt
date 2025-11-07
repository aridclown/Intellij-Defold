package com.aridclown.intellij.defold.debugger

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat

class MobDebugPathResolverTest : BasePlatformTestCase() {

    private lateinit var pathMapper: MobDebugPathMapper
    private lateinit var resolver: MobDebugPathResolver

    override fun setUp() {
        super.setUp()
        pathMapper = mockk()
        resolver = MobDebugPathResolver(project, pathMapper)
    }

    fun `test remote candidates includes mapped path`() {
        val localPath = "/project/src/main.lua"
        val remotePath = "src/main.lua"

        every { pathMapper.toRemote(localPath) } returns remotePath

        val candidates = runReadAction {
            resolver.computeRemoteCandidates(localPath)
        }

        assertThat(candidates).containsExactlyInAnyOrder(
            remotePath,
            "@$remotePath"
        )
    }

    fun `test remote candidates includes relative path when no mapping`() {
        val localPath = "${project.basePath}/scripts/test.lua"

        every { pathMapper.toRemote(localPath) } returns null

        val candidates = runReadAction {
            resolver.computeRemoteCandidates(localPath)
        }

        assertThat(candidates).allMatch { it.contains("scripts/test.lua") || it.contains("@scripts/test.lua") }
    }

    fun `test remote candidates returns empty list when no mapping and no relative path`() {
        val localPath = "/some/other/path/file.lua"

        every { pathMapper.toRemote(localPath) } returns null

        val candidates = runReadAction {
            resolver.computeRemoteCandidates(localPath)
        }

        assertThat(candidates).isEmpty()
    }

    fun `test resolve local path uses mapper first`() {
        val remotePath = "src/main.lua"
        val localPath = "/project/src/main.lua"

        every { pathMapper.toLocal(remotePath) } returns localPath

        val result = runReadAction {
            resolver.resolveLocalPath(remotePath)
        }

        assertThat(result).isEqualTo(localPath)
    }

    fun `test resolve local path strips @ prefix before mapping`() {
        val remotePath = "@src/main.lua"
        val localPath = "/project/src/main.lua"

        every { pathMapper.toLocal("src/main.lua") } returns localPath

        val result = runReadAction {
            resolver.resolveLocalPath(remotePath)
        }

        assertThat(result).isEqualTo(localPath)
    }

    fun `test resolve local path resolves relative path against project base`() {
        val remotePath = "scripts/test.lua"

        every { pathMapper.toLocal(remotePath) } returns null

        val result = runReadAction {
            resolver.resolveLocalPath(remotePath)
        }

        assertThat(result).isNotNull()
        assertThat(result).contains("scripts/test.lua")
        assertThat(result).startsWith(project.basePath!!)
    }

    fun `test resolve local path returns null for absolute remote path with no mapping`() {
        val remotePath = "/absolute/path/file.lua"

        every { pathMapper.toLocal(remotePath) } returns null

        val result = runReadAction {
            resolver.resolveLocalPath(remotePath)
        }

        assertThat(result).isNull()
    }

    fun `test compute relative to project returns correct relative path`() {
        val absolutePath = "${project.basePath}/src/scripts/main.lua"

        every { pathMapper.toRemote(absolutePath) } returns null

        val candidates = runReadAction {
            resolver.computeRemoteCandidates(absolutePath)
        }

        assertThat(candidates).anyMatch {
            it == "src/scripts/main.lua" || it == "@src/scripts/main.lua"
        }
    }

    fun `test path normalization handles system-independent separators`() {
        val windowsStylePath = "${project.basePath}\\scripts\\test.lua"

        every { pathMapper.toRemote(any()) } returns null

        val candidates = runReadAction {
            resolver.computeRemoteCandidates(windowsStylePath)
        }

        assertThat(candidates).allMatch { it.contains("/") && !it.contains("\\") }
    }
}
