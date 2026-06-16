package com.example.core.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    // Hashes password with SHA-256 and a generic salt for secure local user verification
    fun hashPassword(password: String, salt: String = "SecureSyncAppSalt"): String {
        return try {
            val combined = password + salt
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(combined.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            password // Fallback on error, though SHA-256 is always available
        }
    }

    // Direct local AES-128 Encryption/Decryption helper for security auditing/demonstration
    // Generates a 16-byte key from our seed string
    private fun deriveKey(seed: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(seed.toByteArray(Charsets.UTF_8)).copyOf(16)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plainText: String, secretKeySeed: String = "LocalSyncSecretSeed"): String {
        return try {
            val key = deriveKey(secretKeySeed)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val iv = ByteArray(16)
            SecureRandom().nextBytes(iv) // Random Initialization Vector (IV)
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = iv + encryptedBytes
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText // Fallback on failure
        }
    }

    fun decrypt(encryptedText: String, secretKeySeed: String = "LocalSyncSecretSeed"): String {
        return try {
            val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
            if (combined.size < 16) return encryptedText
            
            val iv = combined.copyOf(16)
            val encryptedBytes = combined.copyOfRange(16, combined.size)
            
            val key = deriveKey(secretKeySeed)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            encryptedText // Return unmodified on failure in decryption (e.g. if key doesn't match)
        }
    }
}
