package com.example.pclanbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class RelayService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_TARGET_IP = "EXTRA_TARGET_IP"
        const val EXTRA_PORT = "EXTRA_PORT"
        const val CHANNEL_ID = "pc_lan_relay_channel"
        const val NOTIF_ID = 1001
    }

    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRelay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val targetIp = intent?.getStringExtra(EXTRA_TARGET_IP) ?: "192.168.212.77"
                val port = intent?.getIntExtra(EXTRA_PORT, 8000) ?: 8000
                startForeground(NOTIF_ID, createNotification(targetIp, port))
                startRelay(targetIp, port)
            }
        }
        return START_STICKY
    }

    private fun startRelay(targetIp: String, port: Int) {
        if (isRunning.getAndSet(true)) return

        pool.execute {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress("0.0.0.0", port))
                serverSocket = ss

                while (isRunning.get()) {
                    val clientSocket = ss.accept()
                    pool.execute { handleClient(clientSocket, targetIp, port) }
                }
            } catch (_: Exception) {
            } finally {
                isRunning.set(false)
            }
        }
    }

    private fun handleClient(clientSocket: Socket, targetIp: String, targetPort: Int) {
        var upstreamSocket: Socket? = null
        try {
            clientSocket.tcpNoDelay = true
            upstreamSocket = Socket()
            upstreamSocket.tcpNoDelay = true
            upstreamSocket.connect(InetSocketAddress(targetIp, targetPort), 3000)

            val cIn = clientSocket.getInputStream()
            val cOut = clientSocket.getOutputStream()
            val uIn = upstreamSocket.getInputStream()
            val uOut = upstreamSocket.getOutputStream()

            pool.execute { pipe(cIn, uOut) }
            pipe(uIn, cOut)
        } catch (_: Exception) {
        } finally {
            try { clientSocket.close() } catch (_: Exception) {}
            try { upstreamSocket?.close() } catch (_: Exception) {}
        }
    }

    private fun pipe(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16384)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (_: Exception) {
        }
    }

    private fun stopRelay() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun createNotification(targetIp: String, port: Int): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PC USB LAN Relay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("PC LAN Relay Active (Port $port)")
            .setContentText("Forwarding -> $targetIp:$port")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .build()
    }

    override fun onDestroy() {
        stopRelay()
        pool.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
