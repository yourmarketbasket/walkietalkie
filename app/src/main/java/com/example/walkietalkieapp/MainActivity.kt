package com.example.walkietalkieapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.walkietalkieapp.model.Device
import com.example.walkietalkieapp.network.BluetoothService
import com.example.walkietalkieapp.network.WifiService
import com.example.walkietalkieapp.ui.DeviceListScreen
import com.example.walkietalkieapp.ui.theme.WalkieTalkieAppTheme

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothService: BluetoothService
    private lateinit var wifiService: WifiService
    private var permissionsGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResult(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bluetoothService = BluetoothService(this)
        wifiService = WifiService(this)
        requestPermissions()

        setContent {
            WalkieTalkieAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (permissionsGranted) {
                        AppNavigator()
                    } else {
                        PermissionRequestScreen { requestPermissions() }
                    }
                }
            }
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        return permissions.toTypedArray()
    }

    private fun requestPermissions() {
        val permissionsToRequest = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            permissionsGranted = true
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        if (permissions.values.all { it }) {
            permissionsGranted = true
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            permissionsGranted = false
            Toast.makeText(this, "Required permissions denied", Toast.LENGTH_LONG).show()
        }
    }

    @Composable
    fun AppNavigator() {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "device_list") {
            composable("device_list") {
                val bluetoothDevices by bluetoothService.discoveredDevices.collectAsState()
                val wifiDevices by wifiService.discoveredDevices.collectAsState()
                val allDevices = bluetoothDevices + wifiDevices

                DeviceListScreen(
                    devices = allDevices,
                    onDeviceClick = { device ->
                        if (device.type == "Bluetooth") {
                            bluetoothService.pairDevice(device.address)
                        } else {
                            wifiService.connect(device) {
                                navController.navigate("talk")
                            }
                        }
                    },
                    onDiscoverClick = {
                        bluetoothService.startDiscovery()
                        wifiService.startDiscovery()
                    }
                )
            }
            composable("talk") {
                TalkScreen(
                    onTalkPressed = { /* Start talking */ },
                    onTalkReleased = { /* Stop talking */ },
                    bluetoothService = bluetoothService
                )
            }
            composable("incoming_call") {
                IncomingCallScreen(
                    onAccept = { /* Handle accept */ },
                    onReject = { /* Handle reject */ }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bluetoothService.registerReceiver()
    }

    override fun onPause() {
        super.onPause()
        bluetoothService.unregisterReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothService.closeConnection()
    }
}

@Composable
fun TalkScreen(
    onTalkPressed: () -> Unit,
    onTalkReleased: () -> Unit,
    bluetoothService: BluetoothService
) {
    var isTalking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                if (isTalking) {
                    onTalkReleased()
                } else {
                    onTalkPressed()
                }
                isTalking = !isTalking
            }
        ) {
            Text(if (isTalking) "Stop" else "Talk")
        }
    }

    bluetoothService.receiveData { data ->
        // Play received audio data
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Please grant all required permissions to use the app.",
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