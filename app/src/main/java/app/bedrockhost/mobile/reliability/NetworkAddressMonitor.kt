package app.bedrockhost.mobile.reliability

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.Inet4Address

enum class AddressKind { LAN, TAILSCALE_OR_CGNAT, PUBLIC, IGNORED }

data class HostAddress(val ip: String, val kind: AddressKind, val interfaceName: String?)

object AddressClassifier {
    fun classify(ip: String): AddressKind {
        val p = ip.split('.').map { it.toIntOrNull() ?: return AddressKind.IGNORED }
        if (p.size != 4 || p.any { it !in 0..255 }) return AddressKind.IGNORED
        return when {
            p[0] == 0 || p[0] == 127 -> AddressKind.IGNORED
            p[0] == 169 && p[1] == 254 -> AddressKind.IGNORED
            p[0] == 10 -> AddressKind.LAN
            p[0] == 172 && p[1] in 16..31 -> AddressKind.LAN
            p[0] == 192 && p[1] == 168 -> AddressKind.LAN
            p[0] == 100 && p[1] in 64..127 -> AddressKind.TAILSCALE_OR_CGNAT
            else -> AddressKind.PUBLIC
        }
    }
}

/**
 * Tracks the phone's usable IPv4 addresses (Wi-Fi LAN and VPN such as Tailscale) and reports changes,
 * so the shown server address stays correct when the network changes. Callbacks arrive on a system thread.
 */
class NetworkAddressMonitor(context: Context) {
    private val connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    @Suppress("DEPRECATION")
    fun currentAddresses(): List<HostAddress> =
        connectivityManager.allNetworks.flatMap { network ->
            val properties = connectivityManager.getLinkProperties(network) ?: return@flatMap emptyList()
            properties.linkAddresses.mapNotNull { linkAddress ->
                val address = linkAddress.address as? Inet4Address ?: return@mapNotNull null
                val ip = address.hostAddress ?: return@mapNotNull null
                val kind = AddressClassifier.classify(ip)
                if (kind == AddressKind.IGNORED) null else HostAddress(ip, kind, properties.interfaceName)
            }
        }.distinctBy { it.ip }

    fun start(onChanged: (List<HostAddress>) -> Unit) {
        stop()
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onChanged(currentAddresses())
            override fun onLost(network: Network) = onChanged(currentAddresses())
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = onChanged(currentAddresses())
        }
        callback = networkCallback
        // VPN networks (Tailscale) are excluded from requests by default, so ask for them explicitly.
        val request = NetworkRequest.Builder().removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        onChanged(currentAddresses())
    }

    fun stop() {
        callback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        callback = null
    }
}
