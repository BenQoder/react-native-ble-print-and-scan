package com.margelo.nitro.bleprintandscan

import android.app.Activity
import com.margelo.nitro.bleprintandscan.*
import com.margelo.nitro.core.Promise
import com.margelo.nitro.NitroModules
import com.bleprintandscan.ScannerBluetoothManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HybridBleScanner: HybridBleScannerSpec() {
    
    private lateinit var scannerManager: ScannerBluetoothManager
    
    // MARK: - Scanner Connection Management
    
    override fun initializeScanner(): Promise<Unit> {
        return Promise.async {
            val appContext = NitroModules.applicationContext!!
            scannerManager = ScannerBluetoothManager(appContext)
            
            if (!scannerManager.isBluetoothSupported()) {
                throw Exception("Bluetooth is not supported on this device")
            }
            
            val activity = NitroModules.applicationContext!!.currentActivity as? Activity
                ?: throw Exception("Cannot initialize Scanner Bluetooth without an activity")
            
            val permissionsGranted = scannerManager.requestBluetoothPermissions(activity)
            if (!permissionsGranted) {
                throw Exception("Scanner Bluetooth permissions not granted")
            }
        }
    }
    
    override fun startScanningForScanners(onScannerFound: (Array<Device>) -> Unit): Promise<Unit> {
        return Promise.async {
            if (!scannerManager.isBluetoothEnabled()) {
                throw Exception("Please enable bluetooth")
            }
            
            scannerManager.startScanning { devices ->
                val deviceArray = devices.map { device ->
                    Device(device["id"]!!, device["name"]!!)
                }.toTypedArray()
                onScannerFound(deviceArray)
            }
        }
    }
    
    override fun suspendScanForScanners(): Promise<Unit> {
        return Promise.async {
            scannerManager.stopScanning()
        }
    }
    
    override fun connectToScanner(deviceId: String): Promise<Unit> {
        return Promise.async {
            if (!scannerManager.isBluetoothEnabled()) {
                throw Exception("Please enable bluetooth")
            }
            
            scannerManager.connectToScanner(deviceId)
        }
    }
    
    override fun disconnectFromScanner(deviceId: String): Promise<Unit> {
        return Promise.async {
            scannerManager.disconnect(deviceId)
        }
    }
    
    // MARK: - Multi-Scanner Management
    
    override fun isScannerConnected(deviceId: String): Promise<Boolean> {
        return Promise.async {
            scannerManager.isConnected(deviceId)
        }
    }
    
    override fun getConnectedScanners(): Promise<Array<Device>> {
        return Promise.async {
            val connectedScanners = scannerManager.getConnectedScanners()
            connectedScanners.map { scanner ->
                Device(scanner["id"]!!, scanner["name"]!!)
            }.toTypedArray()
        }
    }
    
    override fun disconnectAllScanners(): Promise<Unit> {
        return Promise.async {
            scannerManager.disconnectAllScanners()
        }
    }
    
    
    // MARK: - Scan Operations
    
    override fun startListening(deviceId: String, onScanResult: (ScanResult) -> Unit): Promise<Unit> {
        return Promise.async {
            if (!scannerManager.isConnected(deviceId)) {
                throw Exception("Scanner $deviceId is not connected")
            }
            
            scannerManager.startListening(deviceId) { scanData ->
                val scanResult = ScanResult(
                    data = scanData["data"]!!,
                    timestamp = scanData["timestamp"]!!,
                    deviceId = scanData["deviceId"]!!,
                    deviceName = scanData["deviceName"]!!
                )
                onScanResult(scanResult)
            }
        }
    }
    
    override fun stopListening(deviceId: String): Promise<Unit> {
        return Promise.async {
            scannerManager.stopListening(deviceId)
        }
    }
    
    override fun triggerScan(deviceId: String, duration: ScannerTrigger): Promise<Unit> {
        return Promise.async {
            if (!scannerManager.isConnected(deviceId)) {
                throw Exception("Scanner $deviceId is not connected")
            }
            
            val command = "%SCANTM#${duration.ordinal + 1}#"
            scannerManager.sendCommand(deviceId, command)
        }
    }
    
    // MARK: - Scanner Configuration
    
    override fun setScannerMode(deviceId: String, mode: ScannerMode): Promise<Unit> {
        return Promise.async {
            if (!scannerManager.isConnected(deviceId)) {
                throw Exception("Scanner $deviceId is not connected")
            }
            
            val command = when (mode) {
                ScannerMode.KEY_HOLD -> "%SCMD#00#"
                ScannerMode.CONTINUOUS -> "%SCMD#01#"
                ScannerMode.KEY_PULSE -> "%SCMD#02#"
                ScannerMode.HOST_TRIGGER -> "%SCMD#03#"
            }
            
            scannerManager.sendCommand(deviceId, command)
        }
    }
    
    override fun setBeepSettings(deviceId: String, settings: BeepSettings): Promise<Unit> {
        return Promise.async {
            if (!scannerManager.isConnected(deviceId)) {
                throw Exception("Scanner $deviceId is not connected")
            }
            
            val command = when (settings.volume) {
                BeepVolume.MUTE -> ScannerBluetoothManager.BEEP_MUTE
                BeepVolume.LOW -> ScannerBluetoothManager.BEEP_LOW_VOLUME
                BeepVolume.MIDDLE -> ScannerBluetoothManager.BEEP_MIDDLE_VOLUME
                BeepVolume.HIGH -> ScannerBluetoothManager.BEEP_HIGH_VOLUME
            }
            
            scannerManager.sendCommand(deviceId, command)
        }
    }
    
    override fun setPowerSettings(deviceId: String, settings: PowerSettings): Promise<Unit> {
        return Promise.async {
            if (!scannerManager.isConnected(deviceId)) {
                throw Exception("Scanner $deviceId is not connected")
            }
            
            val command = when (settings.sleepTimeMinutes.toInt()) {
                0 -> ScannerBluetoothManager.NEVER_SLEEP
                1 -> "\$RF#ST02"
                3 -> "\$RF#ST06"
                5 -> "\$RF#ST10"
                10 -> "\$RF#ST20"
                30 -> "\$RF#ST60"
                60 -> "\$RF#ST<0"
                120 -> "\$RF#STH0"
                else -> ScannerBluetoothManager.NEVER_SLEEP
            }
            
            scannerManager.sendCommand(deviceId, command)
        }
    }
    
    override fun setDataFormatSettings(deviceId: String, settings: DataFormatSettings): Promise<Unit> {
        return Promise.async {
            if (!scannerManager.isConnected(deviceId)) {
                throw Exception("Scanner $deviceId is not connected")
            }
            
            val commands = mutableListOf<String>()
            
            if (settings.prefixEnabled) {
                commands.add("\$DATA#2")
            }
            if (settings.suffixEnabled) {
                commands.add("\$DATA#1")
            }
            if (settings.hideBarcodePrefix) {
                commands.add("\$DATA#5")
            }
            if (settings.hideBarcodeContent) {
                commands.add("\$DATA#4")
            }
            if (settings.hideBarcodeSuffix) {
                commands.add("\$DATA#3")
            }
            
            for (command in commands) {
                scannerManager.sendCommand(deviceId, command)
            }
        }
    }
    
    override fun setTimestamp(deviceId: String, format: TimestampFormat, datetime: String?): Promise<Unit> {
        return Promise.async {
            if (!scannerManager.isConnected(deviceId)) {
                throw Exception("Scanner $deviceId is not connected")
            }
            
            val command = when (format) {
                TimestampFormat.DISABLED -> ScannerBluetoothManager.DISABLE_TIMESTAMP
                TimestampFormat.DATE_TIME, TimestampFormat.UNIX_TIMESTAMP -> ScannerBluetoothManager.ENABLE_TIMESTAMP
            }
            
            scannerManager.sendCommand(deviceId, command)
        }
    }
    
    // MARK: - Advanced Commands
    
    override fun restoreFactorySettings(deviceId: String): Promise<Unit> {
        return Promise.async {
            if (!scannerManager.isConnected(deviceId)) {
                throw Exception("Scanner $deviceId is not connected")
            }
            
            scannerManager.sendCommand(deviceId, ScannerBluetoothManager.RESTORE_FACTORY_SETTINGS)
        }
    }
    
    override fun customBeep(deviceId: String, level: Double): Promise<Unit> {
        return Promise.async {
            if (!scannerManager.isConnected(deviceId)) {
                throw Exception("Scanner $deviceId is not connected")
            }
            
            val clampedLevel = level.toInt().coerceIn(0, 26)
            val command = "\$BUZZ#B${(clampedLevel + 0x30).toChar()}"
            
            scannerManager.sendCommand(deviceId, command)
        }
    }
    
    override fun customBeepTime(deviceId: String, timeMs: Double, type: Double, frequencyHz: Double): Promise<Unit> {
        return Promise.async {
            if (!scannerManager.isConnected(deviceId)) {
                throw Exception("Scanner $deviceId is not connected")
            }
            
            val clampedTime = timeMs.toInt().coerceIn(10, 2540)
            val clampedType = type.toInt().coerceIn(0, 2)
            val clampedFreq = frequencyHz.toInt().coerceIn(100, 5200)
            
            val temp1 = (clampedTime + 10) / 10
            val s1 = temp1.toString(16)
            val s2 = clampedType.toString()
            val temp3 = (clampedFreq - 100) / 20
            val s3 = temp3.toString(16)
            
            val command = "\$BUZZ#BK$s1$s2$s3"
            
            scannerManager.sendCommand(deviceId, command)
        }
    }
    
    override fun powerOff(deviceId: String): Promise<Unit> {
        return Promise.async {
            if (!scannerManager.isConnected(deviceId)) {
                throw Exception("Scanner $deviceId is not connected")
            }
            
            scannerManager.sendCommand(deviceId, ScannerBluetoothManager.POWER_OFF)
        }
    }
    
    // MARK: - Data Management
    
    override fun getStoredDataCount(deviceId: String): Promise<Double> {
        return Promise.async {
            if (!scannerManager.isConnected(deviceId)) {
                throw Exception("Scanner $deviceId is not connected")
            }
            
            val response = scannerManager.sendCommand(deviceId, "%#+TCNT")
            // Parse the response to extract count
            // This would need proper parsing based on scanner response format
            0.0 // Placeholder
        }
    }
    
    override fun uploadStoredData(deviceId: String, clearAfterUpload: Boolean): Promise<Array<ScanResult>> {
        return Promise.async {
            if (!scannerManager.isConnected(deviceId)) {
                throw Exception("Scanner $deviceId is not connected")
            }
            
            val command = if (clearAfterUpload) "%#TXMEM#C" else "%#TXMEM"
            scannerManager.sendCommand(deviceId, command)
            
            // This would need proper implementation to collect multiple scan results
            arrayOf<ScanResult>()
        }
    }
    
    override fun clearStoredData(deviceId: String): Promise<Unit> {
        return Promise.async {
            if (!scannerManager.isConnected(deviceId)) {
                throw Exception("Scanner $deviceId is not connected")
            }
            
            scannerManager.sendCommand(deviceId, "%#*NEW*")
        }
    }
    
    // MARK: - Raw Command Interface
    
    override fun sendRawCommand(deviceId: String, command: String): Promise<String> {
        return Promise.async {
            if (!scannerManager.isConnected(deviceId)) {
                throw Exception("Scanner $deviceId is not connected")
            }
            
            scannerManager.sendCommand(deviceId, command)
        }
    }
}