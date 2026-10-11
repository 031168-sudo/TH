package com.minews1

import android.os.ParcelUuid
import java.util.Locale

object Ble {
    val MINEW_UUID: ParcelUuid = ParcelUuid.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    val XIAOMI_UUID: ParcelUuid = ParcelUuid.fromString("0000fe95-0000-1000-8000-00805f9b34fb")
    val EDDYSTONE_UUID: ParcelUuid = ParcelUuid.fromString("0000feaa-0000-1000-8000-00805f9b34fb")

    const val PRODUCT_LYWSDCGQ = 0x01AA
    const val PRODUCT_FLOWER_CARE = 0x0098

    // Идентификаторы величин MiBeacon (объекты 0x10xx).
    const val OBJ_TEMPERATURE = 0x1004
    const val OBJ_HUMIDITY = 0x1006
    const val OBJ_ILLUMINANCE = 0x1007
    const val OBJ_MOISTURE = 0x1008
    const val OBJ_CONDUCTIVITY = 0x1009
    const val OBJ_BATTERY = 0x100A
    const val OBJ_TEMP_HUMIDITY = 0x100D

    /** Одна величина из рекламного пакета MiBeacon. */
    class XiaomiFrame(val product: Int, val mac: String?, val objectId: Int, val value: ByteArray)

    fun u(b: ByteArray, i: Int): Int = b[i].toInt() and 255

    /**
     * Разбирает service data Xiaomi (UUID 0xFE95).
     *
     * Длина заголовка зависит от флагов frame control: за продуктом и счётчиком
     * пакетов идут необязательные MAC и байт возможностей, и только потом сама
     * величина. Поэтому смещения считаются по флагам, а не зашиты числами:
     * у LYWSDCGQ байта возможностей нет, у Flower Care он есть.
     */
    fun parseXiaomi(d: ByteArray?): XiaomiFrame? {
        if (d == null || d.size < 5) return null
        val fc = u(d, 0) or (u(d, 1) shl 8)
        if (fc and 0x08 != 0) return null // зашифрованный пакет читать нечем
        if (fc and 0x40 == 0) return null // величины в пакете нет
        var o = 5
        var mac: String? = null
        if (fc and 0x10 != 0) {
            if (d.size < o + 6) return null
            mac = String.format(
                Locale.US, "%02X:%02X:%02X:%02X:%02X:%02X",
                u(d, o), u(d, o + 1), u(d, o + 2), u(d, o + 3), u(d, o + 4), u(d, o + 5),
            )
            o += 6
        }
        if (fc and 0x20 != 0) o += 1
        if (d.size < o + 3) return null
        val objectId = u(d, o) or (u(d, o + 1) shl 8)
        val len = u(d, o + 2)
        if (d.size < o + 3 + len) return null
        return XiaomiFrame(
            product = u(d, 2) or (u(d, 3) shl 8),
            mac = mac,
            objectId = objectId,
            value = d.copyOfRange(o + 3, o + 3 + len),
        )
    }

    fun minewEmbeddedMac(d: ByteArray?): String? {
        if (d == null || d.size < 13 || u(d, 0) != 0xA1 || u(d, 1) != 0x01) return null
        return String.format(
            Locale.US, "%02X:%02X:%02X:%02X:%02X:%02X",
            u(d, 12), u(d, 11), u(d, 10), u(d, 9), u(d, 8), u(d, 7),
        )
    }
}
