package com.example.walkietalkieapp.network

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.walkietalkieapp.model.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class BluetoothService(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val TAG = "BluetoothService"
    private var isReceiverRegistered = false

    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    companion object {
        const val REQUEST_ENABLE_BT = 1
        const val REQUEST_BLUETOOTH_PERMISSIONS = 2
    }

    private fun checkPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun startDiscovery(activity: android.app.Activity) {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported on this device")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(context, "Turning on Bluetooth...", Toast.LENGTH_SHORT).show()
            if (checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            } else {
                Toast.makeText(context, "Bluetooth permission required", Toast.LENGTH_SHORT).show()
            }
            return
        }

        _discoveredDevices.value = emptyList()

        if (checkPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            activity.startActivity(discoverableIntent)
        } else {
            Toast.makeText(context, "Bluetooth advertise permission required", Toast.LENGTH_SHORT).show()
        }

        registerReceiver()

        if (checkPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            val started = bluetoothAdapter.startDiscovery()
            if (started) {
                Log.d(TAG, "Bluetooth discovery started")
            } else {
                Log.e(TAG, "Failed to start Bluetooth discovery")
            }
        } else {
            Toast.makeText(context, "Bluetooth scan permission required", Toast.LENGTH_SHORT).show()
        }
    }
    fun stopDiscovery() {
        if (!checkPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted")
            return
        }
        bluetoothAdapter?.cancelDiscovery()
        Log.d(TAG, "Bluetooth discovery stopped")
        unregisterReceiver()
    }

    fun pairDevice(deviceAddress: String) {
        if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
            return
        }
        try {
            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            device?.createBond()
            Log.d(TAG, "Initiated pairing with device: $deviceAddress")
        } catch (e: Exception) {
            Log.e(TAG, "Error pairing device: ${e.message}")
        }
    }

    fun connect(device: Device, onConnected: () -> Unit) {
        if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
                bluetoothSocket = bluetoothDevice?.createRfcommSocketToServiceRecord(uuid)
                bluetoothAdapter?.cancelDiscovery() // Cancel discovery before connecting
                bluetoothSocket?.connect()
                _connectionState.value = ConnectionState.Connected
                Log.d(TAG, "Connected to device: ${device.address}")
                withContext(Dispatchers.Main) {
                    onConnected()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Connection failed: ${e.message}")
                _connectionState.value = ConnectionState.Error
            }
        }
    }

    fun acceptConnection(onConnected: () -> Unit) {
        if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("WalkieTalkie", uuid)
                bluetoothSocket = serverSocket?.accept()
                _connectionState.value = ConnectionState.Connected
                Log.d(TAG, "Accepted connection")
                withContext(Dispatchers.Main) {
                    onConnected()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Accept connection failed: ${e.message}")
                _connectionState.value = ConnectionState.Error
            }
        }
    }

    fun sendData(data: ByteArray) {
        try {
            bluetoothSocket?.outputStream?.write(data)
            Log.d(TAG, "Sent data: ${data.size} bytes")
        } catch (e: IOException) {
            Log.e(TAG, "Error sending data: ${e.message}")
            _connectionState.value = ConnectionState.Error
        }
    }

    fun receiveData(onDataReceived: (ByteArray) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            while (true) {
                try {
                    val bytes = bluetoothSocket?.inputStream?.read(buffer) ?: 0
                    if (bytes > 0) {
                        Log.d(TAG, "Received data: $bytes bytes")
                        onDataReceived(buffer.copyOf(bytes))
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error receiving data: ${e.message}")
                    _connectionState.value = ConnectionState.Error
                    break
                }
            }
        }
    }

    fun closeConnection() {
        try {
            bluetoothSocket?.close()
            serverSocket?.close()
            _connectionState.value = ConnectionState.Disconnected
            Log.d(TAG, "Connection closed")
        } catch (e: IOException) {
            Log.e(TAG, "Error closing connection: ${e.message}")
            _connectionState.value = ConnectionState.Error
        }
    }

    fun registerReceiver() {
        if (isReceiverRegistered) {
            Log.d(TAG, "Receiver already registered")
            return
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)
        isReceiverRegistered = true
        Log.d(TAG, "Broadcast receiver registered")
    }

    fun unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
                isReceiverRegistered = false
                Log.d(TAG, "Broadcast receiver unregistered")
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Receiver not registered: ${e.message}")
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
                            return
                        }
                        val deviceName = device.name ?: "Unknown Device"
                        val deviceHardwareAddress = device.address
                        if (deviceHardwareAddress != null) {
                            val newDevice = Device(deviceName, deviceHardwareAddress, "Bluetooth")
                            if (!_discoveredDevices.value.any { it.address == newDevice.address }) {
                                _discoveredDevices.value = _discoveredDevices.value + newDevice
                                Log.d(TAG, "Device found: $deviceName ($deviceHardwareAddress)")
                            }
                        }
                    } else {
                        Log.w(TAG, "Device found but null in ACTION_FOUND")
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    if (device != null && bondState == BluetoothDevice.BOND_BONDED) {
                        Log.d(TAG, "Device paired: ${device.address}")
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d(TAG, "Bluetooth discovery started")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Bluetooth discovery finished, devices found: ${_discoveredDevices.value.size}")
                    unregisterReceiver()
                }
            }
        }
    }
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connected : ConnectionState()
    object Error : ConnectionState()
}