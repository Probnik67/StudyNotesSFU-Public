package ru.studynotes.sfu.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.MessageDigest

object DeviceSigningKey {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "studynotes_backend_signing_v1"

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

    fun getOrCreateKeyPair(): KeyPair {
        val keyStore = keyStore()

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as? java.security.PrivateKey
            val publicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey

            if (privateKey != null && publicKey != null) {
                return KeyPair(publicKey, privateKey)
            }
        }

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()

        generator.initialize(spec)
        return generator.generateKeyPair()
    }

    fun publicKeyBase64(): String {
        val publicKey = getOrCreateKeyPair().public
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun keyId(): String {
        val publicKey = getOrCreateKeyPair().public.encoded
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey)

        return digest
            .take(16)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    fun sign(data: ByteArray): String {
        val privateKey = getOrCreateKeyPair().private

        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)

        return Base64.encodeToString(
            signature.sign(),
            Base64.NO_WRAP
        )
    }
}
