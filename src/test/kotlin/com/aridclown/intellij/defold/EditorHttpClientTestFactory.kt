package com.aridclown.intellij.defold

import okhttp3.HttpUrl

internal object EditorHttpClientTestFactory {
    fun create(): EditorHttpClient {
        val url = HttpUrl.Builder()
            .scheme("http")
            .host("127.0.0.1")
            .port(8080)
            .build()

        return EditorHttpClient::class.java
            .declaredConstructors
            .first()
            .apply { isAccessible = true }
            .newInstance(url, setOf("build")) as EditorHttpClient
    }
}
