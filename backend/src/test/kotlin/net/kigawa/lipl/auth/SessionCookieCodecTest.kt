package net.kigawa.lipl.auth

import java.security.SecureRandom
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionCookieCodecTest {

    private fun randomKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    private val payload = SessionPayload(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        accessExpiresAt = 1_000_000L,
        refreshExpiresAt = 2_000_000L,
    )

    @Test
    fun `encode then decode round-trips the payload`() {
        val codec = SessionCookieCodec(randomKey())

        val decoded = codec.decode(codec.encode(payload))

        assertEquals(payload, decoded)
    }

    @Test
    fun `decoding with a different key returns null`() {
        val codec = SessionCookieCodec(randomKey())
        val encoded = codec.encode(payload)
        val otherCodec = SessionCookieCodec(randomKey())

        assertNull(otherCodec.decode(encoded))
    }

    @Test
    fun `decoding a tampered value returns null`() {
        val codec = SessionCookieCodec(randomKey())
        val encoded = codec.encode(payload)
        val tampered = encoded.dropLast(2) + "AA"

        assertNull(codec.decode(tampered))
    }

    @Test
    fun `decoding garbage returns null instead of throwing`() {
        val codec = SessionCookieCodec(randomKey())

        assertNull(codec.decode("not-a-valid-cookie-value"))
        assertNull(codec.decode(Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(4))))
    }
}
