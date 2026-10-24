package com.smartexpense

import com.smartexpense.data.BackupService
import com.smartexpense.domain.BackupEnvelope

/** Plaintext fixture for legacy import and malformed-JSON tests; production exports are encrypted. */
suspend fun BackupService.exportFixture():ByteArray {
    val password="fixture password".toCharArray()
    val encrypted=export(password)
    return try {BackupEnvelope.decode(encrypted,password)} finally {password.fill('\u0000');encrypted.fill(0)}
}
