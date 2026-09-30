package ru.studynotes.sfu.security

import java.security.MessageDigest
import java.util.UUID

object BackendRequestSigner {

    private const val PROTOCOL = "STUDYNOTES-V1"

    data class SignedHeaders(
        val keyId: String,
        val timestamp: String,
        val nonce: String,
        val contentSha256: String,
        val signature: String
    )

    fun emptyContentSha256(): String =
        sha256Hex(ByteArray(0))

    fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    fun analyzeContentSha256(
        fields: List<Pair<String, String>>,
        images: List<Pair<Int, ByteArray>>
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")

        fun updateText(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(bytes.size.toString().toByteArray(Charsets.US_ASCII))
            digest.update(':'.code.toByte())
            digest.update(bytes)
            digest.update('\n'.code.toByte())
        }

        digest.update("STUDYNOTES-ANALYZE-CONTENT-V1\n".toByteArray(Charsets.US_ASCII))

        fields.forEach { (name, value) ->
            digest.update("FIELD\n".toByteArray(Charsets.US_ASCII))
            updateText(name)
            updateText(value)
        }

        images.sortedBy { it.first }.forEach { (pageIndex, bytes) ->
            digest.update("IMAGE\n".toByteArray(Charsets.US_ASCII))
            updateText(pageIndex.toString())
            updateText(bytes.size.toString())
            updateText(sha256Hex(bytes))
        }

        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    fun create(
        method: String,
        path: String,
        contentSha256: String
    ): SignedHeaders {
        require(method.isNotBlank()) { "HTTP method is blank" }
        require(path.startsWith("/")) { "Request path must start with /" }
        require(contentSha256.matches(Regex("^[0-9a-f]{64}$"))) {
            "Content SHA-256 must be lowercase hexadecimal"
        }

        val timestamp = (System.currentTimeMillis() / 1000L).toString()
        val nonce = UUID.randomUUID().toString()

        val canonical = buildString {
            append(PROTOCOL)
            append('\n')
            append(timestamp)
            append('\n')
            append(nonce)
            append('\n')
            append(method.uppercase())
            append('\n')
            append(path)
            append('\n')
            append(contentSha256)
        }

        return SignedHeaders(
            keyId = DeviceSigningKey.keyId(),
            timestamp = timestamp,
            nonce = nonce,
            contentSha256 = contentSha256,
            signature = DeviceSigningKey.sign(
                canonical.toByteArray(Charsets.UTF_8)
            )
        )
    }
}