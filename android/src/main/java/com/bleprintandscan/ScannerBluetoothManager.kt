package com.bleprintandscan

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class ScannerConnectionInfo(
    val device: BluetoothDevice,
    var socket: BluetoothSocket? = null,
    var inputStream: InputStream? = null,
    var outputStream: OutputStream? = null,
    var isConnected: Boolean = false,
    var isListening: Boolean = false
)

class ScannerBluetoothManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ScannerBluetoothManager"
        const val REQUEST_BLUETOOTH_PERMISSIONS = 2
        
        // SPP UUID for classic Bluetooth
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        
        // Scanner command constants (from natum-android analysis)
        const val READ_FIRMWARE_VERSION = "\$SW#VER"
        const val RESTORE_FACTORY_SETTINGS = "%#IFSNO\$B"
        const val READ_INTERFACE_SETTING = "%#IFSNO\$"
        const val WORK_MODE_NORMAL = "%#NORMD"
        const val WORK_MODE_STORE = "%#INVMD"
        const val BEEP_MUTE = "\$BUZZ#0"
        const val BEEP_HIGH_VOLUME = "\$BUZZ#1"
        const val BEEP_MIDDLE_VOLUME = "\$BUZZ#2"
        const val BEEP_LOW_VOLUME = "\$BUZZ#3"
        const val POWER_OFF = "\$POWER#OFF"
        const val NEVER_SLEEP = "\$RF#ST00"
        const val ENABLE_TIMESTAMP = "%RTCSTAMP#1"
        const val DISABLE_TIMESTAMP = "%RTCSTAMP#0"
    }
    
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val scannerConnections = ConcurrentHashMap<String, ScannerConnectionInfo>()
    private val discoveredDevices = mutableSetOf<BluetoothDevice>()
    
    // Callbacks
    private var onScannerFound: ((List<Map<String, String>>) -> Unit)? = null
    private var dataCallbacks = ConcurrentHashMap<String, (Map<String, String>) -> Unit>()
    private var commandCallbacks = ConcurrentHashMap<String, (Result<String>) -> Unit>()
    
    // Discovery receiver
    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let { 
                        if (hasBluetoothPermission()) {
                            Log.d(TAG, "Discovered device: ${it.name ?: "Unknown"} (${it.address})")
                            discoveredDevices.add(it)
                            notifyScannersFound()
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Discovery finished")
                }
            }
        }
    }
    
    init {
        // Register discovery receiver
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(discoveryReceiver, filter)
    }
    
    // MARK: - Permission Management
    
    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun requestBluetoothPermissions(activity: Activity): Boolean {
        if (hasBluetoothPermission()) {
            return true
        }
        
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        
        ActivityCompat.requestPermissions(activity, permissions, REQUEST_BLUETOOTH_PERMISSIONS)
        return false
    }
    
    // MARK: - Public Interface
    
    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }
    
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }
    
    @SuppressLint("MissingPermission")
    fun startScanning(onScannerFound: (List<Map<String, String>>) -> Unit) {
        if (!hasBluetoothPermission()) {
            Log.e(TAG, "Bluetooth permissions not granted")
            return
        }
        
        this.onScannerFound = onScannerFound
        discoveredDevices.clear()
        
        bluetoothAdapter?.let { adapter ->
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            adapter.startDiscovery()
        }
    }
    
    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (hasBluetoothPermission()) {
            bluetoothAdapter?.cancelDiscovery()
        }
    }
    
    private fun notifyScannersFound() {
        val devices = discoveredDevices.mapNotNull { device ->
            val deviceName = device.name
            
            // Filter out devices with invalid names
            if (!deviceName.isNullOrBlank() && 
                deviceName.trim().isNotEmpty() && 
                !deviceName.startsWith("Unknown") &&
                !deviceName.matches(Regex("^[0-9A-F:]{17}$"))) { // Not just MAC address
                
                mapOf(
                    "id" to device.address,
                    "name" to deviceName.trim()
                )
            } else {
                null
            }
        }
        onScannerFound?.invoke(devices)
    }
    
    suspend fun connectToScanner(deviceId: String): Boolean = suspendCoroutine { continuation ->
        // Check if already connected
        if (scannerConnections[deviceId]?.isConnected == true) {
            continuation.resume(true)
            return@suspendCoroutine
        }
        
        if (!hasBluetoothPermission()) {
            continuation.resumeWithException(Exception("Bluetooth permissions not granted"))
            return@suspendCoroutine
        }
        
        val device = bluetoothAdapter?.getRemoteDevice(deviceId)
        if (device == null) {
            continuation.resumeWithException(Exception("Device not found: $deviceId"))
            return@suspendCoroutine
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Connecting to scanner: ${device.name ?: "Unknown"} ($deviceId)")
                
                @SuppressLint("MissingPermission")
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                
                // Cancel discovery to improve connection performance
                bluetoothAdapter?.cancelDiscovery()
                
                socket.connect()
                
                val connectionInfo = ScannerConnectionInfo(
                    device = device,
                    socket = socket,
                    inputStream = socket.inputStream,
                    outputStream = socket.outputStream,
                    isConnected = true
                )
                
                scannerConnections[deviceId] = connectionInfo
                
                Log.d(TAG, "Successfully connected to scanner: $deviceId")
                continuation.resume(true)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to scanner $deviceId: ${e.message}")
                continuation.resumeWithException(e)
            }
        }
    }
    
    suspend fun disconnect(deviceId: String): Boolean = suspendCoroutine { continuation ->
        val connectionInfo = scannerConnections[deviceId]
        if (connectionInfo == null || !connectionInfo.isConnected) {
            continuation.resume(true)
            return@suspendCoroutine
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                connectionInfo.socket?.close()
                scannerConnections.remove(deviceId)
                dataCallbacks.remove(deviceId)
                commandCallbacks.remove(deviceId)
                
                Log.d(TAG, "Successfully disconnected from scanner: $deviceId")
                continuation.resume(true)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting from scanner $deviceId: ${e.message}")
                continuation.resumeWithException(e)
            }
        }
    }
    
    suspend fun disconnectAllScanners(): Boolean {
        var allSuccess = true
        val deviceIds = scannerConnections.keys.toList()
        
        for (deviceId in deviceIds) {
            try {
                if (!disconnect(deviceId)) {
                    allSuccess = false
                }
            } catch (e: Exception) {
                allSuccess = false
                Log.e(TAG, "Failed to disconnect scanner $deviceId: ${e.message}")
            }
        }
        
        return allSuccess
    }
    
    fun isConnected(deviceId: String): Boolean {
        val connectionInfo = scannerConnections[deviceId] ?: return false
        
        try {
            // For Classic Bluetooth, check if socket is still connected
            val socket = connectionInfo.socket
            if (connectionInfo.isConnected && (socket == null || !socket.isConnected)) {
                Log.d(TAG, "Cleaning up stale scanner connection for device: $deviceId")
                scannerConnections.remove(deviceId)
                dataCallbacks.remove(deviceId)
                return false
            }
            
            return connectionInfo.isConnected && socket?.isConnected == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking scanner connection state for device: $deviceId")
            // Clean up problematic connection
            scannerConnections.remove(deviceId)
            dataCallbacks.remove(deviceId)
            return false
        }
    }
    
    fun getConnectedScanners(): List<Map<String, String>> {
        // Clean up stale connections first
        val staleConnections = mutableListOf<String>()
        
        scannerConnections.forEach { (deviceId, connectionInfo) ->
            try {
                val socket = connectionInfo.socket
                if (connectionInfo.isConnected && (socket == null || !socket.isConnected)) {
                    staleConnections.add(deviceId)
                    Log.d(TAG, "Found stale scanner connection for device: $deviceId")
                }
            } catch (e: Exception) {
                staleConnections.add(deviceId)
                Log.d(TAG, "Error checking scanner connection state for device: $deviceId, removing")
            }
        }
        
        // Remove stale connections
        staleConnections.forEach { deviceId ->
            scannerConnections.remove(deviceId)
            dataCallbacks.remove(deviceId)
        }
        
        return scannerConnections.filter { it.value.isConnected }.mapNotNull { (deviceId, connectionInfo) ->
            try {
                val socket = connectionInfo.socket
                if (socket?.isConnected == true) {
                    val deviceName = connectionInfo.device.name
                    if (!deviceName.isNullOrBlank() && deviceName.trim().isNotEmpty()) {
                        mapOf(
                            "id" to deviceId,
                            "name" to deviceName.trim()
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error validating scanner connection for device: $deviceId")
                null
            }
        }
    }
    
    fun startListening(deviceId: String, onScanResult: (Map<String, String>) -> Unit) {
        val connectionInfo = scannerConnections[deviceId]
        if (connectionInfo == null || !connectionInfo.isConnected) {
            throw Exception("Scanner $deviceId is not connected")
        }
        
        dataCallbacks[deviceId] = onScanResult
        scannerConnections[deviceId]?.isListening = true
        
        // Start listening thread
        CoroutineScope(Dispatchers.IO).launch {
            listenForData(deviceId, connectionInfo)
        }
    }
    
    fun stopListening(deviceId: String) {
        dataCallbacks.remove(deviceId)
        scannerConnections[deviceId]?.isListening = false
    }
    
    private suspend fun listenForData(deviceId: String, connectionInfo: ScannerConnectionInfo) {
        val buffer = ByteArray(1024)
        
        try {
            while (connectionInfo.isListening && connectionInfo.isConnected) {
                val inputStream = connectionInfo.inputStream ?: break
                
                val bytesRead = inputStream.read(buffer)
                if (bytesRead > 0) {
                    val data = buffer.copyOf(bytesRead)
                    processReceivedData(deviceId, data, connectionInfo.device)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading from scanner $deviceId: ${e.message}")
            // Connection lost, clean up
            withContext(Dispatchers.Main) {
                scannerConnections.remove(deviceId)
                dataCallbacks.remove(deviceId)
                commandCallbacks.remove(deviceId)
            }
        }
    }
    
    private fun processReceivedData(deviceId: String, data: ByteArray, device: BluetoothDevice) {
        try {
            // Parse scanner protocol (from natum-android analysis)
            val response = parseResponse(data)
            Log.d(TAG, "Received from scanner $deviceId: $response")
            
            // Check if this is a command response or scan data
            val commandCallback = commandCallbacks[deviceId]
            if (commandCallback != null) {
                commandCallback(Result.success(response))
                commandCallbacks.remove(deviceId)
            } else {
                // This is scan data
                val scanCallback = dataCallbacks[deviceId]
                if (scanCallback != null && response.isNotEmpty()) {
                    val scanResult = mapOf(
                        "data" to response,
                        "timestamp" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date()),
                        "deviceId" to deviceId,
                        "deviceName" to (device.name ?: "Unknown Scanner")
                    )
                    scanCallback(scanResult)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing data from scanner $deviceId: ${e.message}")
        }
    }
    
    private fun parseResponse(data: ByteArray): String {
        // Based on natum-android protocol analysis
        // Scanner responses may come with protocol wrapper: [0x02][length][0x0A][data][checksum][0x03]
        return if (data.size >= 6 && data[0] == 0x02.toByte() && data[data.size - 1] == 0x03.toByte()) {
            // Extract data between protocol wrapper
            val dataStart = 3 // Skip [0x02][length][0x0A]
            val dataEnd = data.size - 3 // Skip [checksum1][checksum2][0x03]
            if (dataEnd > dataStart) {
                String(data.sliceArray(dataStart until dataEnd), Charsets.UTF_8)
            } else {
                String(data, Charsets.UTF_8)
            }
        } else {
            String(data, Charsets.UTF_8)
        }
    }
    
    suspend fun sendCommandWithoutResponse(deviceId: String, command: String): Boolean = suspendCoroutine { continuation ->
        val connectionInfo = scannerConnections[deviceId]
        if (connectionInfo == null || !connectionInfo.isConnected) {
            continuation.resumeWithException(Exception("Scanner $deviceId is not connected"))
            return@suspendCoroutine
        }
        
        val outputStream = connectionInfo.outputStream
        if (outputStream == null) {
            continuation.resumeWithException(Exception("No output stream for scanner $deviceId"))
            return@suspendCoroutine
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Package command with protocol wrapper (from natum-android analysis)
                val commandData = packageCommand(command)
                
                outputStream.write(commandData)
                outputStream.flush()
                
                Log.d(TAG, "Sent command to scanner $deviceId (no response expected): $command")
                continuation.resume(true)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending command to scanner $deviceId: ${e.message}")
                continuation.resumeWithException(e)
            }
        }
    }
    
    suspend fun sendCommand(deviceId: String, command: String): String = suspendCoroutine { continuation ->
        val connectionInfo = scannerConnections[deviceId]
        if (connectionInfo == null || !connectionInfo.isConnected) {
            continuation.resumeWithException(Exception("Scanner $deviceId is not connected"))
            return@suspendCoroutine
        }
        
        val outputStream = connectionInfo.outputStream
        if (outputStream == null) {
            continuation.resumeWithException(Exception("No output stream for scanner $deviceId"))
            return@suspendCoroutine
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Package command with protocol wrapper (from natum-android analysis)
                val commandData = packageCommand(command)
                
                // Store callback for command response
                commandCallbacks[deviceId] = { result ->
                    when {
                        result.isSuccess -> continuation.resume(result.getOrThrow())
                        result.isFailure -> continuation.resumeWithException(result.exceptionOrNull() ?: Exception("Command failed"))
                    }
                }
                
                outputStream.write(commandData)
                outputStream.flush()
                
                Log.d(TAG, "Sent command to scanner $deviceId: $command")
                
                // Set timeout for command response
                CoroutineScope(Dispatchers.IO).launch {
                    kotlinx.coroutines.delay(5000) // 5 second timeout
                    if (commandCallbacks.containsKey(deviceId)) {
                        commandCallbacks.remove(deviceId)
                        continuation.resumeWithException(Exception("Command timeout"))
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending command to scanner $deviceId: ${e.message}")
                commandCallbacks.remove(deviceId)
                continuation.resumeWithException(e)
            }
        }
    }
    
    private fun packageCommand(command: String): ByteArray {
        // Package command with protocol wrapper (based on natum-android ScannerUtil)
        val commandBytes = command.toByteArray(Charsets.UTF_8)
        val data = mutableListOf<Byte>()
        
        // Add command bytes
        data.addAll(commandBytes.toList())
        
        // Create package: [0x02][length][0x0A][data][checksum][0x03]
        val packageData = mutableListOf<Byte>()
        packageData.add(0x02) // Start byte
        packageData.add((data.size + 4).toByte()) // Length
        packageData.add(0x0A) // Command indicator
        packageData.addAll(data) // Command data
        
        // Calculate checksum
        val checksum = calculateChecksum(data)
        packageData.add(((checksum and 0xFF00) shr 8).toByte()) // High byte
        packageData.add((checksum and 0xFF).toByte()) // Low byte
        
        packageData.add(0x03) // End byte
        
        return packageData.toByteArray()
    }
    
    private fun calculateChecksum(data: List<Byte>): Int {
        var sum = 0
        val btLength = data.size + 4
        val size = data.size + 2
        
        for (s in 0 until size) {
            when (s) {
                0 -> sum += btLength * (size - s)
                1 -> sum += 0x0A * (size - s)
                else -> sum += data[s - 2] * (size - s)
            }
        }
        
        return BigInteger("10000", 16).toInt() - (sum and 0xffff)
    }
    
    fun cleanup() {
        try {
            context.unregisterReceiver(discoveryReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            disconnectAllScanners()
        }
    }
}