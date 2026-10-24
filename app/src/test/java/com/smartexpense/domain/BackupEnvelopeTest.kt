package com.smartexpense.domain
import org.junit.Assert.*
import org.junit.Test
class BackupEnvelopeTest {
    @Test fun encryptedRoundTrip() {
        val content="personal financial data".toByteArray()
        val encoded=BackupEnvelope.encode(content,"password".toCharArray())
        assertFalse(String(encoded).contains("personal financial data"))
        assertArrayEquals(content,BackupEnvelope.decode(encoded,"password".toCharArray()))
    }
    @Test(expected=IllegalArgumentException::class) fun emptyExportPasswordRejected() {BackupEnvelope.encode("data".toByteArray(),charArrayOf())}
    @Test(expected=IllegalArgumentException::class) fun shortExportPasswordRejected() {BackupEnvelope.encode("data".toByteArray(),"short".toCharArray())}
    @Test(expected=IllegalArgumentException::class) fun blankExportPasswordRejected() {BackupEnvelope.encode("data".toByteArray(),"        ".toCharArray())}
    @Test(expected=IllegalArgumentException::class) fun wrongPasswordRejected() {
        BackupEnvelope.decode(BackupEnvelope.encode("data".toByteArray(),"correct password".toCharArray()),"wrong".toCharArray())
    }
    @Test fun legacyPlaintextStillImports() {
        val data="{}".toByteArray()
        assertArrayEquals(data,BackupEnvelope.decode(data,charArrayOf()))
    }
    @Test fun legacyShortPasswordEncryptedBackupStillImports() {
        // Pre-policy SETB1 fixture encrypted with the three-character password "old".
        val data=java.util.Base64.getDecoder().decode("U0VUQjEBAgMEBQYHCAkKCwwNDg8QAQIDBAUGBwgJCgsMq4RHhdszgmdBK05heTTQ3qF6leWl0w==")
        assertArrayEquals("legacy".toByteArray(),BackupEnvelope.decode(data,"old".toCharArray()))
    }
    @Test(expected=IllegalArgumentException::class) fun tamperedCiphertextRejected() {
        val bytes=BackupEnvelope.encode("data".toByteArray(),"password".toCharArray())
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        BackupEnvelope.decode(bytes,"password".toCharArray())
    }
    @Test fun repeatedExportsUseDifferentSaltAndNonce() {
        val first=BackupEnvelope.encode("data".toByteArray(),"password".toCharArray())
        val second=BackupEnvelope.encode("data".toByteArray(),"password".toCharArray())
        assertFalse(first.contentEquals(second))
    }
}
