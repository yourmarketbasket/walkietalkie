package com.example.walkietalkieapp.network

import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import com.example.walkietalkieapp.model.Device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WifiService(private val context: Context) {
    private val wifiP2pManager: WifiP2pManager? by lazy(LazyThreadSafetyMode.NONE) {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    }
    private var channel: WifiP2pManager.Channel? = null

    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices

    init {
        channel = wifiP2pManager?.initialize(context, Looper.getMainLooper(), null)
    }

    fun startDiscovery() {
        wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // Discovery initiated
            }

            override fun onFailure(reasonCode: Int) {
                // Discovery failed
            }
        })
    }

    fun stopDiscovery() {
        wifiP2pManager?.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // Discovery stopped
            }

            override fun onFailure(reason: Int) {
                // Stopping discovery failed
            }
        })
    }

    val peerListListener = WifiP2pManager.PeerListListener { peerList ->
        val refreshedPeers = peerList.deviceList.map {
            Device(it.deviceName, it.deviceAddress, "Wi-Fi")
        }
        _discoveredDevices.value = refreshedPeers
    }

    fun connect(device: Device, onConnected: () -> Unit) {
        val config = android.net.wifi.p2p.WifiP2pConfig()
        config.deviceAddress = device.address
        wifiP2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onConnected()
            }

            override fun onFailure(reason: Int) {
                // Connection failed
            }
        })
    }

    fun createGroup(onGroupCreated: () -> Unit) {
        wifiP2pManager?.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onGroupCreated()
            }

            override fun onFailure(reason: Int) {
                // Group creation failed
            }
        })
    }
}
