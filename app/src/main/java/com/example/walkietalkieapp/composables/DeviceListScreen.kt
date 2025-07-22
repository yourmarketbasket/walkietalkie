package com.example.walkietalkieapp.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.walkietalkieapp.model.Device

@Composable
fun DeviceListScreen(
    devices: List<Device>,
    onDeviceClick: (Device) -> Unit,
    onDiscoverClick: () -> Unit,
    isDiscovering: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onDiscoverClick,
            enabled = !isDiscovering,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(if (isDiscovering) "Discovering..." else "Discover Devices")
        }
        if (isDiscovering) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }
        LazyColumn {
            items(devices) { device ->
                DeviceListItem(device = device, onClick = { onDeviceClick(device) })
            }
        }
    }
}

@Composable
fun DeviceListItem(device: Device, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = device.name)
                Text(text = if (device.isBluetooth) "Bluetooth" else "Wi-Fi")
            }
        }
    }
}
