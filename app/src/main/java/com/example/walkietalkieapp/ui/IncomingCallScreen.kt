package com.example.walkietalkieapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun IncomingCallScreen(onAccept: () -> Unit, onReject: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Incoming Call")
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(onClick = onAccept) {
                Text("Accept")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = onReject) {
                Text("Reject")
            }
        }
    }
}
