package com.verazial.server.utils

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Suppress("TrustAllX509TrustManager", "CustomX509TrustManager")
internal fun OkHttpClient.Builder.ignoreSSLErrors(): OkHttpClient.Builder = apply {
    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return arrayOf()
        }
    }

    val sslContext = SSLContext.getInstance("SSL")
    sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())

    sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier { _, _ -> true }
}