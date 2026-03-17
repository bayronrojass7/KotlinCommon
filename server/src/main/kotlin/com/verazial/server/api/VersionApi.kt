package com.verazial.server.api

import com.verazial.server.model.version.ValidateClientRequest
import com.verazial.server.model.version.ValidateClientResponse
import com.verazial.server.utils.basicAuthInterceptor
import com.verazial.server.utils.ignoreSSLErrors
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.*
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * VerazialID Version REST API
 */
interface VersionApi {

    /**
     * Validate the version a VerazialID App.
     */
    @POST("ValidateClient")
    suspend fun validateClient(
        @Body request: ValidateClientRequest
    ): ValidateClientResponse

    /**
     * Download latest version of a VerazialID App.
     */
    @GET("DownloadClient/{idProduct}")
    @Streaming
    suspend fun downloadClient(
        @Path("idProduct") idProduct: String
    ): ResponseBody


    companion object {
        /**
         * Create a new instance of the VerazialID Version REST API
         *
         * @param baseUrl The base URL of the VerazialID Version Server
         * @param timeout The timeout for the calls to the VerazialID Version Server
         * @return A new instance of the VerazialID Version REST API
         */
        fun createClient(
            baseUrl: String,
            timeout: Duration,
            user: String,
            password: String,
            unsafeHttps: Boolean = false
        ): VersionApi {
            val okHttpClient = OkHttpClient.Builder()
                .callTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .addInterceptor(basicAuthInterceptor(user, password))
                .run { if (unsafeHttps) ignoreSSLErrors() else this }
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(MoshiConverterFactory.create())
                .client(okHttpClient)
                .build()
                .create()
        }
    }
}

/**
 * Download latest version of a VerazialID App as an InputStream.
 */
suspend fun VersionApi.downloadClientStream(idProduct: String): Pair<Long, InputStream> =
    downloadClient(idProduct).run { contentLength() to byteStream() }