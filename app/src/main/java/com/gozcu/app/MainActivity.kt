package com.gozcu.app

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ekran = TextView(this)
        ekran.text = "Gözcü çalışıyor"
        ekran.textSize = 24f
        ekran.setPadding(48, 96, 48, 48)

        setContentView(ekran)
    }
}
