package com.verazial.server.utils

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Request
import okio.Buffer
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue


internal fun basicAuthInterceptor(username: String, password: String) = Interceptor { chain ->
    val request = chain.request()
    val authenticatedRequest = request.newBuilder()
        .header("Authorization", Credentials.basic(username, password))
        .build()
    chain.proceed(authenticatedRequest)
}

@OptIn(ExperimentalTime::class)
@Suppress("unused")
internal fun loggingInterceptor() = Interceptor { chain ->
    val request: Request = chain.request()
    val (response, duration) = measureTimedValue {
        val bodyBuffer = Buffer()
        request.body()?.writeTo(bodyBuffer)
        val bodyByteArray = ByteArray(bodyBuffer.size.toInt())
        bodyBuffer.readFully(bodyByteArray)
        val bodyString = bodyByteArray.decodeToString()
        """
        Sending request ${request.url()}
        ${request.headers().toString().replace('\n', ',').trimIndent()}
        $bodyString
        """.trimIndent()
            .windowed(1000, 1000, true).forEach(::println)
        chain.proceed(request)
    }
    """
    Received response ${response.request().url()}
    took $duration
    ${response.headers().toString().replace('\n', ',').trimIndent()}
    """.trimIndent()
        .windowed(1000, 1000, true).forEach(::println)

    response
}