package com.gozcu.app

class RemoteIdBilgi {
    var kimlik: String? = null
    var pilotLat: Double? = null
    var pilotLon: Double? = null
}

object RemoteId {

    fun coz(d: ByteArray): RemoteIdBilgi {
        val bilgi = RemoteIdBilgi()
        if (d.size < 27) return bilgi

        val ilk = (d[2].toInt() and 0xFF) shr 4
        if (ilk == 0xF) {
            val boy = d[3].toInt() and 0xFF
            val adet = d[4].toInt() and 0xFF
            for (k in 0 until adet) mesaj(d, 5 + k * boy, bilgi)
        } else {
            mesaj(d, 2, bilgi)
        }
        return bilgi
    }

    private fun mesaj(d: ByteArray, bas: Int, bilgi: RemoteIdBilgi) {
        if (bas < 0 || bas + 25 > d.size) return
        val tip = (d[bas].toInt() and 0xFF) shr 4
        when (tip) {
            0 -> {
                val ham = d.copyOfRange(bas + 2, bas + 22)
                val metin = String(ham, Charsets.US_ASCII).trim { it == '\u0000' || it == ' ' }
                if (metin.isNotEmpty()) bilgi.kimlik = metin
            }
            4 -> {
                val lat = int32(d, bas + 2) / 1e7
                val lon = int32(d, bas + 6) / 1e7
                if (lat != 0.0 && lon != 0.0 && Math.abs(lat) <= 90.0 && Math.abs(lon) <= 180.0) {
                    bilgi.pilotLat = lat
                    bilgi.pilotLon = lon
                }
            }
        }
    }

    private fun int32(d: ByteArray, i: Int): Int =
        (d[i].toInt() and 0xFF) or
            ((d[i + 1].toInt() and 0xFF) shl 8) or
            ((d[i + 2].toInt() and 0xFF) shl 16) or
            ((d[i + 3].toInt() and 0xFF) shl 24)
}
