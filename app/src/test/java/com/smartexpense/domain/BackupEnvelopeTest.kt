package com.smartexpense.domain
import org.junit.Assert.*
import org.junit.Test
class BackupEnvelopeTest {
    @Test fun encryptedRoundTrip() { val content="personal financial data".toByteArray();val encoded=BackupEnvelope.encode(content,"password".toCharArray());assertFalse(String(encoded).contains("personal financial data"));assertArrayEquals(content,BackupEnvelope.decode(encoded,"password".toCharArray())) }
    @Test(expected=Exception::class) fun wrongPasswordRejected() {BackupEnvelope.decode(BackupEnvelope.encode("data".toByteArray(),"correct".toCharArray()),"wrong".toCharArray())}
    @Test fun unencryptedRoundTrip() {val data="{}".toByteArray();assertArrayEquals(data,BackupEnvelope.decode(BackupEnvelope.encode(data,charArrayOf()),charArrayOf()))}
}
