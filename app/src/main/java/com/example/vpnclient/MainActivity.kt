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
import kotlin.concurrent.thread

data class VpnConfig(val name: String, val host: String, var latency: Long = -1)

class MainActivity : AppCompatActivity() {
    private lateinit var startButton: Button
    private lateinit var statusText: TextView
    private lateinit var testButton: Button
    private lateinit var addButton: Button
    private lateinit var importButton: Button
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
        importButton = findViewById(R.id.importButton)
        settingsButton = findViewById(R.id.settingsButton)
        recyclerView = findViewById(R.id.recyclerView)

        adapter = ConfigAdapter(configs)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

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
        importButton.setOnClickListener { showImportDialog() }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        updateUi()
    }

    private fun showImportDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "https://example.com/servers.txt"
            setPadding(32, 32, 32, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Import from URL")
            .setMessage("Enter a link to a text file with servers (one per line: name|host or just host)")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val urlStr = input.text.toString().trim()
                if (urlStr.isNotEmpty()) {
                    importFromUrl(urlStr)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importFromUrl(urlStr: String) {
        Toast.makeText(this, "Importing...", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val newConfigs = mutableListOf<VpnConfig>()
                text.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                    val parts = trimmed.split("|", limit = 2)
                    if (parts.size == 2) {
                        newConfigs.add(VpnConfig(parts[0].trim(), parts[1].trim()))
                    } else {
                        newConfigs.add(VpnConfig(trimmed, trimmed))
                    }
                }

                runOnUiThread {
                    if (newConfigs.isNotEmpty()) {
                        configs.clear()
                        configs.addAll(newConfigs)
                        adapter.notifyDataSetChanged()
                        Toast.makeText(this, "Imported ${newConfigs.size} servers", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "No valid servers found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
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
                Toast.makeText(this, "Sorted by lowest ping", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun measureLatency(host: String): Long {
        return try {
            val url = if (host.startsWith("http")) URL(host) else URL("https://$host")
            val start = System.currentTimeMillis()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
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
        statusText.text = if (running) "Connected" else "Disconnected"
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
