package com.gozcu.app

import android.location.Location

object Takip {
    private const val MIN_NOKTA = 3
    private const val MIN_MESAFE = 200f
    private const val MIN_SURE = 10 * 60_000L

    fun degerlendir(c: Cihaz): Boolean {
        if (c.tespit?.tur != Tur.TAKIPCI) return false
        val iz = c.izler
        if (iz.size < MIN_NOKTA) return false
        if (iz.last().zaman - iz.first().zaman < MIN_SURE) return false

        val sonuc = FloatArray(1)
        var uzak = 0f
        for (n in iz) {
            Location.distanceBetween(iz.first().enlem, iz.first().boylam, n.enlem, n.boylam, sonuc)
            if (sonuc[0] > uzak) uzak = sonuc[0]
        }
        return uzak >= MIN_MESAFE
    }
}
