package com.verazial.server.api

import com.verazial.server.model.ApiResponse
import com.verazial.server.model.biometric.IdentifySubjectResponse
import com.verazial.server.model.biometric.Sample
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
 * VerazialID Biometric REST API
 */
interface BiometricApi {

    /**
     * Get a list of subjects.
     *
     * Get a list of subjects that meet a given condition.
     */
    @GET("v1/biometric/getSubjects")
    suspend fun getSubjects(
        @Query("filter") condition: String,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
    ): ApiResponse


    /**
     * Identifies a subject.
     *
     * Identifies a subject using the biometric samples provided.
     * Identification is performed using all available records in the database (1:N)
     * or just using a subset of them (1:n),
     * depending on the value of the filter parameter.
     */
    @POST("v1/biometric/identifySubject")
    suspend fun identifySubject(
        @Body samples: List<Sample>,
        @Query("filter") filter: String?
    ): IdentifySubjectResponse


    /**
     * Verifies a subject's identity.
     *
     * Verifies a subject's identity against the biometric sample provided (1:1).
     */
    @POST("v1/biometric/verifySubject")
    suspend fun verifySubject(
        @Body samples: Sample,
        @Query("id") id: String
    ): ApiResponse


    companion object {
        /**
         * Create a new instance of the VerazialID Biometric REST API
         *
         * @param baseUrl The base URL of the VerazialID Biometric Server
         * @param timeout The timeout for the calls to the VerazialID Biometric Server
         * @return A new instance of the VerazialID Biometric REST API
         */
        fun createClient(
            baseUrl: String,
            timeout: Duration,
            user: String,
            password: String,
            unsafeHttps: Boolean = false
        ): BiometricApi {
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