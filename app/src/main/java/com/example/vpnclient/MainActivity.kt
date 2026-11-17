package com.example.vpnclient

import android.content.Context
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.concurrent.thread

data class VpnConfig(val name: String, val host: String, var latency: Long = -1, var selected: Boolean = false)

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
    private var selectedIndex = -1

    private val prefs by lazy { getSharedPreferences("vpn_configs", Context.MODE_PRIVATE) }

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

        adapter = ConfigAdapter(configs) { index ->
            selectServer(index)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadConfigs()

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

    private fun selectServer(index: Int) {
        if (index < 0 || index >= configs.size) return
        selectedIndex = index
        configs.forEachIndexed { i, c -> c.selected = i == index }
        adapter.notifyDataSetChanged()
        saveConfigs()
        Toast.makeText(this, "Selected: ${configs[index].name}", Toast.LENGTH_SHORT).show()
    }

    private fun loadConfigs() {
        val json = prefs.getString("configs", null)
        if (json != null) {
            try {
                val arr = JSONArray(json)
                configs.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    configs.add(
                        VpnConfig(
                            obj.getString("name"),
                            obj.getString("host"),
                            obj.optLong("latency", -1),
                            obj.optBoolean("selected", false)
                        )
                    )
                    if (obj.optBoolean("selected", false)) selectedIndex = i
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        if (configs.isEmpty()) {
            configs.add(VpnConfig("Cloudflare", "1.1.1.1"))
            configs.add(VpnConfig("Google DNS", "8.8.8.8"))
            configs.add(VpnConfig("Quad9", "9.9.9.9"))
            saveConfigs()
        }
        adapter.notifyDataSetChanged()
    }

    private fun saveConfigs() {
        val arr = JSONArray()
        configs.forEach {
            arr.put(JSONObject().apply {
                put("name", it.name)
                put("host", it.host)
                put("latency", it.latency)
                put("selected", it.selected)
            })
        }
        prefs.edit().putString("configs", arr.toString()).apply()
    }

    private fun showImportDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "https://example.com/servers.txt"
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Import Subscription")
            .setMessage("Link to text file (name|host or just host, one per line)")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val urlStr = input.text.toString().trim()
                if (urlStr.isNotEmpty()) importFromUrl(urlStr)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importFromUrl(urlStr: String) {
        Toast.makeText(this, "Importing...", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val url = URL(urlStr)
                val text = url.readText()
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
                        selectedIndex = -1
                        adapter.notifyDataSetChanged()
                        saveConfigs()
                        Toast.makeText(this, "Imported ${newConfigs.size} servers", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "No servers found", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(this, "Testing ping...", Toast.LENGTH_SHORT).show()

        thread {
            configs.forEach { config ->
                config.latency = measureLatency(config.host)
            }
            configs.sortBy { if (it.latency < 0) Long.MAX_VALUE else it.latency }

            runOnUiThread {
                adapter.notifyDataSetChanged()
                testButton.isEnabled = true
                saveConfigs()
                Toast.makeText(this, "Sorted by lowest ping", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun measureLatency(host: String): Long {
        return try {
            val cleanHost = host.removePrefix("https://").removePrefix("http://").split("/").first().split(":").first()
            val port = if (host.contains(":")) host.split(":").last().toIntOrNull() ?: 443 else 443
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(cleanHost, port), 3000)
            }
            System.currentTimeMillis() - start
        } catch (e: Exception) {
            -1
        }
    }

    private fun showAddDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_config, null)
        val nameInput = dialogView.findViewById<android.widget.EditText>(R.id.nameInput)
        val hostInput = dialogView.findViewById<android.widget.EditText>(R.id.hostInput)

        AlertDialog.Builder(this)
            .setTitle("Add Server")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().ifBlank { "New Server" }
                val host = hostInput.text.toString().trim()
                if (host.isNotEmpty()) {
                    configs.add(VpnConfig(name, host))
                    adapter.notifyItemInserted(configs.size - 1)
                    saveConfigs()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startVpn() {
        if (selectedIndex < 0) {
            Toast.makeText(this, "Select a server first", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 1)
        } else {
            val serviceIntent = Intent(this, VpnServiceImpl::class.java).apply {
                putExtra("host", configs[selectedIndex].host)
                putExtra("name", configs[selectedIndex].name)
            }
            startService(serviceIntent)
            running = true
            updateUi()
        }
    }

    private fun stopVpn() {
        stopService(Intent(this, VpnServiceImpl::class.java))
        running = false
        updateUi()
        Toast.makeText(this, "VPN stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateUi() {
        startButton.text = if (running) "Stop VPN" else "Start VPN"
        statusText.text = if (running) {
            if (selectedIndex >= 0) "Connected to ${configs[selectedIndex].name}" else "Connected"
        } else "Disconnected"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            val serviceIntent = Intent(this, VpnServiceImpl::class.java).apply {
                putExtra("host", configs[selectedIndex].host)
                putExtra("name", configs[selectedIndex].name)
            }
            startService(serviceIntent)
            running = true
            updateUi()
        }
    }
}
