package com.example.walkietalkieapp.composables

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TalkScreen(
    onTalkPressed: () -> Unit,
    onTalkReleased: () -> Unit,
    onDisconnect: () -> Unit,
    onSwitchCommunication: () -> Unit,
    isBluetooth: Boolean
) {
    var isTalking by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    if (isPressed) {
        if (!isTalking) {
            isTalking = true
            onTalkPressed()
        }
    } else if (isTalking) {
        isTalking = false
        onTalkReleased()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Connected via ${if (isBluetooth) "Bluetooth" else "Wi-Fi"}",
            modifier = Modifier.padding(16.dp)
        )
        Button(
            onClick = { /* No-op, handled by interactionSource */ },
            interactionSource = interactionSource,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(if (isTalking) "Talking..." else "Hold to Talk")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Button(onClick = onDisconnect) {
                Text(text = "Disconnect")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = onSwitchCommunication) {
                Text(text = "Switch to ${if (isBluetooth) "Wi-Fi" else "Bluetooth"}")
            }
        }
    }
}
