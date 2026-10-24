package com.smartexpense.data

import android.util.JsonReader
import android.util.JsonToken
import com.smartexpense.domain.*
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Strict, bounded interchange decoder. No ledger writes or coercion of numeric amounts. */
object ChatHistoryJson {
    fun read(input: InputStream): ChatHistory {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() <= ChatHistoryImport.MAX_BYTES) {
            val count = input.read(buffer, 0, minOf(buffer.size, ChatHistoryImport.MAX_BYTES + 1 - output.size()))
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        require(bytes.size <= ChatHistoryImport.MAX_BYTES) { "History exceeds the 5 MiB limit" }
        val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        return parse(text)
    }

    fun parse(text: String): ChatHistory {
        require(text.toByteArray(StandardCharsets.UTF_8).size <= ChatHistoryImport.MAX_BYTES) { "History exceeds the 5 MiB limit" }
        try {
            JsonReader(StringReader(text.removePrefix("\uFEFF"))).use { reader ->
                val root = readObject(reader, true)
                require(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected content after history" }
                require(root["format"] == "tally-chat-history" && root["formatVersion"] == 1 && root["currency"] == "INR") { "Choose a Tally chat history version 1 file in INR" }
                val entries = root["entries"] as? List<*> ?: error("Missing entries")
                val rows = entries.map { value ->
                    @Suppress("UNCHECKED_CAST") val row = value as Map<String, Any?>
                    ChatHistoryEntry(required(row, "sourceId"), required(row, "kind"), row["date"] as String?, row["description"] as String?, row["amount"] as String?, row["account"] as String?, row["category"] as String?, row["notes"] as String?, row["shared"] as? Boolean ?: false,
                        @Suppress("UNCHECKED_CAST") (row["unresolved"] as? List<String>).orEmpty(), row["details"] as String?)
                }
                return ChatHistory(required(root, "historyId"), required(root, "coverage"), rows).also(ChatHistoryImport::validate)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { throw IllegalArgumentException("Invalid Tally history: ${failure.message ?: "malformed JSON"}", failure) }
    }

    private fun required(values: Map<String, Any?>, key: String): String = values[key] as? String ?: throw IllegalArgumentException("Missing $key")

    private fun readObject(reader: JsonReader, root: Boolean): Map<String, Any?> {
        val allowed = if (root) setOf("format", "formatVersion", "currency", "historyId", "coverage", "entries") else setOf("sourceId", "kind", "date", "description", "amount", "account", "category", "notes", "shared", "unresolved", "details")
        val result = linkedMapOf<String, Any?>()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            require(key in allowed && !result.containsKey(key)) { "Unknown or repeated field: $key" }
            result[key] = when (key) {
                "formatVersion" -> { require(reader.peek() == JsonToken.NUMBER) { "Version must be a number" }; require(reader.nextString() == "1") { "Unsupported history version" }; 1 }
                "entries" -> {
                    val entries = mutableListOf<Map<String, Any?>>()
                    reader.beginArray()
                    while (reader.hasNext()) { require(entries.size < ChatHistoryImport.MAX_ENTRIES) { "History exceeds 10,000 entries" }; entries += readObject(reader, false) }
                    reader.endArray(); entries
                }
                "unresolved" -> {
                    val reasons = mutableListOf<String>()
                    reader.beginArray()
                    while (reader.hasNext()) { require(reasons.size < 100); reasons += readString(reader) }
                    reader.endArray(); reasons
                }
                "shared" -> { require(reader.peek() == JsonToken.BOOLEAN) { "shared must be a boolean" }; reader.nextBoolean() }
                else -> if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else readString(reader)
            }
        }
        reader.endObject()
        return result
    }

    private fun readString(reader: JsonReader): String {
        require(reader.peek() == JsonToken.STRING) { "History values, including amounts, must be strings" }
        return reader.nextString().also { require(it.length <= 10000) { "History field is too long" } }
    }
}
