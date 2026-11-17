package com.example.vpnclient

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.concurrent.thread

data class VpnConfig(val name: String, val host: String, var latency: Long = -1)

class MainActivity : AppCompatActivity() {
    private lateinit var startButton: Button
    private lateinit var statusText: TextView
    private lateinit var testButton: Button
    private lateinit var addButton: Button
    private lateinit var settingsButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ConfigAdapter

    private val configs = mutableListOf<VpnConfig>()
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startButton = findViewById(R.id.startButton)
        statusText = findViewById(R.id.statusText)
        testButton = findViewById(R.id.testButton)
        addButton = findViewById(R.id.addButton)
        settingsButton = findViewById(R.id.settingsButton)
        recyclerView = findViewById(R.id.recyclerView)

        adapter = ConfigAdapter(configs)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Default configs
        if (configs.isEmpty()) {
            configs.add(VpnConfig("Google DNS", "8.8.8.8"))
            configs.add(VpnConfig("Cloudflare", "1.1.1.1"))
            configs.add(VpnConfig("Example", "example.com"))
        }

        startButton.setOnClickListener {
            if (!running) startVpn() else stopVpn()
        }

        testButton.setOnClickListener { testAll() }
        addButton.setOnClickListener { showAddDialog() }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        updateUi()
    }

    private fun testAll() {
        testButton.isEnabled = false
        Toast.makeText(this, "Testing latency...", Toast.LENGTH_SHORT).show()

        thread {
            configs.forEach { config ->
                config.latency = measureLatency(config.host)
            }
            configs.sortBy { if (it.latency < 0) Long.MAX_VALUE else it.latency }

            runOnUiThread {
                adapter.notifyDataSetChanged()
                testButton.isEnabled = true
                Toast.makeText(this, "Test complete. Sorted by lowest ping.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun measureLatency(host: String): Long {
        return try {
            val url = if (host.startsWith("http")) URL(host) else URL("https://$host")
            val start = System.currentTimeMillis()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "HEAD"
            conn.connect()
            val time = System.currentTimeMillis() - start
            conn.disconnect()
            time
        } catch (e: Exception) {
            -1
        }
    }

    private fun showAddDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_config, null)
        val nameInput = dialogView.findViewById<android.widget.EditText>(R.id.nameInput)
        val hostInput = dialogView.findViewById<android.widget.EditText>(R.id.hostInput)

        AlertDialog.Builder(this)
            .setTitle("Add Config")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().ifBlank { "New Server" }
                val host = hostInput.text.toString().trim()
                if (host.isNotEmpty()) {
                    configs.add(VpnConfig(name, host))
                    adapter.notifyItemInserted(configs.size - 1)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
