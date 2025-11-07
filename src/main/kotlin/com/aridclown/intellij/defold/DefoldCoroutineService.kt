package com.aridclown.intellij.defold

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.DEFAULT
import kotlinx.coroutines.CoroutineStart.LAZY

@Service(PROJECT)
class DefoldCoroutineService(private val cs: CoroutineScope) {
    fun start(callable: suspend () -> Unit): Job = cs.launch { callable() }

    fun <T> startAsync(lazy: Boolean = false, callable: suspend () -> T): Deferred<T> =
        cs.async(start = if (lazy) LAZY else DEFAULT) { callable() }

    companion object {
        fun Project.launch(callable: suspend () -> Unit): Job = service<DefoldCoroutineService>().start(callable)
    }
}