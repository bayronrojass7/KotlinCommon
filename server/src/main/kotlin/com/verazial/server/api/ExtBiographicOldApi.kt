package com.verazial.server.api

import com.verazial.server.model.ApiResponse
import com.verazial.server.model.extbiographic.ClockActionOld
import com.verazial.server.model.extbiographic.GetClockActionsOldResponse
import com.verazial.server.model.extbiographic.GetIdentityOldResponse
import com.verazial.server.utils.basicAuthInterceptor
import com.verazial.server.utils.ignoreSSLErrors
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * VerazialID External Biographic REST API
 * This is the old version of the API
 * It is used by the old version of the VerazialID app
 * It is deprecated and will be removed in the future
 */
interface ExtBiographicOldApi {

    /**
     * Retrieves an identity given an id.
     */
    @GET("getIdentity")
    suspend fun getIdentity(
        @Query("nid") nid: String
    ): GetIdentityOldResponse

    /**
     * Retrieves the available clock actions
     * for a user given an id.
     */

    @GET("getClockActions")
    suspend fun getActions(
        @Query("nid") nid: String
    ): GetClockActionsOldResponse

    /**
     * Creates a new event record with the specified event data.
     */
    @POST("postAction")
    suspend fun postAction(
        @Query("nid") nid: String,
        @Body action: ClockActionOld
    ): ApiResponse


    companion object {
        /**
         * Create a new instance of the VerazialID Biographic REST API
         *
         * @param baseUrl The base URL of the VerazialID Biographic Server
         * @param timeout The timeout for the calls to the VerazialID Biographic Server
         * @return A new instance of the VerazialID Biometric REST API
         */
        fun createClient(
            baseUrl: String,
            timeout: Duration,
            user: String,
            password: String,
            unsafeHttps: Boolean = false
        ): ExtBiographicOldApi {
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