package com.example.walkietalkieapp.network

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.example.walkietalkieapp.model.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*

class BluetoothService(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    fun startDiscovery(activity: android.app.Activity) {
        if (bluetoothAdapter == null) {
            android.widget.Toast.makeText(context, "Bluetooth not supported", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            activity.startActivityForResult(enableBtIntent, 1)
        }

        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        activity.startActivity(discoverableIntent)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bluetoothAdapter.startDiscovery()
        android.widget.Toast.makeText(context, "Bluetooth discovery started", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun stopDiscovery() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            return
        }
        bluetoothAdapter?.cancelDiscovery()
    }

    fun pairDevice(deviceAddress: String) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        device?.createBond()
    }

    fun connect(device: Device, onConnected: () -> Unit) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
                bluetoothSocket = bluetoothDevice?.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket?.connect()
                withContext(Dispatchers.Main) {
                    onConnected()
                }
            } catch (e: IOException) {
                // Handle connection error
            }
        }
    }

    fun acceptConnection(onConnected: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@launch
                }
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("WalkieTalkie", uuid)
                bluetoothSocket = serverSocket?.accept()
                withContext(Dispatchers.Main) {
                    onConnected()
                }
            } catch (e: IOException) {
                // Handle error
            }
        }
    }

    fun sendData(data: ByteArray) {
        try {
            bluetoothSocket?.outputStream?.write(data)
        } catch (e: IOException) {
            // Handle error
        }
    }

    fun receiveData(onDataReceived: (ByteArray) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            while (true) {
                try {
                    val bytes = bluetoothSocket?.inputStream?.read(buffer) ?: 0
                    if (bytes > 0) {
                        onDataReceived(buffer.copyOf(bytes))
                    }
                } catch (e: IOException) {
                    // Handle error
                    break
                }
            }
        }
    }

    fun closeConnection() {
        try {
            bluetoothSocket?.close()
            serverSocket?.close()
        } catch (e: IOException) {
            // Handle error
        }
    }

    fun registerReceiver() {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        context.registerReceiver(receiver, filter)
    }

    fun unregisterReceiver() {
        context.unregisterReceiver(receiver)
    }

    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }
                        val deviceName = device.name
                        val deviceHardwareAddress = device.address
                        if (deviceName != null && deviceHardwareAddress != null) {
                            val newDevice = Device(deviceName, deviceHardwareAddress, "Bluetooth")
                            if (!_discoveredDevices.value.any { it.address == newDevice.address }) {
                                _discoveredDevices.value = _discoveredDevices.value + newDevice
                            }
                        }
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device?.bondState == BluetoothDevice.BOND_BONDED) {
                        // Successfully paired
                    }
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
