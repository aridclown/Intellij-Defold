package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.DefoldConstants.ELLIPSIS_VAR
import com.aridclown.intellij.defold.DefoldConstants.GLOBAL_VAR
import com.aridclown.intellij.defold.DefoldConstants.LOCALS_PAGE_SIZE
import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.aridclown.intellij.defold.debugger.value.MobDebugVarargValue
import com.aridclown.intellij.defold.debugger.value.MobRValue
import com.aridclown.intellij.defold.debugger.value.MobRValue.VarargPreview
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.aridclown.intellij.defold.debugger.value.MobVariable.Kind
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.*
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat

class MobDebugStackFrameIntegrationTest : BasePlatformTestCase() {

    private lateinit var evaluator: MobDebugEvaluator

    override fun setUp() {
        super.setUp()
        evaluator = mockk()
    }

    fun `test source position is null when file path is null`() {
        val frame = MobDebugStackFrame(
            project = project,
            filePath = null,
            line = 42,
            variables = emptyList(),
            evaluator = evaluator,
            evaluationFrameIndex = 0
        )

        assertThat(frame.sourcePosition).isNull()
    }

    fun `test source position is null when file does not exist`() {
        val frame = MobDebugStackFrame(
            project = project,
            filePath = "/nonexistent/file.lua",
            line = 42,
            variables = emptyList(),
            evaluator = evaluator,
            evaluationFrameIndex = 0
        )

        assertThat(frame.sourcePosition).isNull()
    }

    fun `test source position is created for valid file`() {
        val file = myFixture.addFileToProject("test.lua", "print('hello')")
        val fileUrl = file.virtualFile.url

        val frame = MobDebugStackFrame(
            project = project,
            filePath = fileUrl,
            line = 1,
            variables = emptyList(),
            evaluator = evaluator,
            evaluationFrameIndex = 0
        )

        assertThat(frame.sourcePosition)
            .isNotNull
            .extracting({ it?.file }, { it?.line })
            .containsExactly(file.virtualFile, 0) // 0-indexed
    }

    fun `test evaluator is null when evaluation frame index is null`() {
        val frame = MobDebugStackFrame(
            project = project,
            filePath = null,
            line = 1,
            variables = emptyList(),
            evaluator = evaluator,
            evaluationFrameIndex = null
        )

        assertThat(frame.evaluator).isNull()
    }

    fun `test evaluator is created when evaluation frame index is provided`() {
        val frame = MobDebugStackFrame(
            project = project,
            filePath = null,
            line = 1,
            variables = emptyList(),
            evaluator = evaluator,
            evaluationFrameIndex = 2
        )

        assertThat(frame.evaluator)
            .isNotNull
            .isInstanceOf(MobDebugXDebuggerEvaluator::class.java)
    }

    fun `test visible locals returns empty list when no variables`() {
        val frame = MobDebugStackFrame(
            project = project,
            filePath = null,
            line = 1,
            variables = emptyList(),
            evaluator = evaluator,
            evaluationFrameIndex = 0
        )

        assertThat(frame.visibleLocals()).isEmpty()
    }

    fun `test visible locals returns regular variables without varargs`() {
        val variables = listOf(
            MobVariable("x", MobRValue.Num("10"), "x"),
            MobVariable("y", MobRValue.Num("20"), "y")
        )

        val frame = MobDebugStackFrame(
            project = project,
            filePath = null,
            line = 1,
            variables = variables,
            evaluator = evaluator,
            evaluationFrameIndex = 0
        )

        val visibleLocals = frame.visibleLocals()
        assertThat(visibleLocals).hasSize(2)
        assertThat(visibleLocals.map { it.name }).containsExactly("x", "y")
    }

    fun `test visible locals groups varargs into ellipsis variable`() {
        val variables = listOf(
            MobVariable("x", MobRValue.Num("10"), "x"),
            MobVariable("(*vararg 1)", MobRValue.Str("a"), "(*vararg 1)"),
            MobVariable("(*vararg 2)", MobRValue.Str("b"), "(*vararg 2)"),
            MobVariable("y", MobRValue.Num("20"), "y")
        )
        val frame = MobDebugStackFrame(
            project = project,
            filePath = null,
            line = 1,
            variables = variables,
            evaluator = evaluator,
            evaluationFrameIndex = 0
        )

        val visibleLocals = frame.visibleLocals()
        assertThat(visibleLocals)
            .hasSize(3)
            .extracting<String> { it.name }
            .containsExactly("x", "y", ELLIPSIS_VAR)

        val ellipsisVar = visibleLocals.last()
        assertThat(ellipsisVar.value).isInstanceOf(VarargPreview::class.java)
        assertThat(ellipsisVar.kind).isEqualTo(Kind.PARAMETER)
    }

    fun `test compute children adds global var when not in variables list`() {
        val variables = listOf(
            MobVariable("x", MobRValue.Num("10"), "x")
        )
        val frame = MobDebugStackFrame(
            project = project,
            filePath = null,
            line = 1,
            variables = variables,
            evaluator = evaluator,
            evaluationFrameIndex = 0
        )

        assertThat(captureChildren(frame))
            .extracting<String> { it.first }
            .contains(GLOBAL_VAR, "x")
    }

    fun `test compute children does not add global var when already in variables list`() {
        val variables = listOf(
            MobVariable("x", MobRValue.Num("10"), "x"),
            MobVariable(GLOBAL_VAR, MobRValue.GlobalVar(), GLOBAL_VAR)
        )
        val frame = MobDebugStackFrame(
            project = project,
            filePath = null,
            line = 1,
            variables = variables,
            evaluator = evaluator,
            evaluationFrameIndex = 0
        )

        assertThat(captureChildren(frame))
            .filteredOn { it.first == GLOBAL_VAR }
            .hasSize(1)
    }

    fun `test compute children groups varargs under ellipsis entry`() {
        val variables = listOf(
            MobVariable("x", MobRValue.Num("10"), "x"),
            MobVariable("(*vararg 1)", MobRValue.Str("a"), "(*vararg 1)"),
            MobVariable("(*vararg 2)", MobRValue.Str("b"), "(*vararg 2)")
        )
        val frame = MobDebugStackFrame(
            project = project,
            filePath = null,
            line = 1,
            variables = variables,
            evaluator = evaluator,
            evaluationFrameIndex = 0
        )

        val children = captureChildren(frame)
        assertThat(children)
            .extracting<String> { it.first }
            .contains(ELLIPSIS_VAR)
        assertThat(children)
            .filteredOn { it.first == ELLIPSIS_VAR }
            .singleElement()
            .extracting { it.second }
            .isInstanceOf(MobDebugVarargValue::class.java)
    }

    fun `test compute children paginates variables when exceeding page size`() {
        val variables = (1..LOCALS_PAGE_SIZE + 5).map { i ->
            MobVariable("var$i", MobRValue.Num("$i"), "var$i")
        }
        val frame = MobDebugStackFrame(
            project = project,
            filePath = null,
            line = 1,
            variables = variables,
            evaluator = evaluator,
            evaluationFrameIndex = 0
        )

        val children = captureChildren(frame)
        // Should have GLOBAL_VAR + first page of variables + "Show more" node
        assertThat(children)
            .filteredOn { it.first == "Show more" }
            .singleElement()
            .extracting { it.second }
            .isInstanceOf(MobMoreNode::class.java)
    }

    fun `test compute children does not paginate when variables fit in page size`() {
        val variables = (1..<LOCALS_PAGE_SIZE).map { i ->
            MobVariable("var$i", MobRValue.Num("$i"), "var$i")
        }
        val frame = MobDebugStackFrame(
            project = project,
            filePath = null,
            line = 1,
            variables = variables,
            evaluator = evaluator,
            evaluationFrameIndex = 0
        )

        assertThat(captureChildren(frame))
            .filteredOn { it.first == "Show more" }
            .isEmpty()
    }

    fun `test compute children maintains varargs position in middle of variables`() {
        val variables = listOf(
            MobVariable("a", MobRValue.Num("1"), "a"),
            MobVariable("b", MobRValue.Num("2"), "b"),
            MobVariable("(*vararg 1)", MobRValue.Str("x"), "(*vararg 1)"),
            MobVariable("(*vararg 2)", MobRValue.Str("y"), "(*vararg 2)"),
            MobVariable("c", MobRValue.Num("3"), "c"),
            MobVariable("d", MobRValue.Num("4"), "d")
        )
        val frame = MobDebugStackFrame(
            project = project,
            filePath = null,
            line = 1,
            variables = variables,
            evaluator = evaluator,
            evaluationFrameIndex = 0
        )

        val children = captureChildren(frame)
        val names = children.map { it.first }
        // Should be: GLOBAL_VAR, a, b, ..., c, d
        val ellipsisIndex = names.indexOf(ELLIPSIS_VAR)
        val aIndex = names.indexOf("a")
        val bIndex = names.indexOf("b")
        val cIndex = names.indexOf("c")

        assertThat(ellipsisIndex)
            .isGreaterThan(aIndex)
            .isGreaterThan(bIndex)
            .isLessThan(cIndex)
    }

    fun `test more node displays remaining count`() {
        val moreNode = MobMoreNode("(5 more items)") { }
        val node = mockk<XValueNode>(relaxed = true)

        moreNode.computePresentation(node, XValuePlace.TREE)

        verify { node.setPresentation(null, null, "(5 more items)", true) }
    }

    fun `test more node loads children when expanded`() {
        var childrenLoaded = false
        val moreNode = MobMoreNode("(5 more items)") { _ ->
            childrenLoaded = true
        }

        moreNode.computeChildren(mockk<XCompositeNode>(relaxed = true))

        assertThat(childrenLoaded).isTrue
    }

    private fun captureChildren(frame: MobDebugStackFrame): List<Pair<String, XValue>> {
        val capturedChildren = mutableListOf<Pair<String, XValue>>()

        frame.computeChildren(object : XCompositeNode {
            override fun addChildren(children: XValueChildrenList, last: Boolean) {
                for (i in 0 until children.size()) {
                    capturedChildren.add(children.getName(i) to children.getValue(i))
                }
            }

            override fun isObsolete(): Boolean = false
            override fun tooManyChildren(remaining: Int) {}
            override fun setAlreadySorted(alreadySorted: Boolean) {}
            override fun setErrorMessage(errorMessage: String) {}
            override fun setErrorMessage(errorMessage: String, link: XDebuggerTreeNodeHyperlink?) {}
            override fun setMessage(
                message: String,
                icon: javax.swing.Icon?,
                attributes: SimpleTextAttributes,
                link: XDebuggerTreeNodeHyperlink?
            ) {
            }
        })

        return capturedChildren
    }
}
