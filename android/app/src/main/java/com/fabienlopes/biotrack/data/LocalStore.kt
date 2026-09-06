package com.fabienlopes.biotrack.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class LocalStore private constructor(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "biotrack-snapshot.json"))
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun load(): AppSnapshot {
        synchronized(fileLock) {
            if (!file.exists()) return AppSnapshot()
            val decoded = runCatching {
                val raw = file.readText()
                val sourceVersion = SnapshotMigration.sourceVersion(raw, json)
                val migrated = SnapshotMigration.decode(raw, json)
                sourceVersion to migrated
            }.getOrElse {
                preserveCorruptedFile()
                return AppSnapshot()
            }
            if (decoded.first != SnapshotMigration.CURRENT_SCHEMA_VERSION) {
                // A persistence failure must not discard a snapshot that decoded successfully.
                runCatching { writeAtomically(json.encodeToString(decoded.second)) }
            }
            return decoded.second
        }
    }

    fun save(snapshot: AppSnapshot) {
        synchronized(fileLock) {
            writeAtomically(json.encodeToString(SnapshotMigration.migrate(snapshot)))
        }
    }

    fun encode(snapshot: AppSnapshot): String = json.encodeToString(SnapshotMigration.migrate(snapshot))

    fun decode(raw: String): AppSnapshot = SnapshotMigration.decode(raw, json)

    private fun writeAtomically(serialized: String) {
        val parent = requireNotNull(file.parentFile) { "Snapshot directory is unavailable." }
        Files.createDirectories(parent.toPath())
        val temporary = Files.createTempFile(parent.toPath(), "${file.name}.", ".tmp")
        try {
            FileOutputStream(temporary.toFile()).use { output ->
                output.write(serialized.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary,
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun preserveCorruptedFile() {
        val backup = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
        runCatching { file.copyTo(backup, overwrite = false) }
    }

    companion object {
        internal fun forFile(file: File): LocalStore = LocalStore(file)

        /** Coordinates all LocalStore instances in this process around read/migrate/write. */
        private val fileLock = Any()
    }
}

object EncryptedBackup {
    private const val prefix = "BTSEC1"
    private const val iterations = 150_000

    fun encrypt(payload: String, passphrase: CharArray): String {
        require(passphrase.isNotEmpty()) { "Le mot de passe ne peut pas être vide." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        return listOf(prefix, salt.encode(), iv.encode(), encrypted.encode()).joinToString(":")
    }

    fun decrypt(serialized: String, passphrase: CharArray): String {
        require(passphrase.isNotEmpty()) { "Le mot de passe ne peut pas être vide." }
        val parts = serialized.split(":")
        require(parts.size == 4 && parts[0] == prefix) { "Sauvegarde chiffrée invalide." }
        val salt = parts[1].decode()
        val iv = parts[2].decode()
        val ciphertext = parts[3].decode()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.encode(): String = Base64.getEncoder().withoutPadding().encodeToString(this)
    private fun String.decode(): ByteArray = Base64.getDecoder().decode(this)
}
