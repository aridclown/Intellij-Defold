package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldProjectService
import com.aridclown.intellij.defold.DefoldProjectService.Companion.isDefoldProject
import com.aridclown.intellij.defold.DefoldScriptType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat

class DefoldScriptBreakpointTypeTest : BasePlatformTestCase() {

    private lateinit var breakpointType: DefoldScriptBreakpointType

    override fun setUp() {
        super.setUp()
        breakpointType = DefoldScriptBreakpointType()

        mockkObject(DefoldProjectService.Companion)
        every { project.isDefoldProject } returns true
    }

    override fun tearDown() {
        super.tearDown()
        clearAllMocks()
        unmockkAll()
    }

    fun `test breakpoint type properties`() {
        assertThat(breakpointType)
            .extracting({ it.id }, { it.title }, { it.priority })
            .containsExactly("defold-script", "Defold Line Breakpoints", 100)
    }

    fun `test can put breakpoint in Defold script files`() {
        val supportedExtensions = DefoldScriptType.entries.map { it.extension }

        supportedExtensions.forEach { ext ->
            val file = mockk<VirtualFile> {
                every { extension } returns ext
            }

            assertThat(breakpointType.canPutAt(file, 1, project))
                .withFailMessage("Expected to put breakpoint in .$ext file")
                .isTrue
        }
    }

    fun `test cannot put breakpoint when not Defold project`() {
        val file = mockk<VirtualFile> {
            every { extension } returns "lua"
        }
        every { project.isDefoldProject } returns false

        assertThat(breakpointType.canPutAt(file, 1, project)).isFalse
    }

    fun `test cannot put breakpoint in unsupported files`() {
        val unsupportedCases = listOf(
            "txt" to "non-script extension",
            null to "null extension"
        )

        unsupportedCases.forEach { (ext, desc) ->
            val file = mockk<VirtualFile> {
                every { extension } returns ext
            }

            assertThat(breakpointType.canPutAt(file, 1, project))
                .withFailMessage("Expected to NOT put breakpoint with $desc")
                .isFalse
        }
    }
}
