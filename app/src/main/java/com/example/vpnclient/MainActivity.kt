package com.example.vpnclient

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var startButton: Button
    private lateinit var statusText: TextView
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startButton = findViewById(R.id.startButton)
        statusText = findViewById(R.id.statusText)

        startButton.setOnClickListener {
            if (!running) startVpn() else stopVpn()
        }
        updateUi()
    }

    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 1)
        } else {
            startService(Intent(this, VpnServiceImpl::class.java))
            running = true
            updateUi()
        }
    }

    private fun stopVpn() {
        stopService(Intent(this, VpnServiceImpl::class.java))
        running = false
        updateUi()
    }

    private fun updateUi() {
        startButton.text = if (running) "Stop VPN" else "Start VPN"
        statusText.text = if (running) "Status: Connected" else "Status: Disconnected"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            startService(Intent(this, VpnServiceImpl::class.java))
            running = true
            updateUi()
        }
    }
}
