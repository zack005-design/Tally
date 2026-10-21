package com.smartexpense.data

object ReceiptStorage {
    const val CHUNK_BYTES=512*1024
    const val TOTAL_BYTES=16*1024*1024
    fun hash(bytes:ByteArray)=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
    fun validate(bill:ScannedBill) {
        require(bill.bytes.isNotEmpty() && bill.bytes.size<=BillScanner.MAX_BYTES && bill.text.length<=10000)
        val bytes=bill.bytes
        val valid=when(bill.mime) {
            "application/pdf"->bytes.take(5).toByteArray().toString(Charsets.US_ASCII)=="%PDF-"
            "image/png"->bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))
            "image/jpeg"->bytes.size>2 && bytes[0]==(-1).toByte() && bytes[1]==(-40).toByte()
            "image/webp"->bytes.size>=12 && bytes.copyOfRange(0,4).toString(Charsets.US_ASCII)=="RIFF" && bytes.copyOfRange(8,12).toString(Charsets.US_ASCII)=="WEBP"
            else->false
        }
        require(valid) {"Unsupported or damaged receipt attachment"}
    }
    fun join(chunks:List<ReceiptChunkEntity>):ByteArray {
        require(chunks.isNotEmpty() && chunks.size<=12 && chunks.map {it.position}==chunks.indices.toList()) {"Incomplete receipt attachment"}
        require(chunks.all {it.bytes.isNotEmpty() && it.bytes.size<=CHUNK_BYTES} && chunks.sumOf {it.bytes.size}<=BillScanner.MAX_BYTES)
        val out=java.io.ByteArrayOutputStream();chunks.forEach {out.write(it.bytes)};return out.toByteArray()
    }
}
