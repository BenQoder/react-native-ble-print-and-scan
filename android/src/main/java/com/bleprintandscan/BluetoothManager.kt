package com.bleprintandscan

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanCallback
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.RxBleConnection
import com.polidea.rxandroidble3.RxBleDevice
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class ConnectionInfo(
    val device: RxBleDevice,
    var connection: RxBleConnection? = null,
    var writeableUUIDs: List<UUID> = listOf(),
    var isConnected: Boolean = false
)

class BluetoothManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val devices = mutableMapOf<String, String>() // Stores device ID and name
    private var scanSubscription: Disposable? = null
    private val rxBleClient: RxBleClient = RxBleClient.create(context)
    private val compositeDisposable = CompositeDisposable()
    private val connections = mutableMapOf<String, ConnectionInfo>() // deviceId -> ConnectionInfo
    private val deviceDisposables = mutableMapOf<String, CompositeDisposable>() // Per-device disposables

    companion object {
        const val REQUEST_BLUETOOTH_PERMISSIONS = 1
    }

    private val knownWritableUUIDs = listOf(
        UUID.fromString("00002AF1-0000-1000-8000-00805F9B34FB")
    )

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    private fun arePermissionsGranted(permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun requestBluetoothPermissions(activity: Activity): Boolean = suspendCoroutine { continuation ->
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        if (arePermissionsGranted(permissions)) {
            continuation.resume(true) // Permissions are already granted
            return@suspendCoroutine
        }

        ActivityCompat.requestPermissions(
            activity,
            permissions,
            REQUEST_BLUETOOTH_PERMISSIONS
        )

        if (arePermissionsGranted(permissions)) {
            continuation.resume(true) // Permissions are already granted
            return@suspendCoroutine
        }

        continuation.resume(false)
    }

    fun startScanning(onDeviceFound: (Array<Map<String, String>>) -> Unit, promise: com.margelo.nitro.core.Promise<Unit>) {
        val devicesFound = mutableListOf<Map<String, String>>()
        scanSubscription?.dispose()
        devices.clear()

        try {
            scanSubscription = rxBleClient.scanBleDevices(
                com.polidea.rxandroidble3.scan.ScanSettings.Builder()
                    .setCallbackType(com.polidea.rxandroidble3.scan.ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                    .build()
            ).subscribe(
                { scanResult ->
                    val deviceName = scanResult.bleDevice.name
                    val deviceId = scanResult.bleDevice.macAddress
                    if (!deviceName.isNullOrBlank() && !devices.containsKey(deviceId)) {
                        devices[deviceId] = deviceName
                        devicesFound.add(mapOf("id" to deviceId, "name" to deviceName))
                        onDeviceFound(devicesFound.toTypedArray())
                    }
                },
                { throwable ->
                    Log.d("BluetoothManager", "Device Error: ${throwable.message}")
                    promise.reject(
                        Exception("Error while scanning for devices: ${throwable.message}", throwable)
                    )
                }
            )

            promise.resolve(Unit)

        } catch (e: Exception) {
            Log.d("BluetoothManager", "Device Error: ${e.message}")
            promise.reject(
                Exception("Error while scanning for devices: ${e.message}", e)
            )
        }
    }

    fun suspendScanning(promise: com.margelo.nitro.core.Promise<Unit>) {
        try {
            if (scanSubscription != null && !scanSubscription!!.isDisposed) {
                scanSubscription!!.dispose() // Dispose of the current scan
                scanSubscription = null
                devices.clear() // Optionally clear discovered devices
                Log.d("BluetoothManager", "Scanning has been suspended successfully.")
                promise.resolve(Unit)
            } else {
                Log.d("BluetoothManager", "No active scan to suspend.")
                promise.resolve(Unit)
            }
        } catch (e: Exception) {
            Log.e("BluetoothManager", "Error while suspending scan: ${e.message}")
            promise.reject(
                Exception("Failed to suspend scanning: ${e.message}", e)
            )
        }
    }

    suspend fun connectToDevice(deviceId: String): Boolean = suspendCoroutine { continuation ->
        // Check if already connected
        if (connections[deviceId]?.isConnected == true) {
            continuation.resume(true)
            return@suspendCoroutine
        }

        val device = rxBleClient.getBleDevice(deviceId)
        Log.d("BluetoothManager", "Connecting to device: $deviceId, State: ${device.connectionState}")

        // Initialize disposable container for this device
        if (!deviceDisposables.containsKey(deviceId)) {
            deviceDisposables[deviceId] = CompositeDisposable()
        }

        val disposable = device.establishConnection(false).subscribe(
            { rxBleConnection ->
                Log.d("BluetoothManager", "Connected to device: $deviceId")
                val connectionInfo = ConnectionInfo(device = device, connection = rxBleConnection, isConnected = true)
                connections[deviceId] = connectionInfo
                
                discoverServices(deviceId, rxBleConnection)
                continuation.resume(true)
            },
            { throwable ->
                Log.e("BluetoothManager", "Connection error for device $deviceId: ${throwable.message}")
                continuation.resumeWithException(
                    Exception("Error while connecting to device $deviceId: ${throwable.message}", throwable)
                )
            }
        )

        deviceDisposables[deviceId]?.add(disposable)
    }

    suspend fun disconnect(deviceId: String): Boolean = suspendCoroutine { continuation ->
        val connectionInfo = connections[deviceId]
        if (connectionInfo == null || !connectionInfo.isConnected) {
            Log.d("BluetoothManager", "No active connection to disconnect for device: $deviceId")
            continuation.resume(true)
            return@suspendCoroutine
        }

        try {
            // Dispose of the device-specific disposables
            deviceDisposables[deviceId]?.clear()
            deviceDisposables.remove(deviceId)
            
            // Update connection state
            connections[deviceId]?.isConnected = false
            connections.remove(deviceId)
            
            Log.d("BluetoothManager", "Disconnected successfully from device: $deviceId")
            continuation.resume(true)
        } catch (e: Exception) {
            Log.e("BluetoothManager", "Error while disconnecting from device $deviceId: ${e.message}")
            continuation.resumeWithException(
                Exception("Error while disconnecting from device $deviceId: ${e.message}", e)
            )
        }
    }

    suspend fun disconnectAllDevices(): Boolean {
        var allSuccess = true
        val deviceIds = connections.keys.toList()
        
        for (deviceId in deviceIds) {
            try {
                val success = disconnect(deviceId)
                if (!success) {
                    allSuccess = false
                }
            } catch (e: Exception) {
                allSuccess = false
                Log.e("BluetoothManager", "Failed to disconnect device $deviceId: ${e.message}")
            }
        }
        
        return allSuccess
    }

    fun getAllowedMtu(deviceId: String): Int {
        return connections[deviceId]?.connection?.mtu ?: 20
    }
    
    fun isConnected(deviceId: String): Boolean {
        return connections[deviceId]?.isConnected ?: false
    }
    
    fun getConnectedDevices(): List<Map<String, String>> {
        return connections.filter { it.value.isConnected }.map { (deviceId, connectionInfo) ->
            mapOf(
                "id" to deviceId,
                "name" to (devices[deviceId] ?: "Unknown Device")
            )
        }
    }

    suspend fun printWithDevice(deviceId: String, lines: List<ByteArray>): Boolean = suspendCoroutine { continuation ->
        val connectionInfo = connections[deviceId]
        if (connectionInfo == null || !connectionInfo.isConnected || connectionInfo.connection == null) {
            continuation.resumeWithException(
                Exception("No active connection to print with for device: $deviceId")
            )
            return@suspendCoroutine
        }

        Log.d("BluetoothManager", "Printing to device: $deviceId, State: ${connectionInfo.device.connectionState}")

        val totalSize = lines.sumOf { it.size }
        val byteArray = ByteArray(totalSize)
        var offset = 0
        for (line in lines) {
            line.copyInto(byteArray, offset)
            offset += line.size
        }

        val disposable = connectionInfo.connection!!.createNewLongWriteBuilder()
            .setCharacteristicUuid(knownWritableUUIDs.first())
            .setBytes(byteArray)
            .setMaxBatchSize(50)
            .build()
            .subscribe(
                {
                    Log.d("BluetoothManager", "Data successfully sent to device: $deviceId")
                    continuation.resume(true)
                },
                { throwable ->
                    Log.e("BluetoothManager", "Error while printing to device $deviceId: ${throwable.message}")
                    continuation.resumeWithException(
                        Exception("Error while printing to device $deviceId: ${throwable.message}", throwable)
                    )
                }
            )

        deviceDisposables[deviceId]?.add(disposable)
    }

    private fun discoverServices(deviceId: String, rxBleConnection: RxBleConnection) {
        val mtuRequestDisposable = rxBleConnection.requestMtu(512)
            .subscribe({
                val disposable = rxBleConnection.discoverServices()
                    .subscribe(
                        { services ->
                            val writeableUUIDs = mutableListOf<UUID>()
                            services.bluetoothGattServices.forEach { service ->
                                service.characteristics.forEach { characteristic ->
                                    if (isCharacteristicWritable(characteristic)) {
                                        writeableUUIDs.add(characteristic.uuid)
                                    }
                                }
                            }
                            
                            // Update connection info with discovered characteristics
                            connections[deviceId]?.writeableUUIDs = writeableUUIDs
                            Log.d("BluetoothManager", "Service discovery completed for device $deviceId. Found ${writeableUUIDs.size} writable characteristics.")
                        },
                        { throwable ->
                            Log.e("BluetoothManager", "Service discovery failed for device $deviceId: ${throwable.message}")
                        }
                    )
                deviceDisposables[deviceId]?.add(disposable)
            }, { throwable ->
                Log.e("BluetoothManager", "Error while setting MTU for device $deviceId: ${throwable.message}")
            })

        deviceDisposables[deviceId]?.add(mtuRequestDisposable)
    }

    private fun isCharacteristicWritable(characteristic: BluetoothGattCharacteristic): Boolean {
        val properties = characteristic.properties
        return (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) ||
                (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
    }
}