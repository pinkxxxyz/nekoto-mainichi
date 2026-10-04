package com.catlife.app.backup

import com.catlife.app.data.ShoppingItem
import com.catlife.app.data.TodoItem
import java.io.*
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

sealed class BackupPreference {
    data class Bool(val value: Boolean) : BackupPreference()
    data class Text(val value: String) : BackupPreference()
    data class Integer(val value: Int) : BackupPreference()
    data class LongNumber(val value: Long) : BackupPreference()
    data class FloatNumber(val value: Float) : BackupPreference()
    data class DoubleNumber(val value: Double) : BackupPreference()
    data class TextSet(val value: Set<String>) : BackupPreference()
}

data class UserBackup(
    val version: Int = 1,
    val todos: List<TodoItem> = emptyList(),
    val shopping: List<ShoppingItem> = emptyList(),
    val settings: Map<String, BackupPreference> = emptyMap(),
)

object BackupCodec {
    const val MAX_BYTES = 10 * 1024 * 1024
    private const val MAX_ITEMS = 10_000
    private val magic = "KorokkeLifeBK".toByteArray(Charsets.US_ASCII)

    fun validate(data: UserBackup) {
        require(data.version == 1) { "対応していないバックアップversionです" }
        require(data.todos.size <= MAX_ITEMS && data.shopping.size <= MAX_ITEMS)
        require(data.todos.map { it.id }.distinct().size == data.todos.size)
        require(data.shopping.map { it.id }.distinct().size == data.shopping.size)
        data.todos.forEach {
            require(it.id > 0 && it.title.isNotBlank() && it.title.length <= 10_000 && it.createdAt >= 0)
            it.deadlineDate?.let(LocalDate::parse)
            it.deadlineTime?.let(LocalTime::parse)
            it.reminderTime?.let(LocalTime::parse)
            it.reminderDateTime?.let(LocalDateTime::parse)
        }
        data.shopping.forEach { require(it.id > 0 && it.title.isNotBlank() && it.title.length <= 10_000 && it.createdAt >= 0) }
        require(data.settings.size <= 256)
        data.settings.forEach { (key, value) ->
            require(key.isNotBlank() && key.length <= 256)
            when (value) {
                is BackupPreference.Text -> require(value.value.length <= 10_000)
                is BackupPreference.TextSet -> require(value.value.size <= MAX_ITEMS && value.value.all { it.length <= 10_000 })
                is BackupPreference.FloatNumber -> require(value.value.isFinite())
                is BackupPreference.DoubleNumber -> require(value.value.isFinite())
                else -> Unit
            }
            if (key in setOf("cat_voice_enabled", "sound_enabled", "tutorial_done")) require(value is BackupPreference.Bool)
            if (key == "weather_location") require(value is BackupPreference.Text && value.value.isNotBlank() && value.value.length <= 100)
        }
    }

    fun encode(data: UserBackup): ByteArray {
        try {
            validate(data)
            val buffer = object : ByteArrayOutputStream() {
                override fun write(value: Int) {
                    require(size() < MAX_BYTES - 32) { "バックアップが大きすぎます" }
                    super.write(value)
                }
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    require(size() + length <= MAX_BYTES - 32) { "バックアップが大きすぎます" }
                    super.write(bytes, offset, length)
                }
            }
            DataOutputStream(buffer).use { out ->
                out.write(magic); out.writeInt(data.version)
                out.writeInt(data.todos.size)
                data.todos.forEach {
                    out.writeLong(it.id); out.writeUTF(it.title); out.writeBoolean(it.completed)
                    out.nullable(it.deadlineDate); out.nullable(it.deadlineTime); out.nullable(it.reminderDateTime)
                    out.writeBoolean(it.reminderEnabled); out.nullable(it.reminderTime); out.writeLong(it.createdAt)
                }
                out.writeInt(data.shopping.size)
                data.shopping.forEach {
                    out.writeLong(it.id); out.writeUTF(it.title); out.writeBoolean(it.purchased); out.writeLong(it.createdAt)
                }
                out.writeInt(data.settings.size)
                data.settings.toSortedMap().forEach { (key, value) ->
                    out.writeUTF(key)
                    when (value) {
                        is BackupPreference.Bool -> { out.writeByte(1); out.writeBoolean(value.value) }
                        is BackupPreference.Text -> { out.writeByte(2); out.writeUTF(value.value) }
                        is BackupPreference.Integer -> { out.writeByte(3); out.writeInt(value.value) }
                        is BackupPreference.LongNumber -> { out.writeByte(4); out.writeLong(value.value) }
                        is BackupPreference.FloatNumber -> { out.writeByte(5); out.writeFloat(value.value) }
                        is BackupPreference.DoubleNumber -> { out.writeByte(6); out.writeDouble(value.value) }
                        is BackupPreference.TextSet -> {
                            out.writeByte(7); out.writeInt(value.value.size); value.value.sorted().forEach(out::writeUTF)
                        }
                    }
                }
            }
            val payload = buffer.toByteArray()
            require(payload.size <= MAX_BYTES - 32) { "バックアップが大きすぎます" }
            return payload + MessageDigest.getInstance("SHA-256").digest(payload)
        } catch (error: Exception) {
            throw IllegalArgumentException("バックアップデータが不正です: ${error.message}", error)
        }
    }

    fun decode(bytes: ByteArray): UserBackup {
        try {
            require(bytes.size in (magic.size + 4 + 32)..MAX_BYTES)
            val payload = bytes.copyOf(bytes.size - 32)
            require(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(payload), bytes.copyOfRange(payload.size, bytes.size))) {
                "バックアップが破損しています"
            }
            val input = DataInputStream(ByteArrayInputStream(payload))
            val header = ByteArray(magic.size); input.readFully(header); require(header.contentEquals(magic))
            val version = input.readInt(); require(version == 1) { "対応していないバックアップversionです" }
            val todos = List(input.count(MAX_ITEMS)) {
                TodoItem(input.readLong(), input.readUTF(), input.readBoolean(), input.nullable(), input.nullable(), input.nullable(), input.readBoolean(), input.nullable(), input.readLong())
            }
            val shopping = List(input.count(MAX_ITEMS)) {
                ShoppingItem(input.readLong(), input.readUTF(), input.readBoolean(), input.readLong())
            }
            val settings = linkedMapOf<String, BackupPreference>()
            repeat(input.count(256)) {
                val key = input.readUTF(); require(key !in settings)
                settings[key] = when (input.readUnsignedByte()) {
                    1 -> BackupPreference.Bool(input.readBoolean())
                    2 -> BackupPreference.Text(input.readUTF())
                    3 -> BackupPreference.Integer(input.readInt())
                    4 -> BackupPreference.LongNumber(input.readLong())
                    5 -> BackupPreference.FloatNumber(input.readFloat())
                    6 -> BackupPreference.DoubleNumber(input.readDouble())
                    7 -> {
                        val values = List(input.count(MAX_ITEMS)) { input.readUTF() }
                        require(values.distinct().size == values.size)
                        BackupPreference.TextSet(values.toSet())
                    }
                    else -> error("不明な設定データです")
                }
            }
            require(input.available() == 0)
            return UserBackup(version, todos, shopping, settings).also(::validate)
        } catch (error: Exception) {
            throw IllegalArgumentException("有効なKorokkeLifeバックアップではありません: ${error.message}", error)
        }
    }

    private fun DataOutputStream.nullable(value: String?) { writeBoolean(value != null); if (value != null) writeUTF(value) }
    private fun DataInputStream.nullable(): String? = if (readBoolean()) readUTF() else null
    private fun DataInputStream.count(max: Int): Int = readInt().also { require(it in 0..max) }
}
