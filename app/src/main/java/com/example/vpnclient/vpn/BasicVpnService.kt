package com.example.vpnclient.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.vpnclient.MainActivity
import com.example.vpnclient.R
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Basic VPN Service implementation using Android's VpnService API.
 * This is a simple example that demonstrates packet forwarding.
 * For production use, consider using WireGuard or OpenVPN for better performance.
 */
class BasicVpnService : VpnService() {
    private val TAG = "BasicVpnService"
    private var vpnThread: Thread? = null
    private var running = false
    private var vpnInterface: ParcelFileDescriptor? = null
    
    // Statistics
    private var bytesReceived: Long = 0
    private var bytesSent: Long = 0
    private var startTime: Long = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) {
            Log.w(TAG, "VPN service already running")
            return START_STICKY
        }
        
        running = true
        startTime = System.currentTimeMillis()
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        try {
            // Configure VPN
            val builder = Builder()
            builder.setSession("BasicVPN")
                .addAddress("10.0.0.2", 24) // VPN client IP
                .addDnsServer("8.8.8.8")    // Google DNS
                .addRoute("0.0.0.0", 0)     // Route all traffic
                .setMtu(1500)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }
            
            // Establish VPN interface
            vpnInterface = builder.establish()
            
            if (vpnInterface != null) {
                showNotification()
                startVpnThread(vpnInterface!!)
                Log.i(TAG, "VPN interface established")
            } else {
                Log.e(TAG, "Failed to establish VPN interface")
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN: ${e.message}")
            stopSelf()
        }
    }

    private fun startVpnThread(vpnFileDescriptor: ParcelFileDescriptor) {
        vpnThread = Thread {
            val vpnInput = FileInputStream(vpnFileDescriptor.fileDescriptor)
            val vpnOutput = FileOutputStream(vpnFileDescriptor.fileDescriptor)
            
            val buffer = ByteArray(4096)
            while (running) {
                try {
                    val bytesRead = vpnInput.read(buffer)
                    if (bytesRead > 0) {
                        bytesReceived += bytesRead
                        vpnOutput.write(buffer, 0, bytesRead)
                        bytesSent += bytesRead
                    }
                } catch (e: Exception) {
                    if (running) {
                        Log.e(TAG, "Error in VPN thread: ${e.message}")
                    }
                    break
                }
            }
            
            try {
                vpnInput.close()
                vpnOutput.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing streams: ${e.message}")
            }
        }.apply { start() }
    }

    private fun showNotification() {
        val channelId = "vpn_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VPN Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Basic VPN")
            .setContentText("VPN is running")
            .setSmallIcon(R.drawable.ic_vpn)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        running = false
        try {
            vpnThread?.join(1000)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping VPN thread: ${e.message}")
        }
        
        vpnInterface?.close()
        
        val duration = (System.currentTimeMillis() - startTime) / 1000.0
        val speed = if (duration > 0) (bytesReceived + bytesSent) / duration else 0
        Log.i(TAG, "VPN stopped. Duration: ${duration}s, RX: $bytesReceived, TX: $bytesSent, Speed: $speed B/s")
        
        super.onDestroy()
    }
}
