package com.example.walkietalkieapp.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.walkietalkieapp.model.Device
import com.example.walkietalkieapp.states.MainState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID

@SuppressLint("MissingPermission")
class MainViewModel(
    private val context: Context,
    private val mainState: MainState
) : ViewModel() {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var audioRecord: AudioRecord? = null
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var udpSocket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null
    private val UDP_PORT = 8888
    private val DISCOVERY_MESSAGE = "WALKIE_TALKIE_DISCOVERY"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        val newDevice = Device(it.name ?: "Unknown", it.address, true)
                        if (mainState.discoveredDevices.value.none { d -> d.address == newDevice.address }) {
                            mainState.discoveredDevices.value =
                                mainState.discoveredDevices.value + newDevice
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    mainState.isDiscovering.value = false
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)
        startServer()
    }

    fun discoverDevices() {
        mainState.isDiscovering.value = true
        mainState.discoveredDevices.value = emptyList()
        discoverBluetoothDevices()
        discoverWifiDevices()
    }

    private fun discoverBluetoothDevices() {
        if (bluetoothAdapter?.isEnabled == true) {
            bluetoothAdapter.startDiscovery()
        }
    }

    private fun discoverWifiDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                udpSocket = DatagramSocket()
                udpSocket?.broadcast = true
                val discoveryPacket = DatagramPacket(
                    DISCOVERY_MESSAGE.toByteArray(),
                    DISCOVERY_MESSAGE.length,
                    InetAddress.getByName("255.255.255.255"),
                    UDP_PORT
                )
                udpSocket?.send(discoveryPacket)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun connectToDevice(device: Device) {
        if (device.isBluetooth) {
            connectToBluetoothDevice(device)
        } else {
            connectToWifiDevice(device)
        }
    }

    private fun connectToBluetoothDevice(device: Device) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
                bluetoothSocket =
                    bluetoothDevice?.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket?.connect()
                mainState.connectedDevice.value = device
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun connectToWifiDevice(device: Device) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                targetAddress = InetAddress.getByName(device.address)
                mainState.connectedDevice.value = device
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun startRecording() {
        mainState.isRecording.value = true
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        audioRecord?.startRecording()

        viewModelScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            while (mainState.isRecording.value) {
                val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (read > 0) {
                    if (mainState.connectedDevice.value?.isBluetooth == true) {
                        bluetoothSocket?.outputStream?.write(buffer, 0, read)
                    } else {
                        targetAddress?.let {
                            udpSocket?.send(DatagramPacket(buffer, read, it, UDP_PORT))
                        }
                    }
                }
            }
        }
    }

    fun stopRecording() {
        mainState.isRecording.value = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun startServer() {
        startBluetoothServer()
        startWifiServer()
    }

    private fun startBluetoothServer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val serverSocket: BluetoothServerSocket? =
                    bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                        "WalkieTalkie",
                        uuid
                    )
                bluetoothSocket = serverSocket?.accept()
                val device = bluetoothSocket?.remoteDevice
                mainState.connectionRequest.value =
                    Device(device?.name ?: "Unknown", device?.address ?: "", true)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun startWifiServer() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                udpSocket = DatagramSocket(UDP_PORT)
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                while (true) {
                    udpSocket?.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    if (message == DISCOVERY_MESSAGE) {
                        val device =
                            Device(packet.address.hostName, packet.address.hostAddress, false)
                        mainState.connectionRequest.value = device
                    } else {
                        // audio data
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun acceptConnection() {
        mainState.connectedDevice.value = mainState.connectionRequest.value
        mainState.connectionRequest.value = null
    }

    fun rejectConnection() {
        mainState.connectionRequest.value = null
    }

    fun disconnect() {
        try {
            bluetoothSocket?.close()
            udpSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        mainState.connectedDevice.value = null
    }

    fun switchCommunication() {
        val currentDevice = mainState.connectedDevice.value
        disconnect()
        if (currentDevice?.isBluetooth == true) {
            discoverWifiDevices()
        } else {
            discoverBluetoothDevices()
        }
    }

    override fun onCleared() {
        super.onCleared()
        context.unregisterReceiver(receiver)
        disconnect()
    }
}
