package com.verazial.server.api

import com.verazial.server.model.ApiResponse
import com.verazial.server.model.clock.ClockEvent
import com.verazial.server.utils.basicAuthInterceptor
import com.verazial.server.utils.ignoreSSLErrors
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * VerazialID Clock REST API
 */
interface ClockApi {

    /**
     * Creates a new event.
     *
     * Creates a new record with the specified event data.
     */
    @POST("v1/clock/events")
    suspend fun createEvent(
        @Body event: ClockEvent
    ): ApiResponse


    companion object {
        /**
         * Create a new instance of the VerazialID Clock REST API
         *
         * @param baseUrl The base URL of the VerazialID Clock Server
         * @param timeout The timeout for the calls to the VerazialID Clock Server
         * @return A new instance of the VerazialID Clock REST API
         */
        fun createClient(
            baseUrl: String,
            timeout: Duration,
            user: String,
            password: String,
            unsafeHttps: Boolean = false
        ): ClockApi {
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