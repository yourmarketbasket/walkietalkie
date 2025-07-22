package com.example.walkietalkieapp.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.walkietalkieapp.model.Device

@Composable
fun ConnectionScreen(
    device: Device,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Connection request from:")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = device.name)
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(onClick = onAccept) {
                Text(text = "Accept")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onReject) {
                Text(text = "Reject")
            }
        }
    }
}
