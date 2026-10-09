package com.minews1

import android.os.ParcelUuid
import java.util.Locale

object Ble {
    val MINEW_UUID: ParcelUuid = ParcelUuid.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    val XIAOMI_UUID: ParcelUuid = ParcelUuid.fromString("0000fe95-0000-1000-8000-00805f9b34fb")
    val EDDYSTONE_UUID: ParcelUuid = ParcelUuid.fromString("0000feaa-0000-1000-8000-00805f9b34fb")

    fun u(b: ByteArray, i: Int): Int = b[i].toInt() and 255

    fun isXiaomi(d: ByteArray?): Boolean {
        if (d == null || d.size < 15) return false
        if (u(d, 0) != 0x50 || u(d, 1) != 0x20 || u(d, 2) != 0xaa || u(d, 3) != 0x01) return false
        val type = u(d, 11)
        return type == 0x0D || type == 0x0A || type == 0x06 || type == 0x04
    }

    fun xiaomiMac(d: ByteArray?): String? {
        if (d == null || d.size < 11) return null
        return String.format(
            Locale.US, "%02X:%02X:%02X:%02X:%02X:%02X",
            u(d, 5), u(d, 6), u(d, 7), u(d, 8), u(d, 9), u(d, 10),
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
