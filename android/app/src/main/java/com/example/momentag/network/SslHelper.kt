package com.example.momentag.network

import android.content.Context
import com.example.momentag.R
import okhttp3.OkHttpClient
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * SslHelper
 *
 * Provides SSL configuration for OkHttpClient to trust embedded server certificate
 * This is needed because we're using a self-signed certificate
 */
object SslHelper {
    /**
     * Configures OkHttpClient.Builder to trust the embedded server certificate
     * @param builder The OkHttpClient.Builder to configure
     * @param context Android context to access resources
     * @return The configured builder
     */
    fun configureToTrustCertificate(
        builder: OkHttpClient.Builder,
        context: Context,
    ): OkHttpClient.Builder {
        try {
            // Load the embedded certificate
            val cf = CertificateFactory.getInstance("X.509")
            val certificateInputStream: InputStream = context.resources.openRawResource(R.raw.server_cert)
            val ca: Certificate =
                certificateInputStream.use {
                    cf.generateCertificate(it)
                }

            // Create a KeyStore containing our trusted certificate
            val keyStoreType = KeyStore.getDefaultType()
            val keyStore =
                KeyStore.getInstance(keyStoreType).apply {
                    load(null, null)
                    setCertificateEntry("server", ca)
                }

            // Create a TrustManager that trusts the certificate in our KeyStore
            val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
            val tmf =
                TrustManagerFactory.getInstance(tmfAlgorithm).apply {
                    init(keyStore)
                }

            // Create an SSLContext that uses our TrustManager
            val sslContext =
                SSLContext.getInstance("TLS").apply {
                    init(null, tmf.trustManagers, null)
                }

            val trustManager = tmf.trustManagers[0] as X509TrustManager

            // Configure the OkHttpClient to use this SSLContext
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)

            // Add hostname verifier for our specific domain
            val hostnameVerifier =
                HostnameVerifier { hostname, _ ->
                    hostname == "hcs.swpp-2025.snu.ac.kr"
                }
            builder.hostnameVerifier(hostnameVerifier)
        } catch (e: Exception) {
            // Don't throw - let the connection fail naturally with better error messages
        }

        return builder
    }
}
