package com.gozcu.app

import android.bluetooth.le.ScanRecord
import android.os.ParcelUuid

object Imzalar {

    fun uuid16(kisa: Int): ParcelUuid =
        ParcelUuid.fromString("0000%04X-0000-1000-8000-00805F9B34FB".format(kisa))

    val U_REMOTE_ID = uuid16(0xFFFA)
    private val U_SAMSUNG = uuid16(0xFD5A)
    private val U_TILE = uuid16(0xFEED)
    private val U_CHIPOLO = uuid16(0xFE33)
    private val U_EDDYSTONE = uuid16(0xFEAA)
    private val META_UUID = listOf(uuid16(0xFEB7), uuid16(0xFEB8), uuid16(0xFD5F))
    private val META_ID = listOf(0x01AB, 0x058E, 0x0D53)

    private val URETICI = mapOf(
        0x004C to "Apple", 0x0075 to "Samsung", 0x0006 to "Microsoft",
        0x00E0 to "Google", 0x01AB to "Meta", 0x058E to "Meta",
        0x0D53 to "Luxottica", 0x038F to "Xiaomi", 0x027D to "Huawei"
    )

    private val DRONE_OUI = mapOf(
        "60:60:1F" to "DJI", "34:D2:62" to "DJI", "48:1C:B9" to "DJI",
        "90:03:B7" to "Parrot", "A0:14:3D" to "Parrot",
        "00:12:1C" to "Parrot", "00:26:7E" to "Parrot"
    )
    private val DRONE_SSID = listOf("DJI-", "DJI_", "TELLO-", "MAVIC", "PHANTOM", "ANAFI", "BEBOP")

    private fun varMi(k: ScanRecord, u: ParcelUuid): Boolean =
        k.getServiceData(u) != null || (k.serviceUuids?.contains(u) == true)

    fun tani(k: ScanRecord): Tespit? {
        if (k.getServiceData(U_REMOTE_ID) != null) {
            return Tespit(Tur.DRONE, "Drone (Remote ID)")
        }

        val apple = k.getManufacturerSpecificData(0x004C)
        if (apple != null && apple.size >= 2 &&
            (apple[0].toInt() and 0xFF) == 0x12 && (apple[1].toInt() and 0xFF) == 0x19
        ) {
            return Tespit(Tur.TAKIPCI, "Apple Find My aksesuarı (AirTag olabilir)")
        }

        if (varMi(k, U_SAMSUNG)) return Tespit(Tur.TAKIPCI, "Samsung SmartTag")
        if (varMi(k, U_TILE)) return Tespit(Tur.TAKIPCI, "Tile")
        if (varMi(k, U_CHIPOLO)) return Tespit(Tur.TAKIPCI, "Chipolo")

        val g = k.getServiceData(U_EDDYSTONE)
        if (g != null && g.isNotEmpty()) {
            val t = g[0].toInt() and 0xFF
            if (t == 0x40 || t == 0x41) return Tespit(Tur.TAKIPCI, "Google Find Hub takip cihazı")
        }

        for (id in META_ID) {
            if (k.getManufacturerSpecificData(id) != null) {
                return Tespit(Tur.GOZLUK, "Meta / Ray-Ban cihazı (gözlük olabilir)")
            }
        }
        for (u in META_UUID) {
            if (varMi(k, u)) return Tespit(Tur.GOZLUK, "Meta / Ray-Ban cihazı (gözlük olabilir)")
        }
        return null
    }

    fun uretici(k: ScanRecord): String? {
        val sa = k.manufacturerSpecificData ?: return null
        if (sa.size() == 0) return null
        return URETICI[sa.keyAt(0)]
    }

    fun wifiTani(oui: String, ssid: String): Tespit? {
        val marka = DRONE_OUI[oui]
        if (marka != null) return Tespit(Tur.DRONE, "$marka drone (Wi-Fi)")
        if (DRONE_SSID.any { ssid.startsWith(it) }) return Tespit(Tur.DRONE, "Drone ağı (Wi-Fi)")
        return null
    }
}
