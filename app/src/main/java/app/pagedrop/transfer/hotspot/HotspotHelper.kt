package app.pagedrop.transfer.hotspot

import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for discovering the device's network address
 * for use with the embedded HTTP server.
 */
@Singleton
class HotspotHelper @Inject constructor() {

    companion object {
        private const val TAG = "HotspotHelper"

        // Interfaces commonly used for hotspot
        private val HOTSPOT_INTERFACES = setOf("swlan0", "ap0", "wlan1", "softap0", "wlan0")
        // Interfaces to skip
        private val SKIP_INTERFACES = setOf("dummy", "rmnet", "p2p", "usbnet")
    }

    /**
     * Queries [NetworkInterface] for a non-loopback IPv4 address.
     * Prefers hotspot/WiFi interfaces over others.
     * Returns null if no suitable address is found.
     */
    fun getDeviceIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            var fallbackAddress: String? = null

            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue

                val name = intf.name.lowercase()
                // Skip cellular and other non-relevant interfaces
                if (SKIP_INTERFACES.any { name.startsWith(it) }) continue

                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddress = addr.hostAddress ?: continue
                        Log.d(TAG, "Found IPv4: $hostAddress on ${intf.displayName} (${intf.name})")

                        // Prefer hotspot/WiFi interfaces
                        if (HOTSPOT_INTERFACES.contains(name)) {
                            Log.i(TAG, "Using preferred interface: $name → $hostAddress")
                            return hostAddress
                        }
                        // Keep as fallback
                        if (fallbackAddress == null) {
                            fallbackAddress = hostAddress
                        }
                    }
                }
            }

            if (fallbackAddress != null) {
                Log.i(TAG, "Using fallback address: $fallbackAddress")
            }
            return fallbackAddress
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device IP address", e)
        }
        return null
    }

    /**
     * Returns the full server URL for the given port.
     * Example: `http://192.168.49.1:8080`
     */
    fun getServerUrl(port: Int): String {
        val ip = getDeviceIpAddress() ?: "0.0.0.0"
        return "http://$ip:$port"
    }
}
