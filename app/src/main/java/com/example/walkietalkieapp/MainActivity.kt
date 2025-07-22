package com.example.walkietalkieapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.walkietalkieapp.ui.theme.WalkieTalkieAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = AtomicBoolean(false)
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val discoveredDevices = mutableMapOf<BluetoothDevice, Int>()
    private var udpSocket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null
    private var isWifiConnected = false
    private val UDP_PORT = 8888
    private val DISCOVERY_MESSAGE = "WALKIE_TALKIE_DISCOVERY"
    private var permissionsGranted by mutableStateOf(false)
    private var lastKnownLocation: Location? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            permissionsGranted = true
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            updateLocation()
            // Register receiver after permissions are granted
            registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            })
        } else {
            permissionsGranted = false
            Toast.makeText(this, "Required permissions denied. App functionality limited.", Toast.LENGTH_LONG).show()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val rssi: Int = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    device?.let {
                        if (ActivityCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            discoveredDevices[device] = rssi
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (discoveredDevices.isNotEmpty()) {
                        connectToClosestBluetoothDevice()
                    } else {
                        runOnUiThread {
                            Toast.makeText(context, "No Bluetooth devices found", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions immediately on first launch
        requestPermissions()

        setContent {
            WalkieTalkieAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (permissionsGranted) {
                        WalkieTalkieScreen(
                            onDiscoverClick = { tryConnect() },
                            onTalkPressed = { startRecording() },
                            onTalkReleased = { stopRecording() },
                            status = when {
                                isWifiConnected -> "Wi-Fi Connected"
                                bluetoothSocket?.isConnected == true -> "Bluetooth Connected"
                                else -> "Disconnected"
                            }
                        )
                    } else {
                        PermissionRequestScreen(
                            onRequestPermissions = { requestPermissions() }
                        )
                    }
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest)
        } else {
            permissionsGranted = true
            // Register receiver when permissions are already granted
            registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            })
            updateLocation()
        }
    }

    private fun updateLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (e: SecurityException) {
                Toast.makeText(this, "Location access denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun tryConnect() {
        if (!permissionsGranted) {
            Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            if (isWifiAvailable()) {
                startWifiDiscovery()
            } else {
                startBluetoothDiscovery()
            }
        }
    }

    private fun isWifiAvailable(): Boolean {
        if (!permissionsGranted) return false
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    private fun startWifiDiscovery() {
        if (!permissionsGranted) return
        try {
            udpSocket = DatagramSocket(UDP_PORT).apply {
                broadcast = true
            }
            val discoveryPacket = DatagramPacket(
                DISCOVERY_MESSAGE.toByteArray(),
                DISCOVERY_MESSAGE.length,
                InetAddress.getByName("255.255.255.255"),
                UDP_PORT
            )

            CoroutineScope(Dispatchers.IO).launch {
                udpSocket?.send(discoveryPacket)

                val receiveBuffer = ByteArray(1024)
                val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

                try {
                    udpSocket?.receive(receivePacket)
                    val message = String(receivePacket.data, 0, receivePacket.length)
                    if (message == DISCOVERY_MESSAGE) {
                        targetAddress = receivePacket.address
                        isWifiConnected = true
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Wi-Fi device found", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Wi-Fi discovery failed, trying Bluetooth", Toast.LENGTH_SHORT).show()
                    }
                    startBluetoothDiscovery()
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Wi-Fi setup failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            startBluetoothDiscovery()
        }
    }

    private fun startBluetoothDiscovery() {
        if (!permissionsGranted) {
            runOnUiThread {
                Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            runOnUiThread {
                Toast.makeText(this, "Bluetooth or location permissions not granted", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            discoveredDevices.clear()
            bluetoothAdapter.cancelDiscovery()
            bluetoothAdapter.startDiscovery()
        } else {
            runOnUiThread {
                Toast.makeText(this, "Bluetooth is not enabled or not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun connectToClosestBluetoothDevice() {
        if (!permissionsGranted) return
        if (discoveredDevices.isEmpty()) return

        val closestDevice = discoveredDevices.maxByOrNull { it.value }?.key
        closestDevice?.let { device ->
            try {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    bluetoothAdapter?.cancelDiscovery()
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                    bluetoothSocket?.connect()
                    runOnUiThread {
                        Toast.makeText(this, "Bluetooth connected to closest device", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Toast.makeText(this, "Bluetooth connection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                try {
                    bluetoothSocket?.close()
                } catch (e: IOException) {
                    // Ignore
                }
            }
        }
    }

    private fun startRecording() {
        if (!permissionsGranted) {
            Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isWifiConnected && bluetoothSocket?.isConnected != true) {
            Toast.makeText(this, "No device connected", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            runOnUiThread {
                Toast.makeText(this, "Recording permission not granted", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            audioRecord?.startRecording()
            isRecording.set(true)

            CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(bufferSize)
                while (isRecording.get()) {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (read > 0) {
                        if (isWifiConnected) {
                            targetAddress?.let { addr ->
                                udpSocket?.send(DatagramPacket(buffer, read, addr, UDP_PORT))
                            }
                        } else {
                            bluetoothSocket?.outputStream?.write(buffer, 0, read)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Recording error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopRecording() {
        isRecording.set(false)
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            bluetoothSocket?.close()
            udpSocket?.close()
        } catch (e: IOException) {
            // Ignore
        }
        audioRecord?.release()
        if (permissionsGranted) {
            unregisterReceiver(receiver)
        }
    }
}

@Composable
fun WalkieTalkieScreen(
    onDiscoverClick: () -> Unit,
    onTalkPressed: () -> Unit,
    onTalkReleased: () -> Unit,
    status: String
) {
    var isTalking by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    if (isPressed) {
        if (!isTalking) {
            isTalking = true
            onTalkPressed()
        }
    } else {
        if (isTalking) {
            isTalking = false
            onTalkReleased()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Status: $status",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp)
        )
        Button(
            onClick = onDiscoverClick,
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Discover Devices")
        }
        Button(
            onClick = { /* No-op, handled by interactionSource */ },
            interactionSource = interactionSource,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(if (isTalking) "Talking..." else "Hold to Talk")
        }
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Please grant all required permissions to use this app, including location for nearby device discovery.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp)
        )
        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Request Permissions")
        }
    }
}