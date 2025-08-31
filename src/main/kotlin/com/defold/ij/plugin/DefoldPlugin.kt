package com.defold.ij.plugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger

@Service
class DefoldPluginService {
    private val log = Logger.getInstance(DefoldPluginService::class.java)
    init {
        log.info("Defold plugin service initialized")
    }
}

