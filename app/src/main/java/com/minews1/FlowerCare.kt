package com.minews1

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * Чтение журнала Xiaomi Flower Care по GATT.
 *
 * В эфир датчик отдаёт только текущее значение; всё, что он запомнил раньше,
 * лежит внутри и достаётся лишь подключением. Протокол разобран сообществом
 * (библиотека miflora): в управляющую характеристику пишется команда, ответ
 * читается из соседней.
 */
object FlowerCare {
    private val HISTORY_CONTROL = UUID.fromString("00001a10-0000-1000-8000-00805f9b34fb")
    private val HISTORY_DATA = UUID.fromString("00001a11-0000-1000-8000-00805f9b34fb")
    private val DEVICE_TIME = UUID.fromString("00001a12-0000-1000-8000-00805f9b34fb")
    private val FIRMWARE = UUID.fromString("00001a02-0000-1000-8000-00805f9b34fb")

    // Открывает журнал на чтение. Команда A2 в ту же характеристику стирает
    // журнал в датчике, поэтому её здесь нет и быть не должно.
    private val CMD_HISTORY_INIT = byteArrayOf(0xA0.toByte(), 0x00, 0x00)
    private const val CMD_HISTORY_ENTRY = 0xA1.toByte()

    private const val ENTRY_SIZE = 16
    private const val OP_TIMEOUT_MS = 10_000L
    private const val CONNECT_TIMEOUT_MS = 20_000L

    class Result(val entries: List<HistoryDb.Reading>, val battery: Int, val firmware: String)

    class Failure(message: String) : Exception(message)

    /**
     * Подключается, вычитывает журнал целиком и отключается.
     * [onProgress] вызывается после каждой записи: сделано, всего.
     */
    @SuppressLint("MissingPermission")
    suspend fun readHistory(
        context: Context,
        address: String,
        onProgress: (Int, Int) -> Unit,
    ): Result {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: throw Failure("Bluetooth недоступен")
        if (!adapter.isEnabled) throw Failure("Включите Bluetooth")
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw Failure("Неверный адрес датчика")
        }
        val session = Session()
        val gatt = try {
            if (Build.VERSION.SDK_INT >= 23) {
                device.connectGatt(context, false, session, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, session)
            }
        } catch (e: SecurityException) {
            throw Failure("Нет разрешения на подключение")
        } ?: throw Failure("Не удалось начать подключение")

        try {
            session.awaitServices()

            var battery = -1
            var firmware = ""
            // Прошивка и заряд не обязательны для журнала: если не отдались, читаем дальше.
            runCatching { session.read(gatt, FIRMWARE) }.getOrNull()?.let { v ->
                if (v.isNotEmpty()) battery = v[0].toInt() and 255
                if (v.size > 2) firmware = String(v, 2, v.size - 2, Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
            }

            session.write(gatt, HISTORY_CONTROL, CMD_HISTORY_INIT)
            val head = session.read(gatt, HISTORY_DATA)
            if (head.size < 2) throw Failure("Датчик не отдал размер журнала")
            val count = le16(head, 0)

            val bootMs = runCatching { session.read(gatt, DEVICE_TIME) }.getOrNull()
                ?.takeIf { it.size >= 4 }
                ?.let { System.currentTimeMillis() - le32(it, 0) * 1000L }
                ?: throw Failure("Датчик не отдал своё время — даты записей не восстановить")

            val entries = ArrayList<HistoryDb.Reading>(count)
            onProgress(0, count)
            for (i in 0 until count) {
                session.write(gatt, HISTORY_CONTROL, byteArrayOf(CMD_HISTORY_ENTRY, (i and 255).toByte(), ((i shr 8) and 255).toByte()))
                val v = session.read(gatt, HISTORY_DATA)
                if (v.size >= ENTRY_SIZE) entries.add(parseEntry(v, bootMs))
                onProgress(i + 1, count)
            }
            return Result(entries, battery, firmware)
        } catch (e: TimeoutCancellationException) {
            throw Failure("Датчик не отвечает")
        } finally {
            try { gatt.disconnect() } catch (e: SecurityException) { /* no-op */ }
            try { gatt.close() } catch (e: SecurityException) { /* no-op */ }
        }
    }

    private fun parseEntry(v: ByteArray, bootMs: Long): HistoryDb.Reading {
        val seconds = le32(v, 0)
        val temp = le16s(v, 4) / 10f
        val lux = le32(v, 7).toFloat()
        val moisture = (v[11].toInt() and 255).toFloat()
        val cond = le16(v, 12).toFloat()
        return HistoryDb.reading(bootMs + seconds * 1000L, temp, moisture, lux, cond)
    }

    private fun u(b: ByteArray, i: Int) = b[i].toInt() and 255
    private fun le16(b: ByteArray, i: Int) = u(b, i) or (u(b, i + 1) shl 8)
    private fun le16s(b: ByteArray, i: Int) = le16(b, i).toShort().toInt()
    private fun le32(b: ByteArray, i: Int): Long =
        (u(b, i).toLong()) or (u(b, i + 1).toLong() shl 8) or (u(b, i + 2).toLong() shl 16) or (u(b, i + 3).toLong() shl 24)

    /**
     * Android разрешает одну операцию GATT за раз, поэтому каждая ждёт свой ответ,
     * и только потом отправляется следующая.
     */
    private class Session : BluetoothGattCallback() {
        private val ready = CompletableDeferred<Unit>()
        @Volatile private var pending: CompletableDeferred<ByteArray>? = null

        suspend fun awaitServices() {
            withTimeout(CONNECT_TIMEOUT_MS) { ready.await() }
        }

        @SuppressLint("MissingPermission")
        suspend fun read(gatt: BluetoothGatt, uuid: UUID): ByteArray {
            val ch = find(gatt, uuid) ?: throw Failure("Датчик не поддерживает чтение журнала")
            val slot = CompletableDeferred<ByteArray>()
            pending = slot
            if (!gatt.readCharacteristic(ch)) throw Failure("Датчик отклонил чтение")
            return withTimeout(OP_TIMEOUT_MS) { slot.await() }
        }

        @SuppressLint("MissingPermission")
        suspend fun write(gatt: BluetoothGatt, uuid: UUID, value: ByteArray) {
            val ch = find(gatt, uuid) ?: throw Failure("Датчик не поддерживает чтение журнала")
            val slot = CompletableDeferred<ByteArray>()
            pending = slot
            val ok = if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeCharacteristic(ch, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                    BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    ch.value = value
                    gatt.writeCharacteristic(ch)
                }
            }
            if (!ok) throw Failure("Датчик отклонил команду")
            withTimeout(OP_TIMEOUT_MS) { slot.await() }
        }

        // Характеристики журнала и датчика лежат в разных сервисах у разных прошивок,
        // поэтому ищем по всему дереву, а не по заранее известному сервису.
        private fun find(gatt: BluetoothGatt, uuid: UUID): BluetoothGattCharacteristic? =
            gatt.services.firstNotNullOfOrNull { it.getCharacteristic(uuid) }

        private fun deliver(value: ByteArray?, status: Int) {
            val slot = pending ?: return
            pending = null
            if (status == BluetoothGatt.GATT_SUCCESS) slot.complete(value ?: ByteArray(0))
            else slot.completeExceptionally(Failure("Ошибка GATT: $status"))
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                try { gatt.discoverServices() } catch (e: SecurityException) { ready.completeExceptionally(Failure("Нет разрешения на подключение")) }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val err = Failure("Связь с датчиком разорвана")
                ready.completeExceptionally(err)
                pending?.completeExceptionally(err)
                pending = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) ready.complete(Unit)
            else ready.completeExceptionally(Failure("Не удалось прочитать сервисы датчика"))
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT < 33) deliver(characteristic.value, status)
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            deliver(value, status)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            deliver(ByteArray(0), status)
        }
    }
}
