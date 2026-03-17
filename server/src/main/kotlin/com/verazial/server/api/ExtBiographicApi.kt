package com.verazial.server.api

import com.verazial.server.model.ApiResponse
import com.verazial.server.model.extbiographic.ClockAction
import com.verazial.server.model.extbiographic.GetClockActionsResponse
import com.verazial.server.model.extbiographic.GetIdentityResponse
import com.verazial.server.model.extbiographic.NewClockAction
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
 */
interface ExtBiographicApi {

    /**
     * Retrieves an identity given an id.
     */
    @GET("getIdentity")
    suspend fun getIdentity(
        @Query("nId") nId: String,
        @Query("profilePicture") profilePicture: Boolean = false
    ): GetIdentityResponse

    /**
     * Retrieves the available clock actions
     * for a user given an id.
     */
    @POST("getActions")
    suspend fun getActions(
        @Query("nId") nId: String,
        @Body clockAction: ClockAction
    ): GetClockActionsResponse

    /**
     * Creates a new event record with the specified event data.
     */
    @POST("postAction")
    suspend fun postAction(
        @Query("nId") nId: String,
        @Body action: NewClockAction
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
        ): ExtBiographicApi {
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