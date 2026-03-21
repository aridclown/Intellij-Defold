package com.aridclown.intellij.defold

import okhttp3.HttpUrl

internal object EditorHttpClientTestFactory {
    fun create(commands: Set<String> = setOf("build")): EditorHttpClient {
        val url = HttpUrl.Builder()
            .scheme("http")
            .host("127.0.0.1")
            .port(8080)
            .build()

        val constructor = EditorHttpClient::class.java.getDeclaredConstructor(
            HttpUrl::class.java,
            Set::class.java
        ).apply { isAccessible = true }

        return constructor.newInstance(url, commands) as EditorHttpClient
    }
}
