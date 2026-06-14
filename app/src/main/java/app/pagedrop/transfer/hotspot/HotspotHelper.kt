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
    }

    /**
     * Queries [NetworkInterface] for a non-loopback IPv4 address.
     * Returns null if no suitable address is found.
     */
    fun getDeviceIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                // Skip loopback and down interfaces
                if (intf.isLoopback || !intf.isUp) continue

                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddress = addr.hostAddress
                        Log.d(TAG, "Found IPv4 address: $hostAddress on ${intf.displayName}")
                        return hostAddress
                    }
                }
            }
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
