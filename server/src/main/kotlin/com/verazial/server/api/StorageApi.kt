package com.verazial.server.api

import com.verazial.server.model.storage.GetResourceResponse
import com.verazial.server.utils.basicAuthInterceptor
import com.verazial.server.utils.ignoreSSLErrors
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * VerazialID Storage REST API
 */
interface StorageApi {


    /**
     * Retrieves the specified static resource.
     *
     * Locates the resource given its identifier and reads its data.
     */
    @GET("v1/storage/resources/{resourcePath}")
    suspend fun getResource(
        @Path("resourcePath") resourcePath: String
    ): GetResourceResponse


    companion object {
        /**
         * Create a new instance of the VerazialID Storage REST API
         *
         * @param baseUrl The base URL of the VerazialID Storage Server
         * @param timeout The timeout for the calls to the VerazialID Storage Server
         * @return A new instance of the VerazialID Storage REST API
         */
        fun createClient(
            baseUrl: String,
            timeout: Duration,
            user: String,
            password: String,
            unsafeHttps: Boolean = false
        ): StorageApi {
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