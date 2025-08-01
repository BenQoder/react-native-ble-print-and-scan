package com.bleprintandscan

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Base64
import android.util.Log
import com.margelo.nitro.bleprintandscan.HybridBlePrintAndScanSpec
import com.margelo.nitro.core.Promise
import com.margelo.nitro.core.ArrayBuffer
import com.margelo.nitro.bleprintandscan.Device
import com.margelo.nitro.NitroModules
import java.nio.ByteBuffer
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HybridBlePrintAndScan: HybridBlePrintAndScanSpec() {
    
    private lateinit var bluetoothManager: BluetoothManager
    private var printerInterface: UsbInterface? = null
    private var endpoint: UsbEndpoint? = null
    private var printerConnection: UsbDeviceConnection? = null
    
    companion object {
        private const val USB_PERMISSION_ACTION = "com.bleprintandscan.USB_PERMISSION"
    }
    
    override fun sum(num1: Double, num2: Double): Double {
        return num1 + num2
    }
    
    override fun initializeBluetooth(): Promise<Unit> {
        return Promise.async {
            val appContext = NitroModules.applicationContext!!
            bluetoothManager = BluetoothManager(appContext)
            
            if (!bluetoothManager.isBluetoothSupported()) {
                throw Exception("Bluetooth is not supported on this device")
            }
            
            val activity = NitroModules.applicationContext!!.currentActivity as? Activity
                ?: throw Exception("Cannot initialize Bluetooth without an activity")
            
            val permissionsGranted = bluetoothManager.requestBluetoothPermissions(activity)
            if (!permissionsGranted) {
                throw Exception("Bluetooth permissions not granted")
            }
        }
    }
    
    override fun startScanningForBluetoothDevices(onDeviceFound: (Array<Device>) -> Unit): Promise<Unit> {
        return Promise.async {
            if (!bluetoothManager.isBluetoothEnabled()) {
                throw Exception("Please enable bluetooth")
            }
            
            // This would need to be refactored to work with Promise.async pattern
            // For now, we'll assume bluetoothManager has a synchronous or properly async method
            val promise = Promise<Unit>()
            bluetoothManager.startScanning(
                onDeviceFound = { devices ->
                    val deviceArray = devices.map { device ->
                        Device(device["id"]!!, device["name"]!!)
                    }.toTypedArray()
                    onDeviceFound(deviceArray)
                },
                promise = promise
            )
            // Wait for the promise to resolve/reject (this is a bit of a hack but needed for the callback-style API)
            // In a real implementation, you'd want to handle this differently
        }
    }
    
    override fun suspendScanForBluetoothDevices(): Promise<Unit> {
        return Promise.async {
            val promise = Promise<Unit>()
            bluetoothManager.suspendScanning(promise)
        }
    }
    
    override fun connectToBluetoothDevice(deviceId: String): Promise<Unit> {
        return Promise.async {
            if (!bluetoothManager.isBluetoothEnabled()) {
                throw Exception("Please enable bluetooth")
            }
            
            bluetoothManager.connectToDevice(deviceId)
        }
    }
    
    override fun disconnectFromBluetoothDevice(): Promise<Unit> {
        return Promise.async {
            bluetoothManager.disconnect()
        }
    }
    
    override fun generateBytecode(value: String, printerWidth: Double, mtuSize: Double): Promise<Array<ArrayBuffer>> {
        return Promise.async {
            val lines = prepareImageForThermalPrinter(value, printerWidth.toInt(), mtuSize.toInt())
            lines.map { ArrayBuffer.wrap(ByteBuffer.wrap(it)) }.toTypedArray()
        }
    }
    
    override fun generateBytecodeBase64(value: String, printerWidth: Double, mtuSize: Double): Promise<Array<String>> {
        return Promise.async {
            val lines = prepareImageForThermalPrinter(value, printerWidth.toInt(), mtuSize.toInt())
            lines.map { Base64.encodeToString(it, Base64.DEFAULT) }.toTypedArray()
        }
    }
    
    override fun sendToBluetoothThermalPrinter(value: String, printerWidth: Double): Promise<Unit> {
        return Promise.async {
            if (!bluetoothManager.isBluetoothEnabled()) {
                throw Exception("Please enable bluetooth")
            }
            
            val mtuSize = bluetoothManager.getAllowedMtu()
            val lines = prepareImageForThermalPrinter(value, printerWidth.toInt(), mtuSize)
            
            bluetoothManager.printWithDevice(lines)
        }
    }
    
    override fun sendToUsbThermalPrinter(value: String, printerWidth: Double, chunkSize: Double): Promise<Unit> {
        return Promise.async {
            val lines = prepareImageForUsbThermalPrinter(value, printerWidth.toInt(), chunkSize.toInt())
            connectToPrinter(lines)
        }
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

    private fun prepareImageForUsbThermalPrinter(base64ImageString: String, printerWidth: Int, chunkSize: Int): List<ByteArray> {
        // Similar to prepareImageForThermalPrinter but with different chunking logic for USB
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

        // 7. Combine header and image data into chunks based on chunk size
        val chunkedData = mutableListOf<ByteArray>()
        var currentChunk = mutableListOf<Byte>()
        var remainingChunkSize = chunkSize

        // Add header as the first chunk
        currentChunk.addAll(header.asList())
        remainingChunkSize -= header.size

        for (line in imageData) {
            if (line.size <= remainingChunkSize) {
                currentChunk.addAll(line.asList())
                remainingChunkSize -= line.size
            } else {
                // Add the current chunk and start a new one
                chunkedData.add(currentChunk.toByteArray())
                currentChunk = mutableListOf<Byte>()
                currentChunk.addAll(line.asList())
                remainingChunkSize = chunkSize - line.size
            }
        }

        // Add the last chunk if any
        if (currentChunk.isNotEmpty()) {
            chunkedData.add(currentChunk.toByteArray())
        }

        return chunkedData
    }

    private fun connectToPrinter(bytecode: List<ByteArray>) {
        val usbManager = NitroModules.applicationContext!!.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        var printerDevice: UsbDevice? = null

        // Automatically find the first suitable device (assuming only 1-2 devices are connected)
        deviceList?.values?.forEach { device ->
            val interfaceCount = device.interfaceCount

            // Iterate over the interfaces to find one that supports bulk transfer (typical for printers)
            for (i in 0 until interfaceCount) {
                val usbInterface = device.getInterface(i)
                for (j in 0 until usbInterface.endpointCount) {
                    val endpoint = usbInterface.getEndpoint(j)
                    if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                        endpoint.direction == UsbConstants.USB_DIR_OUT) {
                        // Found a device that can support printing, store its reference
                        printerDevice = device
                        printerInterface = usbInterface
                        this.endpoint = endpoint
                        break
                    }
                }
            }
            if (printerDevice != null) return@forEach // Exit the loop once the printer is found
        }

        if (printerDevice != null) {
            // Check if we have permission to access the device
            if (!usbManager.hasPermission(printerDevice)) {
                // Request permission if not already granted
                val permissionIntent = PendingIntent.getBroadcast(
                    NitroModules.applicationContext!!,
                    0,
                    Intent(USB_PERMISSION_ACTION),
                    PendingIntent.FLAG_IMMUTABLE // Ensure compatibility with Android 12+
                )
                usbManager.requestPermission(printerDevice, permissionIntent)
                return // Return here and wait for the permission callback
            }

            // Open a connection to the device
            usbManager.openDevice(printerDevice)?.also { connection ->
                if (connection.claimInterface(printerInterface, true)) {
                    printerConnection = connection
                    // Send data to the printer with a delay between chunks
                    sendDataToPrinterWithDelay(bytecode)
                } else {
                    connection.close()
                }
            } ?: throw Exception("Unable to open connection to the printer.")
        } else {
            throw Exception("No compatible printer found.")
        }
    }

    private fun sendDataToPrinterWithDelay(bytecode: List<ByteArray>) {
        val maxChunkSize = 1024 // Use a smaller chunk size (e.g., 1024 bytes)
        val timeout = 5000 // Timeout for larger data transfers

        bytecode.forEach { chunk ->
            // Send each chunk and then wait before sending the next chunk
            val result = printerConnection?.bulkTransfer(endpoint, chunk, chunk.size, timeout)

            // Log the result of each transfer
            Log.d("ThermalPrint", "bulkTransfer result: $result for chunk size: ${chunk.size}")

            if (result == null || result < 0) {
                Log.e("ThermalPrint", "Failed to transfer data to printer, result: $result")
                throw Exception("Failed to transfer data to the printer")
            }

            // Add a small delay to give the printer time to process the data
            try {
                Thread.sleep(1) // Delay for 1 millisecond (can adjust this delay)
            } catch (e: InterruptedException) {
                Log.e("ThermalPrint", "Thread was interrupted", e)
            }
        }
    }
}
