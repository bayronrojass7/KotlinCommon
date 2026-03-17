package com.verazial.server.api

import com.verazial.server.model.widget.SegmentatedSearchFilter
import com.verazial.server.utils.basicAuthInterceptor
import com.verazial.server.utils.ignoreSSLErrors
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * VerazialID Widget REST API
 */
interface WidgetApi {

    /**
     * Add a license to the Widget if possible.
     */
    @GET("api/VerazialID/AddLicense")
    suspend fun addLicense(): String

    /**
     * Remove a license from the Widget.
     */
    @POST("api/VerazialID/RemoveLicense/{license}")
    suspend fun removeLicense(
        @Path("license") license: String
    ): Boolean

    /**
     * Check if a license is valid.
     */
    @POST("api/VerazialID/CheckLicense/{license}")
    suspend fun checkLicense(
        @Path("license") license: String
    ): Boolean

    /**
     * Add a used device to the Widget.
     */
    @POST("api/VerazialID/AddDeviceUsed")
    suspend fun addDeviceUsed(
        @Query("segmentId") segmentId: String,
        @Query("deviceId") deviceId: String
    ): Boolean

    /**
     * Remove a used device from the Widget.
     */
    @POST("api/VerazialID/RemoveDeviceUsed")
    suspend fun removeDeviceUsed(
        @Query("segmentId") segmentId: String,
        @Query("deviceId") deviceId: String
    ): Boolean

    /**
     * Check if a device is used.
     */
    @POST("api/VerazialID/CheckDeviceUsed")
    suspend fun checkDeviceUsed(
        @Query("segmentId") segmentId: String,
        @Query("deviceId") deviceId: String
    ): Boolean

    /**
     * Get valid locations from the Widget.
     */
    @GET("api/VerazialID/GetLocations")
    suspend fun getLocations(): Map<String, Map<String, List<String>>>

    /**
     * Get segmented search fields from the Widget.
     */
    @GET("api/VerazialID/GetSegmentatedSearchFilters")
    suspend fun getSegmentatedSearchFilters(): List<SegmentatedSearchFilter>

    /**
     * Get Widgets' version name.
     */
    @GET("api/VerazialID/GetVersionName")
    suspend fun getVersionName(): String


    companion object {
        /**
         * Create a new instance of the VerazialID Widget REST API
         *
         * @param baseUrl The base URL of the VerazialID Widget Server
         * @param timeout The timeout for the calls to the VerazialID Widget Server
         * @return A new instance of the VerazialID Widget REST API
         */
        fun createClient(
            baseUrl: String,
            timeout: Duration,
            user: String,
            password: String,
            unsafeHttps: Boolean = false
        ): WidgetApi {
            val okHttpClient = OkHttpClient.Builder()
                .callTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                .run { if (unsafeHttps) ignoreSSLErrors() else this }
                .run {
                    if (user.isEmpty() || password.isEmpty()) this
                    else addInterceptor(basicAuthInterceptor(user, password))
                }
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(MoshiConverterFactory.create())
                .client(okHttpClient)
                .build()
                .create()
        }
    }
}