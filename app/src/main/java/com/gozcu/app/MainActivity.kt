package com.gozcu.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

private const val TAM = ViewGroup.LayoutParams.MATCH_PARENT
private const val ICERIK = ViewGroup.LayoutParams.WRAP_CONTENT

class MainActivity : Activity() {

    private lateinit var motor: Motor
    private lateinit var radar: RadarView
    private lateinit var durum: TextView
    private lateinit var uyariKutusu: TextView
    private lateinit var bos: TextView
    private lateinit var izinDugmesi: Button
    private val adaptor by lazy { CihazAdaptoru(this) }

    private fun gerekliIzinler(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun istenenIzinler(): Array<String> =
        (gerekliIzinler() + Manifest.permission.ACCESS_COARSE_LOCATION).toTypedArray()

    private fun eksikIzinler(): List<String> =
        gerekliIzinler().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Renk.ZEMIN
        window.navigationBarColor = Renk.ZEMIN

        motor = Motor(this) { yenile() }
        setContentView(arayuzuKur())

        if (eksikIzinler().isNotEmpty()) requestPermissions(istenenIzinler(), 1)
    }

    private fun arayuzuKur(): View {
        val kok = LinearLayout(this)
        kok.orientation = LinearLayout.VERTICAL
        kok.setBackgroundColor(Renk.ZEMIN)
        kok.setPadding(dp(16), dp(20), dp(16), dp(8))

        val baslik = TextView(this)
        baslik.text = "Gözcü"
        baslik.textSize = 28f
        baslik.setTextColor(Renk.YAZI)
        baslik.typeface = Typeface.DEFAULT_BOLD
        kok.addView(baslik)

        durum = metin(this, "Başlatılıyor...", 13f, Renk.SOLUK)
        kok.addView(durum)

        uyariKutusu = metin(this, "", 14f, Renk.KIRMIZI, true)
        uyariKutusu.background = yuvarlak(0x33FF5C5C, dpf(12f), Renk.KIRMIZI, dp(1))
        uyariKutusu.setPadding(dp(14), dp(12), dp(14), dp(12))
        uyariKutusu.visibility = View.GONE
        val uyariPar = LinearLayout.LayoutParams(TAM, ICERIK)
        uyariPar.topMargin = dp(12)
        kok.addView(uyariKutusu, uyariPar)

        izinDugmesi = Button(this)
        izinDugmesi.text = "İzinleri ver"
        izinDugmesi.isAllCaps = false
        izinDugmesi.setTextColor(Renk.ZEMIN)
        izinDugmesi.background = yuvarlak(Renk.YESIL, dpf(12f))
        izinDugmesi.visibility = View.GONE
        izinDugmesi.setOnClickListener { requestPermissions(istenenIzinler(), 1) }
        val dugmePar = LinearLayout.LayoutParams(TAM, ICERIK)
        dugmePar.topMargin = dp(12)
        kok.addView(izinDugmesi, dugmePar)

        radar = RadarView(this)
        val radarPar = LinearLayout.LayoutParams(TAM, dp(250))
        radarPar.topMargin = dp(8)
        kok.addView(radar, radarPar)

        bos = metin(this, "Şu an tanınan bir cihaz yok.\nÇevre dinleniyor...", 14f, Renk.SOLUK)
        bos.gravity = Gravity.CENTER
        bos.setPadding(0, dp(24), 0, dp(24))
        kok.addView(bos, LinearLayout.LayoutParams(TAM, ICERIK))

        val liste = ListView(this)
        liste.adapter = adaptor
        liste.divider = ColorDrawable(Color.TRANSPARENT)
        liste.dividerHeight = dp(10)
        liste.setSelector(ColorDrawable(Color.TRANSPARENT))
        liste.overScrollMode = View.OVER_SCROLL_NEVER
        liste.setOnItemClickListener { _, _, sira, _ -> haritadaAc(adaptor.liste[sira]) }
        kok.addView(liste, LinearLayout.LayoutParams(TAM, 0, 1f))

        val alt = metin(this, "Pasif dinleme · veri telefondan çıkmaz", 11f, Renk.SOLUK)
        alt.gravity = Gravity.CENTER
        alt.setPadding(0, dp(8), 0, dp(4))
        kok.addView(alt, LinearLayout.LayoutParams(TAM, ICERIK))

        return kok
    }

    private fun yenile() {
        val sirali = motor.cihazlar.values.sortedWith(
            compareByDescending<Cihaz> { it.uyari }
                .thenByDescending { it.tespit?.tur?.seviye ?: 0 }
                .thenByDescending { it.rssi }
        )
        radar.cihazlar = sirali
        adaptor.liste = sirali
        adaptor.notifyDataSetChanged()
        bos.visibility = if (sirali.isEmpty()) View.VISIBLE else View.GONE

        val uyarili = sirali.count { it.uyari }
        if (uyarili > 0) {
            uyariKutusu.text = "⚠ $uyarili takip cihazı seninle birlikte hareket ediyor olabilir"
            uyariKutusu.visibility = View.VISIBLE
        } else {
            uyariKutusu.visibility = View.GONE
        }

        val hata = motor.hata
        durum.text = if (hata != null) {
            hata
        } else {
            "● Taranıyor · ${motor.digerSayisi} diğer cihaz" +
                if (motor.konumAcik()) "" else " · konum kapalı, iz sürme çalışmaz"
        }
    }

    private fun baslatmayiDene() {
        val eksik = eksikIzinler()
        if (eksik.isNotEmpty()) {
            durum.text = "İzin gerekli"
            izinDugmesi.visibility = View.VISIBLE
            return
        }
        izinDugmesi.visibility = View.GONE
        val sorun = motor.baslat()
        if (sorun != null) durum.text = sorun else yenile()
    }

    private fun haritadaAc(c: Cihaz) {
        val la = c.pilotLat
        val lo = c.pilotLon
        if (la == null || lo == null) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$la,$lo?q=$la,$lo(Pilot)")))
        } catch (e: Exception) {
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val eksik = eksikIzinler()
        if (eksik.isNotEmpty() && eksik.none { shouldShowRequestPermissionRationale(it) }) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
    }

    override fun onResume() {
        super.onResume()
        baslatmayiDene()
    }

    override fun onPause() {
        super.onPause()
        motor.durdur()
    }
}
