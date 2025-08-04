//
//  HybridBlePrintAndScan.swift
//  Pods
//
//  Created by Adinnu Benedict on 7/31/2025.
//

import Foundation
import UIKit
import NitroModules

extension Array {
    func chunked(into size: Int) -> [[Element]] {
        var chunks = [[Element]]()
        var currentIndex = startIndex
        while currentIndex < endIndex {
            let nextIndex = index(currentIndex, offsetBy: size, limitedBy: endIndex) ?? endIndex
            chunks.append(Array(self[currentIndex..<nextIndex]))
            currentIndex = nextIndex
        }
        return chunks
    }
}

class HybridBlePrinter: HybridBlePrinterSpec {
    
    private var bluetoothManager: BluetoothManager? = nil
    
    
    func initializePrinter() throws -> Promise<Void> {
        return Promise.async {
            if self.bluetoothManager == nil {
                self.bluetoothManager = BluetoothManager()
            }
            
            let bluetoothInitialized = self.bluetoothManager?.requestBluetoothPermissions() ?? false
            
            if !bluetoothInitialized {
                throw NSError(domain: "BluetoothError", code: 1, userInfo: [NSLocalizedDescriptionKey: "Bluetooth permissions not granted"])
            }
        }
    }
    
    func startScanningForPrinters(onDeviceFound: @escaping ([Device]) -> Void) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.bluetoothManager else {
                throw NSError(domain: "BluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "BluetoothManager not initialized"])
            }
            
            guard manager.isBluetoothSupported() else {
                throw NSError(domain: "BluetoothError", code: 3, userInfo: [NSLocalizedDescriptionKey: "Bluetooth is not supported on this device"])
            }
            
            guard manager.isBluetoothEnabled() else {
                throw NSError(domain: "BluetoothError", code: 4, userInfo: [NSLocalizedDescriptionKey: "Please enable bluetooth"])
            }
            
            manager.startScanning(onDeviceFound: onDeviceFound)
        }
    }
    
    func suspendScanForPrinters() throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.bluetoothManager else {
                throw NSError(domain: "BluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "BluetoothManager not initialized"])
            }
            
            _ = await manager.stopScanning()
        }
    }
    
    func connectToPrinter(deviceId: String) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.bluetoothManager else {
                throw NSError(domain: "BluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "BluetoothManager not initialized"])
            }
            
            guard manager.isBluetoothSupported() else {
                throw NSError(domain: "BluetoothError", code: 3, userInfo: [NSLocalizedDescriptionKey: "Bluetooth is not supported on this device"])
            }
            
            guard let uuid = UUID(uuidString: deviceId) else {
                throw NSError(domain: "BluetoothError", code: 5, userInfo: [NSLocalizedDescriptionKey: "Invalid device ID"])
            }
            
            _ = try await manager.connectToDevice(identifier: uuid)
        }
    }
    
    func disconnectFromPrinter(deviceId: String) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.bluetoothManager else {
                throw NSError(domain: "BluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "BluetoothManager not initialized"])
            }
            
            _ = try await manager.disconnect(deviceId: deviceId)
        }
    }
    
    func isPrinterConnected(deviceId: String) throws -> Promise<Bool> {
        return Promise.async {
            guard let manager = self.bluetoothManager else {
                throw NSError(domain: "BluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "BluetoothManager not initialized"])
            }
            
            return manager.isConnected(deviceId: deviceId)
        }
    }
    
    func getConnectedPrinters() throws -> Promise<[Device]> {
        return Promise.async {
            guard let manager = self.bluetoothManager else {
                throw NSError(domain: "BluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "BluetoothManager not initialized"])
            }
            
            return manager.getConnectedDevices()
        }
    }
    
    func disconnectAllPrinters() throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.bluetoothManager else {
                throw NSError(domain: "BluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "BluetoothManager not initialized"])
            }
            
            _ = try await manager.disconnectAllDevices()
        }
    }
    
    func generateBytecode(value: String, printerWidth: Double, mtuSize: Double) throws -> Promise<[ArrayBuffer]> {
        return Promise.async {
            let bitmapData = self.prepareImageForThermalPrinter(
                base64ImageString: value,
                printerWidth: Int(printerWidth),
                mtuSize: Int(mtuSize)
            )
            
            return try bitmapData.map { try ArrayBuffer.copy(data: Data($0)) }
        }
    }
    
    func generateBytecodeBase64(value: String, printerWidth: Double, mtuSize: Double) throws -> Promise<[String]> {
        return Promise.async {
            let bitmapData = self.prepareImageForThermalPrinter(
                base64ImageString: value,
                printerWidth: Int(printerWidth),
                mtuSize: Int(mtuSize)
            )
            
            return self.prepareImageForBase64ThermalPrinter(lines: bitmapData)
        }
    }
    
    func sendToBluetoothThermalPrinter(deviceId: String, value: String, printerWidth: Double) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.bluetoothManager else {
                throw NSError(domain: "BluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "BluetoothManager not initialized"])
            }
            
            guard manager.isBluetoothSupported() else {
                throw NSError(domain: "BluetoothError", code: 3, userInfo: [NSLocalizedDescriptionKey: "Bluetooth is not supported on this device"])
            }
            
            guard manager.isConnected(deviceId: deviceId) else {
                throw NSError(domain: "BluetoothError", code: 7, userInfo: [NSLocalizedDescriptionKey: "Device \(deviceId) is not connected"])
            }
            
            let mtuSize = manager.getAllowedMtu(deviceId: deviceId)
            
            let bitmapData = self.prepareImageForThermalPrinter(
                base64ImageString: value,
                printerWidth: Int(printerWidth),
                mtuSize: mtuSize
            )
            
            print("Printing to device \(deviceId) with length \(bitmapData.count) bytes and MTU \(mtuSize)")
            
            _ = try await manager.printWithDevice(deviceId: deviceId, lines: bitmapData.map { Data($0) })
        }
    }
    
    func sendToUsbThermalPrinter(value: String, printerWidth: Double, chunkSize: Double) throws -> Promise<Void> {
        return Promise.async {
            // USB printing not implemented for iOS
            throw NSError(domain: "BluetoothError", code: 6, userInfo: [NSLocalizedDescriptionKey: "USB printing not implemented in this version"])
        }
    }
    
    func convertTo1BitMonochrome(bitmap: UIImage, maxWidth: Int) -> [UInt8] {
        guard let cgImage = bitmap.cgImage else { return [] }
        let width = cgImage.width
        let height = cgImage.height
        let bytesPerRow = (width + 7) / 8

        var monochromeData = [UInt8](repeating: 0, count: bytesPerRow * height)

        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 4 * width, space: colorSpace, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        
        context?.draw(cgImage, in: CGRect(x: 0, y: 0, width: CGFloat(width), height: CGFloat(height)))
        
        guard let pixelBuffer = context?.data else { return [] }
        let pixels = pixelBuffer.bindMemory(to: UInt8.self, capacity: width * height * 4)

        for y in 0..<height {
            for x in 0..<width {
                let offset = (y * width + x) * 4
                let r = pixels[offset]
                let g = pixels[offset + 1]
                let b = pixels[offset + 2]

                // Convert to grayscale using the weighted average method
                let grayscaleValue = Int(0.299 * Double(r) + 0.587 * Double(g) + 0.114 * Double(b))

                // Set bit to 0 if pixel is dark, 1 if bright (inverted for printing)
                if grayscaleValue < 128 {
                    let byteIndex = y * bytesPerRow + (x / 8)
                    monochromeData[byteIndex] |= (1 << (7 - (x % 8)))
                }
            }
        }

        return monochromeData
    }
    
    func prepareImageForBase64ThermalPrinter(lines: [[UInt8]])-> [String]{
        
        let base64Strings = lines.map{ Data($0).base64EncodedString() }
        
        return base64Strings
    }

    func prepareImageForThermalPrinter(base64ImageString: String, printerWidth: Int, mtuSize: Int) -> [[UInt8]] {
        // 1. Decode Base64 image
        guard let decodedData = Data(base64Encoded: base64ImageString),
              let decodedImage = UIImage(data: decodedData) else {
            return []
        }

        // 2. Scale the bitmap if it exceeds the printer's width
        let scaledImage: UIImage
        if let cgImage = decodedImage.cgImage, cgImage.width > printerWidth {
            let aspectRatio = CGFloat(cgImage.height) / CGFloat(cgImage.width)
            let newHeight = Int(CGFloat(printerWidth) * aspectRatio)
            let newSize = CGSize(width: printerWidth, height: newHeight)
            UIGraphicsBeginImageContext(newSize)
            decodedImage.draw(in: CGRect(origin: .zero, size: newSize))
            scaledImage = UIGraphicsGetImageFromCurrentImageContext() ?? decodedImage
            UIGraphicsEndImageContext()
        } else {
            scaledImage = decodedImage
        }

        // 3. Convert to 1-bit monochrome
        let printerData = convertTo1BitMonochrome(bitmap: scaledImage, maxWidth: printerWidth)

        // 4. Calculate bytes per line
        let bytesPerLine = (printerWidth + 7) / 8

        // 5. Create the image header (ESC/POS command)
        var header = [UInt8](repeating: 0, count: 8)
        header[0] = 0x1D // GS command
        header[1] = 0x76 // 'v'
        header[2] = 0x30 // '0'
        header[3] = 0x00 // Normal mode (no scaling)

        // Width of image in bytes (low byte, high byte)
        header[4] = UInt8(bytesPerLine % 256) // Low byte of width
        header[5] = UInt8(bytesPerLine / 256) // High byte of width

        // Height of image in pixels (low byte, high byte)
        header[6] = UInt8(scaledImage.size.height.truncatingRemainder(dividingBy: 256)) // Low byte of height
        header[7] = UInt8(scaledImage.size.height / 256) // High byte of height

        // 6. Split into lines (each line will be bytesPerLine wide)
        var imageData = [[UInt8]]()
        for y in stride(from: 0, to: printerData.count, by: bytesPerLine) {
            let lineData = Array(printerData[y..<min(y + bytesPerLine, printerData.count)])
            imageData.append(lineData)
        }

        // 7. Combine header and image data into larger chunks based on MTU size
        var chunkedData = [[UInt8]]()
        var currentChunk = [UInt8]()
        var remainingMtu = mtuSize

        // Add header as the first chunk
        currentChunk.append(contentsOf: header)
        remainingMtu -= header.count

        for line in imageData {
            if line.count <= remainingMtu {
                currentChunk.append(contentsOf: line)
                remainingMtu -= line.count
            } else {
                chunkedData.append(currentChunk)
                currentChunk = line
                remainingMtu = mtuSize - line.count
            }
        }

        // Add the last chunk if any
        if !currentChunk.isEmpty {
            chunkedData.append(currentChunk)
        }

        return chunkedData
    }
    
    // MARK: - Paper Control Functions
    
    func feedPaper(deviceId: String, lines: Double) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.bluetoothManager else {
                throw NSError(domain: "BluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "BluetoothManager not initialized"])
            }
            
            guard manager.isConnected(deviceId: deviceId) else {
                throw NSError(domain: "BluetoothError", code: 6, userInfo: [NSLocalizedDescriptionKey: "Device not connected"])
            }
            
            // ESC/POS command for line feed: ESC d + number of lines
            let clampedLines = max(1, min(255, Int(lines))) // Ensure lines is between 1 and 255
            let feedCommand = Data([0x1B, 0x64, UInt8(clampedLines)])
            
            _ = try await manager.sendRawData(deviceId: deviceId, data: feedCommand)
        }
    }
    
    func cutPaper(deviceId: String) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.bluetoothManager else {
                throw NSError(domain: "BluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "BluetoothManager not initialized"])
            }
            
            guard manager.isConnected(deviceId: deviceId) else {
                throw NSError(domain: "BluetoothError", code: 6, userInfo: [NSLocalizedDescriptionKey: "Device not connected"])
            }
            
            // ESC/POS command for full cut: GS V + 65 + 0 (full cut)
            let cutCommand = Data([0x1D, 0x56, 0x41, 0x00])
            
            _ = try await manager.sendRawData(deviceId: deviceId, data: cutCommand)
        }
    }
}
