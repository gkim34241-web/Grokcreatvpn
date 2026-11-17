package com.example.vpnclient

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log

class VpnServiceImpl : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var thread: Thread? = null
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        running = true
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        val builder = Builder()
            .setSession("SimpleVPN")
            .addAddress("10.0.0.2", 24)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)
            .setMtu(1500)

        vpnInterface = builder.establish()

        if (vpnInterface == null) {
            stopSelf()
            return
        }

        showNotification()

        thread = Thread {
            try {
                val input = java.io.FileInputStream(vpnInterface!!.fileDescriptor)
                val output = java.io.FileOutputStream(vpnInterface!!.fileDescriptor)
                val buffer = ByteArray(2048)
                while (running) {
                    val len = input.read(buffer)
                    if (len > 0) {
                        output.write(buffer, 0, len)
                    }
                }
            } catch (e: Exception) {
                Log.e("VPN", "Thread error", e)
            }
        }.also { it.start() }
    }

    private fun showNotification() {
        val channelId = "vpn"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("VPN running")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        running = false
        thread?.interrupt()
        vpnInterface?.close()
        super.onDestroy()
    }
}
