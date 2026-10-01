package com.gozcu.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

@SuppressLint("MissingPermission")
class Motor(private val ctx: Context, private val degisti: () -> Unit) {

    val cihazlar = LinkedHashMap<String, Cihaz>()
    private val digerleri = HashMap<String, Long>()
    val digerSayisi: Int get() = digerleri.size
    var hata: String? = null

    private val yoneltici = Handler(Looper.getMainLooper())
    private val wifi = ctx.applicationContext.getSystemService(WifiManager::class.java)
    private val konumYoneticisi = ctx.getSystemService(LocationManager::class.java)

    private var tarayici: BluetoothLeScanner? = null
    private var konum: Location? = null
    private var calisiyor = false
    private var wifiKayitli = false
    private var tik = 0

    private val bleCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, sonuc: ScanResult) {
            isle(sonuc)
        }

        override fun onScanFailed(errorCode: Int) {
            hata = "Tarama başlamadı (kod $errorCode)"
        }
    }

    private val wifiAlici = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            wifiSonuclari()
        }
    }

    private val konumDinleyici = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            konum = loc
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private val dongu = object : Runnable {
        override fun run() {
            if (!calisiyor) return
            tik++
            if (tik % 60 == 1) {
                try {
                    wifi?.startScan()
                } catch (e: Exception) {
                }
            }
            temizle()
            degisti()
            yoneltici.postDelayed(this, 500)
        }
    }

    fun konumAcik(): Boolean {
        val lm = konumYoneticisi ?: return false
        return try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    fun baslat(): String? {
        val adaptor = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        if (adaptor == null || !adaptor.isEnabled) return "Bluetooth kapalı"
        if (calisiyor) return null

        hata = null
        val ayar = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setLegacy(false)
            .build()
        tarayici = adaptor.bluetoothLeScanner
        tarayici?.startScan(null, ayar, bleCb)

        konumuBaslat()

        if (!wifiKayitli) {
            ctx.registerReceiver(wifiAlici, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
            wifiKayitli = true
        }

        calisiyor = true
        tik = 0
        yoneltici.post(dongu)
        return null
    }

    fun durdur() {
        if (!calisiyor) return
        calisiyor = false
        yoneltici.removeCallbacks(dongu)
        try {
            tarayici?.stopScan(bleCb)
        } catch (e: Exception) {
        }
        try {
            konumYoneticisi?.removeUpdates(konumDinleyici)
        } catch (e: Exception) {
        }
        if (wifiKayitli) {
            try {
                ctx.unregisterReceiver(wifiAlici)
            } catch (e: Exception) {
            }
            wifiKayitli = false
        }
    }

    private fun konumuBaslat() {
        val lm = konumYoneticisi ?: return
        for (saglayici in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (lm.isProviderEnabled(saglayici)) {
                    lm.requestLocationUpdates(saglayici, 5000L, 10f, konumDinleyici)
                    if (konum == null) konum = lm.getLastKnownLocation(saglayici)
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun isle(sonuc: ScanResult) {
        val kayit = sonuc.scanRecord ?: return
        val adres = sonuc.device.address
        val simdi = System.currentTimeMillis()

        val tespit = Imzalar.tani(kayit)
        if (tespit == null) {
            digerleri[adres] = simdi
            return
        }

        val c = cihazlar.getOrPut(adres) { Cihaz(adres, "BLE", simdi) }
        c.tespit = tespit
        c.rssi = if (c.sayac == 0) sonuc.rssi else (c.rssi * 7 + sonuc.rssi * 3) / 10
        c.sayac++
        c.sonGorulme = simdi
        c.uretici = Imzalar.uretici(kayit)

        if (tespit.tur == Tur.DRONE) {
            val veri = kayit.getServiceData(Imzalar.U_REMOTE_ID)
            if (veri != null) {
                val b = RemoteId.coz(veri)
                if (b.kimlik != null) c.kimlik = b.kimlik
                if (b.pilotLat != null && b.pilotLon != null) {
                    c.pilotLat = b.pilotLat
                    c.pilotLon = b.pilotLon
                }
            }
        }

        val k = konum
        if (k != null) c.izEkle(simdi, k.latitude, k.longitude)

        if (tespit.tur == Tur.TAKIPCI) {
            val onceki = c.uyari
            c.uyari = Takip.degerlendir(c)
            if (c.uyari && !onceki) titret()
        }
    }

    private fun wifiSonuclari() {
        val liste = try {
            wifi?.scanResults ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
        val simdi = System.currentTimeMillis()

        for (r in liste) {
            val bssid = r.BSSID ?: continue
            val ssid = (r.SSID ?: "").uppercase()
            val oui = bssid.uppercase().take(8)

            val tespit = Imzalar.wifiTani(oui, ssid)
            if (tespit == null) {
                digerleri[bssid] = simdi
                continue
            }
            val c = cihazlar.getOrPut(bssid) { Cihaz(bssid, "Wi-Fi", simdi) }
            c.tespit = tespit
            c.rssi = r.level
            c.sonGorulme = simdi
            c.kimlik = if (ssid.isNotEmpty()) r.SSID else null
        }
    }

    private fun temizle() {
        val simdi = System.currentTimeMillis()
        digerleri.values.removeAll { simdi - it > 60_000 }
        cihazlar.entries.removeAll {
            val c = it.value
            val sinir = if (c.tespit?.tur == Tur.TAKIPCI) 3 * 3_600_000L else 120_000L
            simdi - c.sonGorulme > sinir
        }
    }

    private fun titret() {
        val v = ctx.getSystemService(Vibrator::class.java) ?: return
        v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
