package com.example.pclanbridge

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {

    private var serviceRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ipInput = EditText(this).apply {
            hint = "PC USB IP (e.g. 192.168.212.77)"
            setText("192.168.212.77")
        }

        val portInput = EditText(this).apply {
            hint = "Port (e.g. 8000)"
            setText("8000")
        }

        val infoText = TextView(this).apply {
            setPadding(0, 24, 0, 24)
            text = buildDeviceInfo(8000)
        }

        val toggleBtn = Button(this).apply {
            text = "Start Relay"
            setOnClickListener {
                val targetIp = ipInput.text.toString().trim()
                val port = portInput.text.toString().trim().toIntOrNull() ?: 8000
                infoText.text = buildDeviceInfo(port)

                if (!serviceRunning) {
                    val intent = Intent(this@MainActivity, RelayService::class.java).apply {
                        action = RelayService.ACTION_START
                        putExtra(RelayService.EXTRA_TARGET_IP, targetIp)
                        putExtra(RelayService.EXTRA_PORT, port)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    serviceRunning = true
                    text = "Stop Relay"
                } else {
                    val intent = Intent(this@MainActivity, RelayService::class.java).apply {
                        action = RelayService.ACTION_STOP
                    }
                    startService(intent)
                    serviceRunning = false
                    text = "Start Relay"
                }
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
            addView(ipInput)
            addView(portInput)
            addView(toggleBtn)
            addView(infoText)
        }

        setContentView(root)
    }

    private fun buildDeviceInfo(port: Int): String {
        val sb = StringBuilder("Tab / Device IPs:\n")
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (!ni.isUp || ni.isLoopback) continue
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        sb.append("• ${ni.name}: http://${addr.hostAddress}:$port\n")
                    }
                }
            }
        } catch (_: Exception) {}
        return sb.toString()
    }
}
