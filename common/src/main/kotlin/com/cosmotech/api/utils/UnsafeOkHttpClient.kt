// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.utils

import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

fun getUnsafeOkHttpClient(): OkHttpClient {
  // Create a trust manager that does not validate certificate chains
  val trustAllCerts =
      arrayOf<TrustManager>(
          object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) {}

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) {}

            override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
          })

  // Install the all-trusting trust manager
  val sslContext = SSLContext.getInstance("SSL")
  sslContext.init(null, trustAllCerts, java.security.SecureRandom())
  // Create an ssl socket factory with our all-trusting manager
  val sslSocketFactory = sslContext.socketFactory

  return OkHttpClient.Builder()
      .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
      .hostnameVerifier { _, _ -> true }
      .build()
}
