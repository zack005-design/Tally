package com.smartexpense.domain
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupEnvelope {
    private val header="SETB1".toByteArray(Charsets.US_ASCII)
    private fun key(password:CharArray,salt:ByteArray):SecretKeySpec {
        val spec=PBEKeySpec(password,salt,210000,256)
        return try {SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES")} finally {spec.clearPassword()}
    }
    fun encode(data:ByteArray,password:CharArray):ByteArray {
        if(password.isEmpty()) return data
        val random=SecureRandom();val salt=ByteArray(16).also(random::nextBytes);val nonce=ByteArray(12).also(random::nextBytes)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(password,salt),GCMParameterSpec(128,nonce));cipher.updateAAD(header)
        return header+salt+nonce+cipher.doFinal(data)
    }
    fun decode(data:ByteArray,password:CharArray):ByteArray {
        require(data.size<=32*1024*1024) {"Backup is too large"}
        if(!data.take(5).toByteArray().contentEquals(header)) return data
        require(data.size>=49 && password.isNotEmpty()) {"Enter the backup password"}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(password,data.copyOfRange(5,21)),GCMParameterSpec(128,data.copyOfRange(21,33)));cipher.updateAAD(header)
        return try {cipher.doFinal(data.copyOfRange(33,data.size))} catch(e:javax.crypto.AEADBadTagException) {throw IllegalArgumentException("Wrong password or damaged encrypted backup",e)}
    }
}
