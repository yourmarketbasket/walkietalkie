package com.example.walkietalkieapp.network

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pConfig.WpsInfo
import android.os.Build
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.walkietalkieapp.model.Device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WifiService(private val context: Context) {
    private val wifiP2pManager: WifiP2pManager? by lazy(LazyThreadSafetyMode.NONE) {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    }
    private var channel: WifiP2pManager.Channel? = null
    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices

    private var permissionCallback: ((Boolean) -> Unit)? = null

    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)) {
                WifiManager.WIFI_STATE_ENABLED -> {
                    initializePeerDiscovery()
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        makeDiscoverableLegacy()
                    } else {
                        makeDiscoverable()
                    }
                }
                WifiManager.WIFI_STATE_DISABLED -> {
                    _discoveredDevices.value = emptyList()
                    Toast.makeText(context, "Wi-Fi is disabled", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    init {
        wifiP2pManager?.let { manager ->
            channel = manager.initialize(context, Looper.getMainLooper(), null)
            channel?.also { ch ->
                manager.requestPeers(ch, peerListListener)
            }
            registerWifiStateReceiver()
        } ?: run {
            Log.e(TAG, "WiFi P2P Manager is not available")
            Toast.makeText(context, "Wi-Fi Direct is not supported", Toast.LENGTH_LONG).show()
        }
    }

    private fun registerWifiStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        }
        context.registerReceiver(wifiStateReceiver, filter)
    }

    private val requiredPermissions: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> arrayOf(
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            else -> arrayOf(
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
            )
        }

    fun checkAndRequestPermissions(activity: FragmentActivity, onPermissionResult: (Boolean) -> Unit) {
        permissionCallback = onPermissionResult
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            onPermissionResult(true)
        } else {
            ActivityCompat.requestPermissions(activity, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            permissionCallback?.invoke(allGranted)
            permissionCallback = null
        }
    }

    fun startDiscovery() {
        if (!wifiP2pManager.isAvailable()) return

        checkAndRequestPermissions(context as FragmentActivity) { granted ->
            if (granted) {
                if (wifiManager.isWifiEnabled) {
                    initializePeerDiscovery()
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        makeDiscoverableLegacy()
                    } else {
                        makeDiscoverable()
                    }
                } else {
                    wifiManager.isWifiEnabled = true
                    Toast.makeText(context, "Enabling Wi-Fi...", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Required permissions not granted", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initializePeerDiscovery() {
        if (!hasRequiredPermissions()) return

        wifiP2pManager?.discoverPeers(channel!!, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Discovery initiated successfully")
                Toast.makeText(context, "Wi-Fi Direct discovery started", Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(reasonCode: Int) {
                Log.e(TAG, "Discovery failed to start with reason code: $reasonCode")
                Toast.makeText(context, "Wi-Fi Direct discovery failed: $reasonCode", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun makeDiscoverable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && wifiP2pManager.isAvailable()) {
            wifiP2pManager?.requestP2pState(channel!!, object : WifiP2pManager.P2pStateListener {
                override fun onP2pStateAvailable(state: Int) {
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        Log.d(TAG, "Device is Wi-Fi Direct capable")
                    } else {
                        Log.w(TAG, "Wi-Fi Direct not available")
                        Toast.makeText(context, "Wi-Fi Direct not available", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }

    private fun makeDiscoverableLegacy() {
        if (wifiP2pManager.isAvailable()) {
            Log.d(TAG, "Using legacy discoverable mode")
            initializePeerDiscovery()
        }
    }

    fun stopDiscovery() {
        if (!wifiP2pManager.isAvailable()) return

        checkAndRequestPermissions(context as FragmentActivity) { granted ->
            if (granted) {
                wifiP2pManager?.stopPeerDiscovery(channel!!, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.d(TAG, "Discovery stopped successfully")
                        _discoveredDevices.value = emptyList()
                    }

                    override fun onFailure(reason: Int) {
                        Log.e(TAG, "Failed to stop discovery: $reason")
                    }
                })
            }
        }
    }

    private val peerListListener = WifiP2pManager.PeerListListener { peerList: WifiP2pDeviceList ->
        val refreshedPeers = peerList.deviceList.map {
            Device(
                name = it.deviceName.takeIf { n -> n.isNotEmpty() } ?: "Unknown Device",
                address = it.deviceAddress,
                type = "Wi-Fi Direct",
                status = when (it.status) {
                    WifiP2pDevice.CONNECTED -> "Connected"
                    WifiP2pDevice.INVITED -> "Invited"
                    WifiP2pDevice.FAILED -> "Failed"
                    WifiP2pDevice.AVAILABLE -> "Available"
                    else -> "Unknown"
                }
            )
        }
        _discoveredDevices.value = refreshedPeers
        Log.d(TAG, "Discovered ${refreshedPeers.size} devices")
    }

    fun connect(device: Device, onConnected: () -> Unit) {
        if (!wifiP2pManager.isAvailable()) return

        checkAndRequestPermissions(context as FragmentActivity) { granted ->
            if (granted) {
                val config = WifiP2pConfig().apply {
                    deviceAddress = device.address
                    wps.setup = WpsInfo.PBC
                }
                if (hasRequiredPermissions()) {
                    wifiP2pManager?.connect(channel!!, config, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            Log.d(TAG, "Connection initiated to ${device.name}")
                            onConnected()
                            Toast.makeText(context, "Connecting to ${device.name}", Toast.LENGTH_SHORT).show()
                        }

                        override fun onFailure(reason: Int) {
                            Log.e(TAG, "Connection failed to ${device.name}: $reason")
                            Toast.makeText(context, "Connection failed to ${device.name}", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            } else {
                Toast.makeText(context, "Required permissions not granted", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun createGroup(onGroupCreated: () -> Unit) {
        if (!wifiP2pManager.isAvailable()) return

        checkAndRequestPermissions(context as FragmentActivity) { granted ->
            if (granted) {
                if (hasRequiredPermissions()) {
                    wifiP2pManager?.createGroup(channel!!, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            Log.d(TAG, "Group created successfully")
                            onGroupCreated()
                            Toast.makeText(context, "Wi-Fi Direct group created", Toast.LENGTH_SHORT).show()
                        }

                        override fun onFailure(reason: Int) {
                            Log.e(TAG, "Group creation failed: $reason")
                            Toast.makeText(context, "Group creation failed: $reason", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            } else {
                Toast.makeText(context, "Required permissions not granted", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun destroy() {
        try {
            stopDiscovery()
            wifiP2pManager?.let { manager ->
                channel?.let { ch ->
                    manager.clearLocalServices(ch, null)
                    manager.clearServiceRequests(ch, null)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        ch.close()
                    }
                }
            }
            context.unregisterReceiver(wifiStateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun WifiP2pManager?.isAvailable(): Boolean {
        return this != null && channel != null
    }

    companion object {
        private const val TAG = "WifiService"
        const val PERMISSION_REQUEST_CODE = 1001
    }
}