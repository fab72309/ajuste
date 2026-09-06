package com.fabienlopes.biotrack

import com.fabienlopes.biotrack.data.EncryptedBackup
import com.fabienlopes.biotrack.data.AppSnapshot
import com.fabienlopes.biotrack.data.ExchangeFormat
import com.fabienlopes.biotrack.data.ProtocolItem
import org.junit.Assert.assertEquals
import org.junit.Test

class EncryptedBackupTest {
    @Test
    fun encryptedPayloadRoundTrips() {
        val plaintext = "{\"schemaVersion\":3}"
        val passphrase = "correct horse battery staple".toCharArray()
        val encrypted = EncryptedBackup.encrypt(plaintext, passphrase)
        assertEquals(plaintext, EncryptedBackup.decrypt(encrypted, passphrase))
    }

    @Test
    fun encryptedCurrentExchangeDecodesBeforeImportConfirmation() {
        val snapshot = AppSnapshot(protocols = listOf(ProtocolItem(id = "p", name = "Routine")))
        val passphrase = "correct horse battery staple".toCharArray()
        val encrypted = EncryptedBackup.encrypt(ExchangeFormat.encode(snapshot), passphrase)

        val decoded = ExchangeFormat.decode(EncryptedBackup.decrypt(encrypted, passphrase))

        assertEquals("p", decoded.protocols.single().id)
        assertEquals(4, decoded.schemaVersion)
    }
}
