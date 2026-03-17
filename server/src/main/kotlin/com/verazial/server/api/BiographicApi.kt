package com.verazial.server.api

import com.verazial.server.model.biographic.GetIdentitiesByBody
import com.verazial.server.model.biographic.GetIdentitiesByResponse
import com.verazial.server.utils.basicAuthInterceptor
import com.verazial.server.utils.ignoreSSLErrors
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * VerazialID Biographic REST API
 */
interface BiographicApi {

    /**
     * Retrieves all existing identities by a given attribute.
     *
     * The criteria is an identity with the biographic
     * attributes to be matched.
     * An offset and a limit can be set to restrict the results
     */
    @POST("v1/biographic/getIdentitiesBy")
    suspend fun getIdentitiesBy(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
        @Body body: GetIdentitiesByBody
    ): GetIdentitiesByResponse


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
        ): BiographicApi {
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