package com.aridclown.intellij.defold.debugger.value

import com.aridclown.intellij.defold.DefoldConstants.ELLIPSIS_VAR
import com.aridclown.intellij.defold.DefoldConstants.TABLE_PAGE_SIZE
import com.aridclown.intellij.defold.DefoldConstants.VARARG_PREVIEW_LIMIT
import com.aridclown.intellij.defold.debugger.MobMoreNode
import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.value.MobRValue.*
import com.intellij.icons.AllIcons
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import javax.swing.Icon

class MobDebugVarargValueTest : BasePlatformTestCase() {

    private lateinit var evaluator: MobDebugEvaluator

    override fun setUp() {
        super.setUp()
        evaluator = mockk()
    }

    fun `test presentation exposes preview type and icon`() {
        val varargs = mobVarargs(Num("1"), Str("hello"), Bool(true))

        val capture = capturePresentation(varargs)

        assertThat(capture.icon).isEqualTo(AllIcons.Nodes.Parameter)
        assertThat(capture.type).isEqualTo("vararg")
        assertThat(capture.preview).isEqualTo("1, hello, true")
        assertThat(capture.hasChildren).isTrue
    }

    fun `test presentation truncates preview past configured limit`() {
        val varargs = numberVarargs(VARARG_PREVIEW_LIMIT + 2)

        val capture = capturePresentation(varargs)

        val expectedPreview = (1..VARARG_PREVIEW_LIMIT).joinToString(", ") { it.toString() } + ", …"
        assertThat(capture.preview).isEqualTo(expectedPreview)
        assertThat(capture.hasChildren).isTrue
    }

    fun `test presentation uses em dash placeholder when empty`() {
        val capture = capturePresentation(emptyList())

        assertThat(capture.preview).isEqualTo("—")
        assertThat(capture.icon).isEqualTo(AllIcons.Nodes.Parameter)
        assertThat(capture.type).isEqualTo("vararg")
        assertThat(capture.hasChildren).isTrue
    }

    fun `test children lists every vararg within a single page`() {
        val varargs = mobVarargs(Num("1"), Str("hello"), Bool(false))

        val children = captureChildren(varargs)

        assertThat(children.list.size()).isEqualTo(3)
        assertThat(children.list.getName(0)).isEqualTo("(*vararg 1)")
        assertThat(children.list.getName(1)).isEqualTo("(*vararg 2)")
        assertThat(children.list.getName(2)).isEqualTo("(*vararg 3)")
        assertThat(children.list.getValue(0)).isInstanceOf(MobDebugValue::class.java)
        assertThat(children.isLast).isTrue
    }

    fun `test children append more node when entries exceed page size`() {
        val varargs = numberVarargs(TABLE_PAGE_SIZE + 1)

        val children = captureChildren(varargs)

        assertThat(children.list.size()).isEqualTo(TABLE_PAGE_SIZE + 1)
        assertThat(children.list.getValue(children.list.size() - 1)).isInstanceOf(MobMoreNode::class.java)
        assertThat(children.isLast).isTrue
    }

    fun `test children add empty list when there are no varargs`() {
        val children = captureChildren(emptyList())

        assertThat(children.list.size()).isZero
        assertThat(children.isLast).isTrue
    }

    fun `test name is ellipsis constant`() {
        val value = MobDebugVarargValue(project, mobVarargs(Num("1")), evaluator, 0, null)

        assertThat(value.name).isEqualTo(ELLIPSIS_VAR)
    }

    fun `test source position navigation delegates to local declaration`() {
        val file = myFixture.addFileToProject(
            "test.lua",
            """
            function test(...)
                print(...)
            end
            """.trimIndent()
        )
        myFixture.openFileInEditor(file.virtualFile)

        val sourcePosition = mockk<XSourcePosition>()
        every { sourcePosition.file } returns file.virtualFile
        every { sourcePosition.line } returns 1

        val value = MobDebugVarargValue(project, mobVarargs(Num("1")), evaluator, 0, sourcePosition)
        val navigatable = mockk<XNavigatable>(relaxed = true)

        value.computeSourcePosition(navigatable)

        verify { navigatable.setSourcePosition(match { it.file == file.virtualFile }) }
    }

    fun `test source position navigation ignored when frame position missing`() {
        val value = MobDebugVarargValue(project, mobVarargs(Num("1")), evaluator, 0, null)
        val navigatable = mockk<XNavigatable>(relaxed = true)

        value.computeSourcePosition(navigatable)

        verify(exactly = 0) { navigatable.setSourcePosition(any()) }
    }

    private fun capturePresentation(varargs: List<MobVariable>): PresentationCapture {
        val value = MobDebugVarargValue(project, varargs, evaluator, 0, null)
        val node = mockk<XValueNode>(relaxed = true)
        val place = mockk<XValuePlace>()
        var capturedIcon: Icon? = null
        var capturedPresentation: XRegularValuePresentation? = null
        var capturedHasChildren = false

        every { node.setPresentation(any(), any(), any()) } answers {
            capturedIcon = args[0] as Icon?
            capturedPresentation = args[1] as XRegularValuePresentation
            capturedHasChildren = args[2] as Boolean
        }

        value.computePresentation(node, place)

        verify { node.setPresentation(any(), any(), true) }
        val presentation = capturedPresentation ?: error("presentation not captured")
        return PresentationCapture(
            icon = capturedIcon,
            type = presentation.type.orEmpty(),
            preview = presentation.extractPreview(),
            hasChildren = capturedHasChildren
        )
    }

    private fun captureChildren(varargs: List<MobVariable>): ChildrenCapture {
        val value = MobDebugVarargValue(project, varargs, evaluator, 0, null)
        val node = mockk<XCompositeNode>(relaxed = true)
        var capturedList: XValueChildrenList? = null
        var capturedIsLast = false

        every { node.addChildren(any(), any()) } answers {
            capturedList = args[0] as XValueChildrenList
            capturedIsLast = args[1] as Boolean
        }

        value.computeChildren(node)

        verify { node.addChildren(any(), any()) }
        return ChildrenCapture(capturedList ?: error("children not captured"), capturedIsLast)
    }

    private fun mobVarargs(vararg values: MobRValue): List<MobVariable> =
        values.mapIndexed { index, value -> MobVariable("(*vararg ${index + 1})", value) }

    private fun numberVarargs(count: Int): List<MobVariable> =
        (1..count).map { index -> MobVariable("(*vararg $index)", Num(index.toString())) }

    private data class PresentationCapture(
        val icon: Icon?,
        val type: String,
        val preview: String,
        val hasChildren: Boolean
    )

    private data class ChildrenCapture(
        val list: XValueChildrenList,
        val isLast: Boolean
    )

    private fun XRegularValuePresentation.extractPreview(): String = runCatching {
        val field = javaClass.getDeclaredField("myValue")
        field.isAccessible = true
        field.get(this) as? String
    }.getOrNull().orEmpty()
}
