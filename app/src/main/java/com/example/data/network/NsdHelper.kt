package com.example.data.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

class NsdHelper(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val tag = "NsdHelper"
    private val serviceType = "_wifishare._tcp"

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _discoveredPeers = MutableStateFlow<List<WifiPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<WifiPeer>> = _discoveredPeers.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    data class WifiPeer(
        val name: String,
        val ip: String,
        val port: Int
    )

    fun registerService(port: Int, deviceName: String) {
        if (_isRegistered.value) {
            unregisterService()
        }

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = deviceName
            this.serviceType = this@NsdHelper.serviceType
            this.port = port
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d(tag, "Service registered: ${info.serviceName}")
                _isRegistered.value = true
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Service registration failed: $errorCode")
                _isRegistered.value = false
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.d(tag, "Service unregistered: ${info.serviceName}")
                _isRegistered.value = false
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Service unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(tag, "Error registering service", e)
        }
    }

    fun unregisterService() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.e(tag, "Error unregistering service", e)
            }
            registrationListener = null
        }
        _isRegistered.value = false
    }

    fun startDiscovery() {
        if (_isDiscovering.value) {
            stopDiscovery()
        }

        _discoveredPeers.value = emptyList()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                Log.e(tag, "Discovery start failed: $errorCode")
                _isDiscovering.value = false
            }

            override fun onStopDiscoveryFailed(type: String, errorCode: Int) {
                Log.e(tag, "Discovery stop failed: $errorCode")
                _isDiscovering.value = false
            }

            override fun onDiscoveryStarted(type: String) {
                Log.d(tag, "Discovery started")
                _isDiscovering.value = true
            }

            override fun onDiscoveryStopped(type: String) {
                Log.d(tag, "Discovery stopped")
                _isDiscovering.value = false
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(tag, "Service found: ${serviceInfo.serviceName}")
                // Resolve discovered services (exclude self)
                if (serviceInfo.serviceType == this@NsdHelper.serviceType) {
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(tag, "Service lost: ${serviceInfo.serviceName}")
                removePeerByName(serviceInfo.serviceName)
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(tag, "Error starting discovery", e)
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(tag, "Error stopping discovery", e)
            }
            discoveryListener = null
        }
        _isDiscovering.value = false
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        // Resolve listener for this instance call
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Resolve failed for ${info.serviceName}: error $errorCode")
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                Log.d(tag, "Service resolved: ${info.serviceName}")
                val host: InetAddress = info.host
                val ip = host.hostAddress ?: ""
                val port = info.port
                val name = info.serviceName

                if (ip.isNotEmpty()) {
                    addPeer(WifiPeer(name, ip, port))
                }
            }
        }

        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(tag, "Error resolving service", e)
        }
    }

    private fun addPeer(peer: WifiPeer) = synchronized(this) {
        val currentList = _discoveredPeers.value.toMutableList()
        currentList.removeAll { it.name == peer.name }
        currentList.add(peer)
        _discoveredPeers.value = currentList
    }

    private fun removePeerByName(name: String) = synchronized(this) {
        val currentList = _discoveredPeers.value.toMutableList()
        currentList.removeAll { it.name == name }
        _discoveredPeers.value = currentList
    }
}
