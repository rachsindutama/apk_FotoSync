package com.example.server

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
    /**
     * Retrieves the device's IPv4 address on Wi-Fi or Hotspot interface.
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // Priority to common wifi and hotspot interface names
            val prioritized = interfaces.sortedByDescending { ni ->
                val name = ni.name.lowercase()
                when {
                    name.contains("wlan") -> 3
                    name.contains("ap") || name.contains("softap") -> 2
                    name.contains("eth") -> 1
                    else -> 0
                }
            }

            for (networkInterface in prioritized) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val hostAddress = address.hostAddress ?: ""
                        if (hostAddress.isNotEmpty() && !hostAddress.startsWith("127.")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }
}
