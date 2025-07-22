package com.example.walkietalkieapp.states

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.walkietalkieapp.model.Device

@Composable
fun rememberMainState() = remember {
    MainState()
}

class MainState {
    var isDiscovering = mutableStateOf(false)
    var discoveredDevices = mutableStateOf<List<Device>>(emptyList())
    var connectionRequest = mutableStateOf<Device?>(null)
    var connectedDevice = mutableStateOf<Device?>(null)
    var isRecording = mutableStateOf(false)
}
