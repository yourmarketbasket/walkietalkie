package com.example.walkietalkieapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.walkietalkieapp.composables.ConnectionScreen
import com.example.walkietalkieapp.composables.DeviceListScreen
import com.example.walkietalkieapp.composables.TalkScreen
import com.example.walkietalkieapp.states.rememberMainState
import com.example.walkietalkieapp.ui.theme.WalkieTalkieAppTheme
import com.example.walkietalkieapp.utils.MainViewModel
import com.example.walkietalkieapp.utils.PermissionHandler

class MainActivity : ComponentActivity() {

    private lateinit var permissionHandler: PermissionHandler
    private lateinit var mainViewModel: MainViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            // All permissions granted
        } else {
            Toast.makeText(this, "Some permissions were denied.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionHandler = PermissionHandler(this, permissionLauncher)
        if (!permissionHandler.arePermissionsGranted()) {
            permissionHandler.requestPermissions()
        }

        setContent {
            WalkieTalkieAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val mainState = rememberMainState()
                    mainViewModel = MainViewModel(this, mainState)
                    val isDiscovering by mainState.isDiscovering
                    val discoveredDevices by mainState.discoveredDevices
                    val connectionRequest by mainState.connectionRequest
                    val connectedDevice by mainState.connectedDevice

                    when {
                        connectedDevice != null -> {
                            TalkScreen(
                                onTalkPressed = { mainViewModel.startRecording() },
                                onTalkReleased = { mainViewModel.stopRecording() },
                                onDisconnect = { mainViewModel.disconnect() },
                                onSwitchCommunication = { mainViewModel.switchCommunication() },
                                isBluetooth = connectedDevice!!.isBluetooth
                            )
                        }
                        connectionRequest != null -> {
                            ConnectionScreen(
                                device = connectionRequest!!,
                                onAccept = { mainViewModel.acceptConnection() },
                                onReject = { mainViewModel.rejectConnection() }
                            )
                        }
                        else -> {
                            DeviceListScreen(
                                devices = discoveredDevices,
                                onDeviceClick = { mainViewModel.connectToDevice(it) },
                                onDiscoverClick = { mainViewModel.discoverDevices() },
                                isDiscovering = isDiscovering
                            )
                        }
                    }
                }
            }
        }
    }
}