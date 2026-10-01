package com.gozcu.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

@SuppressLint("MissingPermission")
class MainActivity : Activity() {

    private lateinit var ekran: TextView
    private var tarayici: BluetoothLeScanner? = null
    private val gorulenler = LinkedHashMap<String, String>()

    private val tarama = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, sonuc: ScanResult) {
            val adres = sonuc.device.address
            val ham = sonuc.scanRecord?.bytes
                ?.joinToString(" ") { "%02X".format(it) } ?: "-"

            gorulenler[adres] = "$adres  ${sonuc.rssi} dBm\n$ham"

            ekran.text = "Görülen cihaz: ${gorulenler.size}\n\n" +
                gorulenler.values.joinToString("\n\n")
        }
    }

    private fun gerekenIzinler(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun izinVarMi(): Boolean =
        gerekenIzinler().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun taramayiBaslat() {
        val adaptor = getSystemService(BluetoothManager::class.java).adapter
        if (adaptor == null || !adaptor.isEnabled) {
            ekran.text = "Bluetooth kapalı, açıp tekrar gir"
            return
        }
        tarayici = adaptor.bluetoothLeScanner
        tarayici?.startScan(tarama)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ekran = TextView(this)
        ekran.textSize = 12f
        ekran.setPadding(32, 64, 32, 32)

        val kaydirma = ScrollView(this)
        kaydirma.addView(ekran)
        setContentView(kaydirma)

        if (!izinVarMi()) {
            requestPermissions(gerekenIzinler(), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        if (izinVarMi()) {
            taramayiBaslat()
        } else {
            ekran.text = "Tarama için izin gerekli"
        }
    }

    override fun onPause() {
        super.onPause()
        tarayici?.stopScan(tarama)
    }
}
