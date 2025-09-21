package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.eval.MobDebugEvaluator
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame

object MobDebugStackBuilder {
    fun buildExecutionStacks(
        project: Project,
        evaluator: MobDebugEvaluator,
        stackDump: StackDump,
        pathResolver: MobDebugPathResolver,
        fallbackFile: String,
        fallbackLine: Int,
        pausedFile: String?
    ): List<XExecutionStack> {
        val coroutineStacks = listOf(stackDump.current) + stackDump.others

        return coroutineStacks.map { coroutine ->
            MobDebugExecutionStack(
                displayName = displayName(coroutine),
                frames = buildFrames(
                    project,
                    evaluator,
                    coroutine,
                    pathResolver,
                    fallbackFile,
                    fallbackLine,
                    fallbackLocalPath = pausedFile
                )
            )
        }
    }

    private fun buildFrames(
        project: Project,
        evaluator: MobDebugEvaluator,
        coroutine: CoroutineStackInfo,
        pathResolver: MobDebugPathResolver,
        fallbackFile: String,
        fallbackLine: Int,
        fallbackLocalPath: String?
    ): List<MobDebugStackFrame> {
        val evaluationBase = if (coroutine.isCurrent) coroutine.frameBase else null

        val frames = coroutine.frames.mapIndexed { index, frame ->
            val remotePath = frame.source ?: fallbackFile
            val localPath = pathResolver.resolveLocalPath(remotePath) ?: fallbackLocalPath
            val rawLine = frame.line ?: fallbackLine
            val line = if (rawLine > 0) rawLine else 1
            val frameIndex = evaluationBase?.plus(index)

            MobDebugStackFrame(
                project = project,
                filePath = localPath,
                line = line,
                variables = frame.variables,
                evaluator = evaluator,
                evaluationFrameIndex = frameIndex
            )
        }

        if (frames.isNotEmpty()) return frames

        return listOf(
            MobDebugStackFrame(
                project = project,
                filePath = fallbackLocalPath,
                line = if (fallbackLine > 0) fallbackLine else 1,
                variables = emptyList(),
                evaluator = evaluator,
                evaluationFrameIndex = evaluationBase
            )
        )
    }

    private fun displayName(coroutine: CoroutineStackInfo): String {
        val base = when (coroutine.id) {
            "main" -> "Main Coroutine"
            else -> "Coroutine ${coroutine.id}"
        }

        val status = coroutine.status
            .takeIf { it.isNotBlank() && it != "running" }
            ?.lowercase()
        val topFrameName = coroutine.frames.firstOrNull()
            ?.name
            ?.takeIf { it.isNotBlank() && it != "main" }

        return buildString {
            append(base)
            if (!topFrameName.isNullOrEmpty()) {
                append(" – ")
                append(topFrameName)
            }
            if (!status.isNullOrEmpty()) {
                append(" (")
                append(status)
                append(')')
            }
        }
    }
}

class MobDebugExecutionStack(
    displayName: String,
    private val frames: List<XStackFrame>
) : XExecutionStack(displayName) {

    override fun getTopFrame(): XStackFrame? = frames.firstOrNull()

    override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
        val slice = if (firstFrameIndex <= 0) frames else frames.drop(firstFrameIndex)
        container.addStackFrames(slice, true)
    }
}
