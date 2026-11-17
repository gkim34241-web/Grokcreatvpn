package com.example.vpnclient

import android.os.Bundle
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import android.content.SharedPreferences

class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("vpn_settings", MODE_PRIVATE)

        val autoConnectSwitch = findViewById<Switch>(R.id.autoConnectSwitch)
        val darkModeSwitch = findViewById<Switch>(R.id.darkModeSwitch)

        autoConnectSwitch.isChecked = prefs.getBoolean("auto_connect", false)
        darkModeSwitch.isChecked = prefs.getBoolean("dark_mode", false)

        autoConnectSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_connect", isChecked).apply()
        }

        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
        }
    }
}
