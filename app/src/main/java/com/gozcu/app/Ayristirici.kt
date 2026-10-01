package com.gozcu.app

fun reklamiAyir(bayt: ByteArray): List<Pair<Int, ByteArray>> {
    val sonuc = mutableListOf<Pair<Int, ByteArray>>()
    var i = 0

    while (i < bayt.size) {
        val uzunluk = bayt[i].toInt() and 0xFF
        if (uzunluk == 0 || i + uzunluk >= bayt.size) break

        val tip = bayt[i + 1].toInt() and 0xFF
        val veri = bayt.copyOfRange(i + 2, i + 1 + uzunluk)

        sonuc.add(tip to veri)
        i += uzunluk + 1
    }
    return sonuc
}

fun tipAdi(tip: Int): String = when (tip) {
    0x01 -> "Bayraklar"
    0x02, 0x03 -> "16-bit servis UUID"
    0x06, 0x07 -> "128-bit servis UUID"
    0x08 -> "Kısa ad"
    0x09 -> "Tam ad"
    0x0A -> "TX gücü"
    0x16 -> "Servis verisi"
    0xFF -> "Üretici verisi"
    else -> "Tip 0x%02X".format(tip)
}
