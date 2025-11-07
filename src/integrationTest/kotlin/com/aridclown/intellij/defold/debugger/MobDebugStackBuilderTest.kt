package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat

class MobDebugStackBuilderTest : BasePlatformTestCase() {

    private var evaluator: MobDebugEvaluator = mockk(relaxed = true)
    private var pathResolver: MobDebugPathResolver = mockk(relaxed = true)

    fun `test builds single execution stack with current coroutine only`() {
        val dump = stackDump(
            current = coroutineInfo(frames = listOf(frameInfo()))
        )

        every { pathResolver.resolveLocalPath("/src/main.lua") } returns "/project/src/main.lua"

        val stacks = buildStacks(dump)

        assertThat(stacks)
            .singleElement()
            .satisfies({
                assertThat(it.displayName).isEqualTo("Main Coroutine – init")
                assertThat(it.topFrame).isNotNull
            })
            .extracting { it.topFrame as MobDebugStackFrame }
            .extracting({ it.getFilePath() }, { it.getLine() })
            .containsExactly("/project/src/main.lua", 10)
    }

    fun `test builds multiple execution stacks with other coroutines`() {
        val dump = stackDump(
            current = coroutineInfo(frames = listOf(frameInfo())),
            others = listOf(
                coroutineInfo(
                    id = "co_1",
                    status = "suspended",
                    frames = listOf(frameInfo(source = "/src/worker.lua", line = 5, name = "work")),
                    frameBase = 0,
                    isCurrent = false
                ),
                coroutineInfo(
                    id = "co_2",
                    status = "dead",
                    frames = listOf(frameInfo(source = "/src/task.lua", line = 20, name = "task")),
                    frameBase = 0,
                    isCurrent = false
                )
            )
        )

        every { pathResolver.resolveLocalPath("/src/main.lua") } returns "/project/src/main.lua"
        every { pathResolver.resolveLocalPath("/src/worker.lua") } returns "/project/src/worker.lua"
        every { pathResolver.resolveLocalPath("/src/task.lua") } returns "/project/src/task.lua"

        val stacks = buildStacks(dump)

        assertThat(stacks)
            .extracting<String> { it.displayName }
            .containsExactly(
                "Main Coroutine – init",
                "Coroutine co_1 – work (suspended)",
                "Coroutine co_2 – task (dead)"
            )
    }

    fun `test uses fallback file and line when frame info is missing`() {
        val dump = stackDump(
            current = coroutineInfo(frames = listOf(frameInfo(source = null, line = null, name = "unknown")))
        )

        every { pathResolver.resolveLocalPath(any()) } returns null

        val stacks = buildStacks(dump, fallbackLine = 42, pausedFile = "temp:///paused.lua")

        assertThat(stacks)
            .singleElement()
            .extracting { it.topFrame as MobDebugStackFrame }
            .extracting({ it.getFilePath() }, { it.getLine() })
            .containsExactly("temp:///paused.lua", 42)
    }

    fun `test creates fallback frame when coroutine has no frames`() {
        val dump = stackDump(current = coroutineInfo(frames = emptyList()))

        val stacks = buildStacks(dump, pausedFile = "temp:///paused.lua")

        assertThat(stacks)
            .singleElement()
            .extracting { it.topFrame as MobDebugStackFrame }
            .satisfies({
                assertThat(it).isNotNull
                assertThat(it?.evaluator).isNotNull
            })
            .extracting({ it.getFilePath() }, { it.getLine() })
            .containsExactly("temp:///paused.lua", 1)
    }

    fun `test normalizes line number to 1 if non-positive`() {
        val file = myFixture.addFileToProject("main.lua", "-- test file").virtualFile

        val dump = stackDump(
            current = coroutineInfo(
                frames = listOf(
                    frameInfo(line = 0),
                    frameInfo(line = -5, name = "other")
                )
            )
        )

        every { pathResolver.resolveLocalPath(any()) } returns file.url

        val stacks = buildStacks(dump)

        assertThat(stacks)
            .singleElement()
            .extracting { it.topFrame as MobDebugStackFrame }
            .satisfies({
                assertThat(it).isNotNull
                assertThat(it?.sourcePosition?.line).isEqualTo(0)
            })
            .extracting({ it.getFilePath() }, { it.getLine() })
            .containsExactly("temp:///src/main.lua", 1)
    }

    fun `test sets evaluation frame index only for current coroutine`() {
        val dump = stackDump(
            current = coroutineInfo(
                frames = listOf(
                    frameInfo(),
                    frameInfo(line = 5, name = "start")
                )
            ),
            others = listOf(
                coroutineInfo(
                    id = "co_1",
                    status = "suspended",
                    frames = listOf(frameInfo(source = "/src/worker.lua", line = 5, name = "work")),
                    frameBase = 5,
                    isCurrent = false
                )
            )
        )

        every { pathResolver.resolveLocalPath(any()) } returns "temp:///src/main.lua"

        val stacks = buildStacks(dump)

        assertThat(stacks).element(0)
            .extracting { it.topFrame?.evaluator }
            .isNotNull

        assertThat(stacks).element(1)
            .extracting { it.topFrame?.evaluator }
            .isNull()
    }

    fun `test display name omits main frame name`() {
        val dump = stackDump(
            current = coroutineInfo(frames = listOf(frameInfo(name = "main")))
        )

        val stacks = buildStacks(dump)

        assertThat(stacks)
            .singleElement()
            .extracting { it.displayName }
            .isEqualTo("Main Coroutine")
    }

    fun `test display name omits blank frame name`() {
        val dump = stackDump(
            current = coroutineInfo(frames = emptyList()),
            others = listOf(
                coroutineInfo(
                    id = "worker",
                    status = "suspended",
                    frames = listOf(frameInfo(source = "/src/worker.lua", name = "")),
                    frameBase = 0,
                    isCurrent = false
                )
            )
        )

        val stacks = buildStacks(dump)

        assertThat(stacks)
            .element(1)
            .extracting { it.displayName }
            .isEqualTo("Coroutine worker (suspended)")
    }

    fun `test execution stack computes frames from index`() {
        val frames = createTestFrames()
        val stack = MobDebugExecutionStack("Test", frames)

        val container = TestFrameContainer()
        stack.computeStackFrames(0, container)

        assertThat(container.frames).hasSize(3)
        assertThat(container.allFramesComputed).isTrue
    }

    fun `test execution stack computes frames from non-zero index`() {
        val frames = createTestFrames()
        val stack = MobDebugExecutionStack("Test", frames)

        val container = TestFrameContainer()
        stack.computeStackFrames(1, container)

        assertThat(container.frames).hasSize(2)
        assertThat(container.allFramesComputed).isTrue
    }

    fun `test execution stack returns top frame`() {
        val topFrame = MobDebugStackFrame(project, "/file1.lua", 1, emptyList(), evaluator, null)
        val frames = listOf(
            topFrame,
            MobDebugStackFrame(project, "/file2.lua", 2, emptyList(), evaluator, null)
        )

        val stack = MobDebugExecutionStack("Test", frames)

        assertThat(stack.topFrame).isSameAs(topFrame)
    }

    fun `test execution stack returns no top frame when empty`() {
        val stack = MobDebugExecutionStack("Test", emptyList())

        assertThat(stack.topFrame).isNull()
    }

    private fun buildStacks(
        stackDump: StackDump,
        fallbackFile: String = DEFAULT_FALLBACK_FILE,
        fallbackLine: Int = DEFAULT_FALLBACK_LINE,
        pausedFile: String? = null
    ) = MobDebugStackBuilder.buildExecutionStacks(
        project, evaluator, stackDump, pathResolver, fallbackFile, fallbackLine, pausedFile
    )

    private fun coroutineInfo(
        id: String = "main",
        status: String = "running",
        frameBase: Int = 3,
        isCurrent: Boolean = true,
        frames: List<FrameInfo> = emptyList(),
    ) = CoroutineStackInfo(id, status, frames, frameBase, isCurrent)

    private fun frameInfo(
        source: String? = "/src/main.lua",
        line: Int? = 10,
        name: String = "init"
    ) = FrameInfo(source, line, name)

    private fun stackDump(
        current: CoroutineStackInfo,
        others: List<CoroutineStackInfo> = emptyList()
    ) = StackDump(current, others)

    private fun createTestFrames(count: Int = 3): List<MobDebugStackFrame> = (1..count).map { i ->
        MobDebugStackFrame(project, "/file$i.lua", i, emptyList(), evaluator, null)
    }

    companion object {
        private const val DEFAULT_FALLBACK_FILE = "/fallback.lua"
        private const val DEFAULT_FALLBACK_LINE = 1
    }

    private class TestFrameContainer : XExecutionStack.XStackFrameContainer {
        val frames = mutableListOf<XStackFrame>()
        var allFramesComputed = false

        override fun addStackFrames(stackFrames: List<XStackFrame>, last: Boolean) {
            frames.addAll(stackFrames)
            allFramesComputed = last
        }

        override fun errorOccurred(errorMessage: String) {}
    }
}