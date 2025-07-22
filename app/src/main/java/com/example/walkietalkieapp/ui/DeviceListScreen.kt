package com.example.walkietalkieapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.walkietalkieapp.model.Device

import androidx.compose.material3.CircularProgressIndicator

@Composable
fun DeviceListScreen(
    devices: List<Device>,
    onDeviceClick: (Device) -> Unit,
    onDiscoverClick: () -> Unit,
    isDiscovering: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onDiscoverClick,
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Discover Devices")
        }
        if (isDiscovering) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(devices) { device ->
                DeviceListItem(
                    device = device,
                    onDeviceClick = { onDeviceClick(device) },
                    onPairClick = { onDeviceClick(device) }
                )
            }
        }
    }
}

@Composable
fun DeviceListItem(device: Device, onDeviceClick: (Device) -> Unit, onPairClick: (Device) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onDeviceClick(device) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${device.address} - ${device.type}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(onClick = { onPairClick(device) }) {
                Text("Pair")
            }
        }
    }
}
