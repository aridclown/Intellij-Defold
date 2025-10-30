package com.aridclown.intellij.defold.debugger

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

class MobDebugBreakpointHandlerTest {

    private lateinit var protocol: MobDebugProtocol
    private lateinit var pathResolver: PathResolver
    private lateinit var breakpointLocations: ConcurrentHashMap.KeySetView<BreakpointLocation, Boolean>
    private lateinit var handler: MobDebugBreakpointHandler

    @BeforeEach
    fun setup() {
        protocol = mockk(relaxed = true)
        pathResolver = mockk()
        breakpointLocations = ConcurrentHashMap.newKeySet()
        handler = MobDebugBreakpointHandler(protocol, pathResolver, breakpointLocations)
    }

    @Test
    fun `registers breakpoint with protocol and adds to location set`() {
        val breakpoint = createBreakpoint("/project/src/main.lua", 5)
        every { pathResolver.computeRemoteCandidates("/project/src/main.lua") } returns listOf("/main.lua")

        handler.registerBreakpoint(breakpoint)

        verify { protocol.setBreakpoint("/main.lua", 6) }
        assertThat(breakpointLocations).contains(BreakpointLocation("/main.lua", 6))
    }

    @Test
    fun `registers breakpoint for multiple remote candidates`() {
        val breakpoint = createBreakpoint("/project/src/main.lua", 10)
        every { pathResolver.computeRemoteCandidates("/project/src/main.lua") } returns listOf(
            "/main.lua",
            "@/main.lua"
        )

        handler.registerBreakpoint(breakpoint)

        verify { protocol.setBreakpoint("/main.lua", 11) }
        verify { protocol.setBreakpoint("@/main.lua", 11) }
        assertThat(breakpointLocations).containsExactlyInAnyOrder(
            BreakpointLocation("/main.lua", 11),
            BreakpointLocation("@/main.lua", 11)
        )
    }

    @Test
    fun `converts zero-based line to one-based for protocol`() {
        val breakpoint = createBreakpoint("/project/test.lua", 0)
        every { pathResolver.computeRemoteCandidates("/project/test.lua") } returns listOf("/test.lua")

        handler.registerBreakpoint(breakpoint)

        verify { protocol.setBreakpoint("/test.lua", 1) }
    }

    @Test
    fun `ignores breakpoint with no source position`() {
        val breakpoint = mockk<XLineBreakpoint<XBreakpointProperties<*>>>()
        every { breakpoint.sourcePosition } returns null

        handler.registerBreakpoint(breakpoint)

        verify(exactly = 0) { protocol.setBreakpoint(any(), any()) }
        verify(exactly = 0) { pathResolver.computeRemoteCandidates(any()) }
        assertThat(breakpointLocations).isEmpty()
    }

    @Test
    fun `handles empty remote candidates gracefully`() {
        val breakpoint = createBreakpoint("/project/unknown.lua", 5)
        every { pathResolver.computeRemoteCandidates("/project/unknown.lua") } returns emptyList()

        handler.registerBreakpoint(breakpoint)

        verify(exactly = 0) { protocol.setBreakpoint(any(), any()) }
        assertThat(breakpointLocations).isEmpty()
    }

    @Test
    fun `unregisters breakpoint from protocol and removes from location set`() {
        val breakpoint = createBreakpoint("/project/src/main.lua", 5)
        every { pathResolver.computeRemoteCandidates("/project/src/main.lua") } returns listOf("/main.lua")

        breakpointLocations.add("/main.lua", 6)

        handler.unregisterBreakpoint(breakpoint, temporary = false)

        verify { protocol.deleteBreakpoint("/main.lua", 6) }
        assertThat(breakpointLocations).isEmpty()
    }

    @Test
    fun `unregisters breakpoint for multiple remote candidates`() {
        val breakpoint = createBreakpoint("/project/src/main.lua", 10)
        every { pathResolver.computeRemoteCandidates("/project/src/main.lua") } returns listOf(
            "/main.lua",
            "@/main.lua"
        )

        breakpointLocations.add("/main.lua", 11)
        breakpointLocations.add("@/main.lua", 11)

        handler.unregisterBreakpoint(breakpoint, temporary = false)

        verify { protocol.deleteBreakpoint("/main.lua", 11) }
        verify { protocol.deleteBreakpoint("@/main.lua", 11) }
        assertThat(breakpointLocations).isEmpty()
    }

    @Test
    fun `ignores unregister for breakpoint with no source position`() {
        val breakpoint = mockk<XLineBreakpoint<XBreakpointProperties<*>>>()
        every { breakpoint.sourcePosition } returns null

        handler.unregisterBreakpoint(breakpoint, temporary = false)

        verify(exactly = 0) { protocol.deleteBreakpoint(any(), any()) }
        verify(exactly = 0) { pathResolver.computeRemoteCandidates(any()) }
    }

    @Test
    fun `handles concurrent breakpoint registration`() {
        val lines = 1..5
        val breakpoints = lines.map { createBreakpoint("/project/main.lua", it) }
        every { pathResolver.computeRemoteCandidates("/project/main.lua") } returns listOf("/main.lua")

        breakpoints.forEach { handler.registerBreakpoint(it) }

        verify(exactly = 5) { protocol.setBreakpoint(eq("/main.lua"), match { it in 2..6 }) }
        assertThat(breakpointLocations).hasSize(5)
    }

    @Test
    fun `multiple breakpoints in same file at different lines`() {
        val bp1 = createBreakpoint("/project/main.lua", 10)
        val bp2 = createBreakpoint("/project/main.lua", 20)
        val bp3 = createBreakpoint("/project/main.lua", 30)
        every { pathResolver.computeRemoteCandidates("/project/main.lua") } returns listOf("/main.lua")

        handler.registerBreakpoint(bp1)
        handler.registerBreakpoint(bp2)
        handler.registerBreakpoint(bp3)

        assertThat(breakpointLocations).containsExactlyInAnyOrder(
            BreakpointLocation("/main.lua", 11),
            BreakpointLocation("/main.lua", 21),
            BreakpointLocation("/main.lua", 31)
        )
    }

    @Test
    fun `breakpoints in different files`() {
        val bp1 = createBreakpoint("/project/main.lua", 5)
        val bp2 = createBreakpoint("/project/util.lua", 10)
        every { pathResolver.computeRemoteCandidates("/project/main.lua") } returns listOf("/main.lua")
        every { pathResolver.computeRemoteCandidates("/project/util.lua") } returns listOf("/util.lua")

        handler.registerBreakpoint(bp1)
        handler.registerBreakpoint(bp2)

        assertThat(breakpointLocations).containsExactlyInAnyOrder(
            BreakpointLocation("/main.lua", 6),
            BreakpointLocation("/util.lua", 11)
        )
    }

    @Test
    fun `handles re-registering same breakpoint`() {
        val bp1 = createBreakpoint("/project/main.lua", 5)
        val bp2 = createBreakpoint("/project/main.lua", 5)
        every { pathResolver.computeRemoteCandidates("/project/main.lua") } returns listOf("/main.lua")

        handler.registerBreakpoint(bp1)
        handler.registerBreakpoint(bp2)

        verify(exactly = 2) { protocol.setBreakpoint("/main.lua", 6) }
        assertThat(breakpointLocations).hasSize(1)
    }

    private fun createBreakpoint(localPath: String, lineNumber: Int): XLineBreakpoint<XBreakpointProperties<*>> {
        val file = mockk<VirtualFile>()
        every { file.path } returns localPath

        val position = mockk<XSourcePosition>()
        every { position.file } returns file
        every { position.line } returns lineNumber

        val breakpoint = mockk<XLineBreakpoint<XBreakpointProperties<*>>>()
        every { breakpoint.sourcePosition } returns position

        return breakpoint
    }
}
