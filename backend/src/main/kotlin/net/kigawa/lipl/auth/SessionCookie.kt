package net.kigawa.lipl.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class SessionPayload(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Long,
    val refreshExpiresAt: Long,
)

private const val GCM_IV_LENGTH_BYTES = 12
private const val GCM_TAG_LENGTH_BITS = 128

fun sessionEncryptionKeyFromEnv(): ByteArray {
    val encoded = System.getenv("SESSION_ENCRYPTION_KEY")
        ?: error("環境変数 SESSION_ENCRYPTION_KEY が設定されていません（32byteをBase64エンコードした値）")
    val key = try {
        Base64.getDecoder().decode(encoded)
    } catch (e: IllegalArgumentException) {
        error("環境変数 SESSION_ENCRYPTION_KEY がBase64として不正です")
    }
    if (key.size != 32) {
        error("環境変数 SESSION_ENCRYPTION_KEY は32byte（Base64エンコード後）である必要があります")
    }
    return key
}

// セッションCookieの値はAES-256-GCMで暗号化する。ブラウザ側からは中身が見えず（httpOnly）、
// 改ざん・キー不一致・壊れた値は例外を投げずnullを返す（呼び出し側は「未ログイン」として扱う）。
class SessionCookieCodec(private val key: ByteArray) {
    private val secretKey = SecretKeySpec(key, "AES")
    private val secureRandom = SecureRandom()

    fun encode(payload: SessionPayload): String {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(Json.encodeToString(SessionPayload.serializer(), payload).toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(iv + ciphertext)
    }

    fun decode(cookieValue: String): SessionPayload? = try {
        val raw = Base64.getUrlDecoder().decode(cookieValue)
        val iv = raw.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = raw.copyOfRange(GCM_IV_LENGTH_BYTES, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext)
        Json.decodeFromString(SessionPayload.serializer(), String(plaintext))
    } catch (e: Exception) {
        null
    }
}
