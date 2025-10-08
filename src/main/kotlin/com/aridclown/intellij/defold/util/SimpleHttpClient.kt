package com.aridclown.intellij.defold.util

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

object SimpleHttpClient {

    private val globalTimeout = Duration.ofSeconds(MAX_TIMEOUT_SECONDS)
    private val client = OkHttpClient()

    class SimpleHttpResponse(
        val code: Int,
        val body: String? = null,
    )

    fun get(url: String, timeout: Duration = globalTimeout): SimpleHttpResponse {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        execute(request, timeout).use { response ->
            return SimpleHttpResponse(response.code, response.body.string())
        }
    }

    fun postBytes(
        url: String,
        body: ByteArray,
        contentType: String,
        timeout: Duration = globalTimeout
    ): SimpleHttpResponse {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(contentType.toMediaType()))
            .build()

        execute(request, timeout).use { response ->
            return SimpleHttpResponse(response.code, response.body.string())
        }
    }

    fun downloadToPath(url: String, target: Path, timeout: Duration = globalTimeout) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        execute(request, timeout).use { response ->
            response.ensureSuccess(url)
                .body
                .byteStream()
                .use { input ->
                    Files.newOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
        }
    }

    private fun execute(
        request: Request,
        timeout: Duration
    ) = client.newBuilder()
        .callTimeout(timeout)
        .connectTimeout(timeout)
        .readTimeout(timeout)
        .writeTimeout(timeout)
        .build()
        .newCall(request)
        .execute()

    private fun Response.ensureSuccess(url: String) = apply {
        if (code !in 200..299) {
            throw IOException("HTTP $code returned for $url")
        }
    }
}

private const val MAX_TIMEOUT_SECONDS = 5L