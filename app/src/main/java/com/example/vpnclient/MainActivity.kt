package com.example.vpnclient

import android.app.ActivityManager
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
import java.net.URLDecoder
import kotlin.concurrent.thread

data class VpnConfig(
    val name: String,
    val host: String,
    var latency: Long = -1,
    var selected: Boolean = false,
    val protocol: String = "VPN",
    val pingHost: String = "",
    val pingPort: Int = 443,
    val raw: String = ""
)

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

        adapter = ConfigAdapter(configs) { index -> selectServer(index) }
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
    }

    override fun onResume() {
        super.onResume()
        val alive = isVpnServiceRunning()
        if (alive) {
            running = true
            prefs.edit().putBoolean("vpn_running", true).apply()
        } else {
            running = prefs.getBoolean("vpn_running", false)
        }
        updateUi()
    }

    @Suppress("DEPRECATION")
    private fun isVpnServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(40).any {
            it.service.className.endsWith("VpnServiceImpl")
        }
    }

    private fun selectServer(index: Int) {
        if (index < 0 || index >= configs.size) return
        selectedIndex = index
        configs.forEachIndexed { i, c -> c.selected = i == index }
        adapter.notifyDataSetChanged()
        saveConfigs()
        updateUi()
    }

    private fun loadConfigs() {
        val json = prefs.getString("configs", null)
        if (json != null) {
            try {
                val arr = JSONArray(json)
                configs.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val loaded = VpnConfig(
                        name = obj.optString("name", "Server"),
                        host = obj.optString("host", ""),
                        latency = obj.optLong("latency", -1),
                        selected = obj.optBoolean("selected", false),
                        protocol = obj.optString("protocol", "VPN"),
                        pingHost = obj.optString("pingHost", ""),
                        pingPort = obj.optInt("pingPort", 443),
                        raw = obj.optString("raw", "")
                    )
                    val pretty = normalize(loaded)
                    configs.add(pretty)
                    if (pretty.selected) selectedIndex = configs.lastIndex
                }
            } catch (_: Exception) {
            }
        }
        if (configs.isEmpty()) {
            configs.add(VpnConfig("Cloudflare", "1.1.1.1:443", protocol = "DNS", pingHost = "1.1.1.1", pingPort = 443))
            configs.add(VpnConfig("Google DNS", "8.8.8.8:443", protocol = "DNS", pingHost = "8.8.8.8", pingPort = 443))
        }
        saveConfigs()
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
                put("protocol", it.protocol)
                put("pingHost", it.pingHost)
                put("pingPort", it.pingPort)
                put("raw", it.raw)
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
            .setTitle("Import subscription")
            .setMessage("One server per line: vless://, trojan://, vmess:// or name|host")
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
                val text = URL(urlStr).readText()
                val newConfigs = mutableListOf<VpnConfig>()
                text.lines().forEach { line ->
                    parseConfigLine(line)?.let { newConfigs.add(it) }
                }
                runOnUiThread {
                    if (newConfigs.isNotEmpty()) {
                        configs.clear()
                        configs.addAll(newConfigs)
                        selectedIndex = -1
                        adapter.notifyDataSetChanged()
                        saveConfigs()
                        prefs.edit().putString("subscription_url", urlStr).apply()
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
            configs.forEach { it.latency = measureLatency(it) }
            configs.sortBy { if (it.latency < 0) Long.MAX_VALUE else it.latency }
            selectedIndex = configs.indexOfFirst { it.selected }
            runOnUiThread {
                adapter.notifyDataSetChanged()
                testButton.isEnabled = true
                saveConfigs()
                Toast.makeText(this, "Sorted by lowest ping", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun measureLatency(config: VpnConfig): Long {
        val host = config.pingHost.ifBlank { return -1L }
        val port = if (config.pingPort in 1..65535) config.pingPort else 443
        return try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2500)
            }
            System.currentTimeMillis() - start
        } catch (_: Exception) {
            -1L
        }
    }

    private fun showAddDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_config, null)
        val nameInput = dialogView.findViewById<android.widget.EditText>(R.id.nameInput)
        val hostInput = dialogView.findViewById<android.widget.EditText>(R.id.hostInput)
        AlertDialog.Builder(this)
            .setTitle("Add server")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().ifBlank { "New Server" }
                val host = hostInput.text.toString().trim()
                if (host.isNotEmpty()) {
                    val parsed = parseConfigLine(if (host.contains("://")) host else "$name|$host")
                    if (parsed != null) {
                        configs.add(parsed)
                        adapter.notifyItemInserted(configs.size - 1)
                        saveConfigs()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startVpn() {
        if (selectedIndex < 0 || selectedIndex >= configs.size) {
            Toast.makeText(this, "Tap a server first", Toast.LENGTH_SHORT).show()
            return
        }
        val config = configs[selectedIndex]
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            startActivityForResult(prepare, 1)
        } else {
            launchService(config)
        }
    }

    private fun launchService(config: VpnConfig) {
        val serviceIntent = Intent(this, VpnServiceImpl::class.java).apply {
            putExtra("host", config.pingHost.ifBlank { config.host })
            putExtra("name", config.name)
        }
        startService(serviceIntent)
        running = true
        prefs.edit().putBoolean("vpn_running", true).apply()
        updateUi()
    }

    private fun stopVpn() {
        stopService(Intent(this, VpnServiceImpl::class.java))
        running = false
        prefs.edit().putBoolean("vpn_running", false).apply()
        updateUi()
        Toast.makeText(this, "VPN stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateUi() {
        val selectedName = configs.getOrNull(selectedIndex)?.name
        if (running) {
            startButton.text = "Stop VPN"
            statusText.text = if (selectedName != null) "Connected \u2022 $selectedName" else "Connected"
        } else {
            startButton.text = "Start VPN"
            statusText.text = if (selectedName != null) "Selected \u2022 $selectedName" else "Disconnected"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK && selectedIndex in configs.indices) {
            launchService(configs[selectedIndex])
        }
    }
}

private fun parseConfigLine(line: String): VpnConfig? {
    val trimmed = line.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
    if (trimmed.contains("://")) return parseProxyUri(trimmed)
    if (trimmed.contains("|")) {
        val parts = trimmed.split("|", limit = 2)
        val name = parts[0].trim().ifBlank { "Server" }
        val host = parts[1].trim()
        val hp = splitHostPort(host)
        return VpnConfig(name, "${hp.first}:${hp.second}", protocol = "HOST", pingHost = hp.first, pingPort = hp.second, raw = trimmed)
    }
    val hp = splitHostPort(trimmed)
    return VpnConfig(trimmed, "${hp.first}:${hp.second}", protocol = "HOST", pingHost = hp.first, pingPort = hp.second, raw = trimmed)
}

private fun normalize(config: VpnConfig): VpnConfig {
    val source = when {
        config.raw.contains("://") -> config.raw
        config.host.contains("://") -> config.host
        config.name.contains("://") -> config.name
        else -> return config
    }
    val parsed = parseProxyUri(source)
    return parsed.copy(latency = config.latency, selected = config.selected)
}

private fun parseProxyUri(uri: String): VpnConfig {
    val hash = uri.indexOf('#')
    val rawName = if (hash >= 0) uri.substring(hash + 1) else ""
    val decoded = try {
        URLDecoder.decode(rawName, "UTF-8")
    } catch (_: Exception) {
        rawName
    }.trim()
    val body = if (hash >= 0) uri.substring(0, hash) else uri
    val scheme = body.substringBefore("://", "vpn").uppercase()
    val rest = body.substringAfter("://")
    val authority = rest.substringBefore("?").let { beforeQuery ->
        if (beforeQuery.contains("@")) beforeQuery.substringAfterLast("@") else beforeQuery
    }
    val (host, port) = splitHostPort(authority)
    val displayHost = if (host.isNotBlank()) "$host:$port" else "unknown"
    val displayName = decoded.ifBlank { if (host.isNotBlank()) host else "Server" }
    return VpnConfig(
        name = displayName,
        host = displayHost,
        protocol = scheme,
        pingHost = host,
        pingPort = port,
        raw = uri
    )
}

private fun splitHostPort(value: String): Pair<String, Int> {
    val clean = value
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore("/")
        .substringBefore("?")
        .trim()
    if (clean.startsWith("[")) {
        val end = clean.indexOf(']')
        val host = if (end > 1) clean.substring(1, end) else clean
        val port = clean.substringAfter("]:", "443").toIntOrNull() ?: 443
        return host to port
    }
    val colon = clean.lastIndexOf(':')
    if (colon > 0 && clean.substring(colon + 1).all { it.isDigit() }) {
        val host = clean.substring(0, colon)
        val port = clean.substring(colon + 1).toIntOrNull() ?: 443
        return host to port
    }
    return clean to 443
}
