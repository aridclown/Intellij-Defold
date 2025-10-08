package com.aridclown.intellij.defold.debugger

import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Connection lifecycle events handling with listener management.
 */
abstract class ConnectionLifecycleHandler(
    protected val logger: Logger
) {
    private val connectedListeners = CopyOnWriteArrayList<() -> Unit>()
    private val disconnectedListeners = CopyOnWriteArrayList<() -> Unit>()
    private val messageListeners = CopyOnWriteArrayList<(String) -> Unit>()

    fun addOnConnectedListener(listener: () -> Unit) {
        connectedListeners.add(listener)
    }

    fun addOnDisconnectedListener(listener: () -> Unit) {
        disconnectedListeners.add(listener)
    }

    fun addListener(listener: (String) -> Unit) {
        messageListeners.add(listener)
    }

    protected fun onConnected() {
        connectedListeners.forEach { listener ->
            runCatching { listener() }.onFailure { throwable ->
                logger.warn("onConnect listener error", throwable)
            }
        }
    }

    protected fun onDisconnected() {
        disconnectedListeners.forEach { listener ->
            runCatching { listener() }.onFailure { throwable ->
                logger.warn("onDisconnect listener error", throwable)
            }
        }
    }

    protected fun notifyMessageListeners(message: String) {
        messageListeners.forEach { listener ->
            runCatching { listener(message) }.onFailure { throwable ->
                logger.warn("message listener error", throwable)
            }
        }
    }
}
