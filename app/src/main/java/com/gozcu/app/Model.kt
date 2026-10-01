package com.gozcu.app

enum class Tur(val etiket: String, val seviye: Int) {
    TAKIPCI("Takip cihazı", 3),
    DRONE("Drone", 3),
    GOZLUK("Akıllı gözlük", 2)
}

data class Tespit(val tur: Tur, val ad: String)

data class Iz(val zaman: Long, val enlem: Double, val boylam: Double)

class Cihaz(val anahtar: String, val kaynak: String, val ilkGorulme: Long) {
    var tespit: Tespit? = null
    var rssi = -100
    var sayac = 0
    var sonGorulme = ilkGorulme
    var uretici: String? = null
    var kimlik: String? = null
    var pilotLat: Double? = null
    var pilotLon: Double? = null
    var uyari = false
    val izler = ArrayList<Iz>()

    fun izEkle(zaman: Long, enlem: Double, boylam: Double) {
        val son = izler.lastOrNull()
        if (son != null && zaman - son.zaman < 60_000) return
        izler.add(Iz(zaman, enlem, boylam))
        if (izler.size > 180) izler.removeAt(0)
    }
}
