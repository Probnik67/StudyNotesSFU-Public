package ru.studynotes.sfu.schedule

import android.content.Context
import ru.studynotes.sfu.R
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal object SfuTlsChainRepair {

    private const val EXPECTED_HOST = "edu.sfu-kras.ru"

    fun socketFactory(context: Context, host: String): SSLSocketFactory {
        require(host.equals(EXPECTED_HOST, ignoreCase = true)) {
            "SFU TLS chain repair is restricted to $EXPECTED_HOST"
        }

        val intermediate = context.resources
            .openRawResource(R.raw.globalsign_gcc_r3_dv_tls_ca_2020)
            .use { input ->
                CertificateFactory.getInstance("X.509")
                    .generateCertificate(input) as X509Certificate
            }

        val systemTrustManager = systemTrustManager()

        val repairingTrustManager = object : X509TrustManager {

            override fun getAcceptedIssuers(): Array<X509Certificate> =
                systemTrustManager.acceptedIssuers

            override fun checkClientTrusted(
                chain: Array<X509Certificate>,
                authType: String
            ) {
                systemTrustManager.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(
                chain: Array<X509Certificate>,
                authType: String
            ) {
                try {
                    systemTrustManager.checkServerTrusted(chain, authType)
                    return
                } catch (original: java.security.cert.CertificateException) {
                    if (chain.isEmpty()) {
                        throw original
                    }

                    val leaf = chain[0]

                    if (leaf.issuerX500Principal != intermediate.subjectX500Principal) {
                        throw original
                    }

                    intermediate.checkValidity()

                    try {
                        leaf.verify(intermediate.publicKey)
                    } catch (_: Exception) {
                        throw original
                    }

                    val repaired = ArrayList<X509Certificate>(chain.size + 1)
                    repaired.add(leaf)
                    repaired.add(intermediate)

                    for (certificate in chain.drop(1)) {
                        if (certificate.subjectX500Principal != intermediate.subjectX500Principal) {
                            repaired.add(certificate)
                        }
                    }

                    systemTrustManager.checkServerTrusted(
                        repaired.toTypedArray(),
                        authType
                    )
                }
            }
        }

        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(repairingTrustManager), null)
        }.socketFactory
    }

    private fun systemTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )

        factory.init(null as KeyStore?)

        return factory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .single()
    }
}
