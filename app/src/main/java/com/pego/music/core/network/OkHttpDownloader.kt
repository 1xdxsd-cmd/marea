package com.pego.music.core.network

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpDownloader private constructor(
    val client: OkHttpClient
) : Downloader() {

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBody = dataToSend?.toRequestBody(null)

        val requestBuilder = okhttp3.Request.Builder()
            .method(httpMethod, requestBody)
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        headers.forEach { (headerName, headerValues) ->
            requestBuilder.removeHeader(headerName)
            headerValues.forEach { value ->
                requestBuilder.addHeader(headerName, value)
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val responseBodyToReturn = response.body?.string().orEmpty()
        val latestUrl = response.request.url.toString()

        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBodyToReturn,
            latestUrl
        )
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        @Volatile
        private var instance: OkHttpDownloader? = null

        fun getInstance(): OkHttpDownloader {
            return instance ?: synchronized(this) {
                val dispatcher = Dispatcher().apply {
                    maxRequests = 48
                    maxRequestsPerHost = 24
                }
                instance ?: OkHttpDownloader(
                    OkHttpClient.Builder()
                        .dispatcher(dispatcher)
                        .connectionPool(ConnectionPool(24, 5, TimeUnit.MINUTES))
                        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                        .readTimeout(25, TimeUnit.SECONDS)
                        .connectTimeout(12, TimeUnit.SECONDS)
                        .followRedirects(true)
                        .build()
                ).also { instance = it }
            }
        }
    }
}
