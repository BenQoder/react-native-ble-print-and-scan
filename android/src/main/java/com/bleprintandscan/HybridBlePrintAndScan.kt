package com.bleprintandscan

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.util.Log
import com.margelo.nitro.bleprintandscan.HybridBlePrintAndScanSpec
import com.margelo.nitro.core.Promise
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HybridBlePrintAndScan: HybridBlePrintAndScanSpec() {
    
    private lateinit var bluetoothManager: BluetoothManager
    
    override fun sum(num1: Double, num2: Double): Double {
        return num1 + num2
    }
    
    override fun initializeBluetooth(promise: Promise<Unit>) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val appContext = hybridContext.context
                bluetoothManager = BluetoothManager(appContext)
                
                if (!bluetoothManager.isBluetoothSupported()) {
                    promise.reject(Exception("Bluetooth is not supported on this device"))
                    return@launch
                }
                
                val activity = hybridContext.currentActivity as? Activity
                if (activity == null) {
                    promise.reject(Exception("Cannot initialize Bluetooth without an activity"))
                    return@launch
                }
                
                val permissionsGranted = bluetoothManager.requestBluetoothPermissions(activity)
                if (permissionsGranted) {
                    promise.resolve(Unit)
                } else {
                    promise.reject(Exception("Bluetooth permissions not granted"))
                }
            } catch (e: Exception) {
                promise.reject(e)
            }
        }
    }
    
    override fun startScanningForBluetoothDevices(onDeviceFound: (Array<Map<String, String>>) -> Unit, promise: Promise<Unit>) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (!bluetoothManager.isBluetoothEnabled()) {
                    promise.reject(Exception("Please enable bluetooth"))
                    return@launch
                }
                
                bluetoothManager.startScanning(
                    onDeviceFound = { devices ->
                        val deviceArray = devices.map { device ->
                            mapOf("id" to device["id"]!!, "name" to device["name"]!!)
                        }.toTypedArray()
                        onDeviceFound(deviceArray)
                    },
                    promise = promise
                )
            } catch (e: Exception) {
                Log.e("HybridBlePrintAndScan", "Error scanning for devices: ${e.message}")
                promise.reject(e)
            }
        }
    }
    
    override fun suspendScanForBluetoothDevices(promise: Promise<Unit>) {
        bluetoothManager.suspendScanning(promise)
    }
    
    override fun connectToBluetoothDevice(deviceId: String, promise: Promise<Unit>) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (!bluetoothManager.isBluetoothEnabled()) {
                    promise.reject(Exception("Please enable bluetooth"))
                    return@launch
                }
                
                bluetoothManager.connectToDevice(deviceId)
                promise.resolve(Unit)
            } catch (e: Exception) {
                promise.reject(e)
            }
        }
    }
    
    override fun disconnectFromBluetoothDevice(promise: Promise<Unit>) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                bluetoothManager.disconnect()
                promise.resolve(Unit)
            } catch (e: Exception) {
                promise.reject(e)
            }
        }
    }
    
    override fun generateBytecode(value: String, printerWidth: Double, mtuSize: Double, promise: Promise<Array<ArrayBuffer>>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val lines = prepareImageForThermalPrinter(value, printerWidth.toInt(), mtuSize.toInt())
                val arrayBuffers = lines.map { it.toTypedArray() }.toTypedArray()
                promise.resolve(arrayBuffers)
            } catch (e: Exception) {
                promise.reject(e)
            }
        }
    }
    
    override fun generateBytecodeBase64(value: String, printerWidth: Double, mtuSize: Double, promise: Promise<Array<String>>) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val lines = prepareImageForThermalPrinter(value, printerWidth.toInt(), mtuSize.toInt())
                val base64Lines = lines.map { Base64.encodeToString(it, Base64.DEFAULT) }.toTypedArray()
                promise.resolve(base64Lines)
            } catch (e: Exception) {
                promise.reject(e)
            }
        }
    }
    
    override fun sendToBluetoothThermalPrinter(value: String, printerWidth: Double, promise: Promise<Unit>) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (!bluetoothManager.isBluetoothEnabled()) {
                    promise.reject(Exception("Please enable bluetooth"))
                    return@launch
                }
                
                val mtuSize = bluetoothManager.getAllowedMtu()
                val lines = prepareImageForThermalPrinter(value, printerWidth.toInt(), mtuSize)
                
                bluetoothManager.printWithDevice(lines)
                promise.resolve(Unit)
            } catch (e: Exception) {
                promise.reject(e)
            }
        }
    }
    
    override fun sendToUsbThermalPrinter(value: String, printerWidth: Double, chunkSize: Double, promise: Promise<Unit>) {
        // USB printing implementation would go here
        // For now, just reject as not implemented
        promise.reject(Exception("USB printing not implemented in this version"))
    }
    
    private fun convertTo1BitMonochrome(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val bytesPerRow = (width + 7) / 8 // Number of bytes per row (8 pixels per byte)

        val monochromeData = ByteArray(bytesPerRow * height)

        // Loop through each pixel, converting to 1-bit monochrome
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Convert to grayscale using the weighted average method
                val grayscaleValue = (0.299 * r + 0.587 * g + 0.114 * b).roundToInt()

                // Set bit to 0 if pixel is dark, 1 if bright (inverted for printing)
                if (grayscaleValue < 128) {
                    val byteIndex = y * bytesPerRow + (x / 8)
                    monochromeData[byteIndex] = monochromeData[byteIndex].toInt().or(1 shl (7 - (x % 8))).toByte()
                }
            }
        }

        return monochromeData
    }

    private fun prepareImageForThermalPrinter(base64ImageString: String, printerWidth: Int, mtuSize: Int): List<ByteArray> {
        // 1. Decode Base64 image
        val decodedString: ByteArray = Base64.decode(base64ImageString, Base64.DEFAULT)
        val decodedBitmap: Bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)

        // 2. Scale the bitmap if it exceeds the printer's width
        val scaledBitmap = if (decodedBitmap.width > printerWidth) {
            val aspectRatio = decodedBitmap.height.toFloat() / decodedBitmap.width
            val newHeight = (printerWidth * aspectRatio).roundToInt()
            Bitmap.createScaledBitmap(decodedBitmap, printerWidth, newHeight, true)
        } else {
            decodedBitmap
        }

        // 3. Convert to 1-bit monochrome
        val printerData: ByteArray = convertTo1BitMonochrome(scaledBitmap)

        // 4. Calculate bytes per line
        val bytesPerLine = (printerWidth + 7) / 8

        // 5. Create the image header (ESC/POS command)
        val header = ByteArray(8)
        header[0] = 0x1D // GS command
        header[1] = 0x76 // 'v'
        header[2] = 0x30 // '0'
        header[3] = 0x00 // Normal mode (no scaling)

        // Width of image in bytes (low byte, high byte)
        header[4] = (bytesPerLine % 256).toByte() // Low byte of width
        header[5] = (bytesPerLine / 256).toByte() // High byte of width

        // Height of image in pixels (low byte, high byte)
        header[6] = (scaledBitmap.height % 256).toByte() // Low byte of height
        header[7] = (scaledBitmap.height / 256).toByte() // High byte of height

        // 6. Split into lines (each line will be bytesPerLine wide)
        val imageData = printerData.asSequence()
            .chunked(bytesPerLine)
            .map { it.toByteArray() }
            .toList()

        // 7. Combine header and image data into chunks based on MTU size
        val chunkedData = mutableListOf<ByteArray>()
        var currentChunk = mutableListOf<Byte>()
        var remainingMtu = mtuSize

        // Add header as the first chunk
        currentChunk.addAll(header.asList())
        remainingMtu -= header.size

        for (line in imageData) {
            if (line.size <= remainingMtu) {
                currentChunk.addAll(line.asList())
                remainingMtu -= line.size
            } else {
                // Add the current chunk and start a new one
                chunkedData.add(currentChunk.toByteArray())
                currentChunk = mutableListOf<Byte>()
                currentChunk.addAll(line.asList())
                remainingMtu = mtuSize - line.size
            }
        }

        // Add the last chunk if any
        if (currentChunk.isNotEmpty()) {
            chunkedData.add(currentChunk.toByteArray())
        }

        return chunkedData
    }
}
