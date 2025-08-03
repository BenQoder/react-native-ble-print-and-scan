//
//  HybridBleScanner.swift
//  react-native-ble-print-and-scan
//
//  Created by Claude Code on 2025-01-05.
//

import Foundation
import UIKit
import NitroModules

class HybridBleScanner: HybridBleScannerSpec {
    
    private var scannerManager: ScannerBluetoothManager? = nil
    
    // MARK: - Scanner Connection Management
    
    func initializeScanner() throws -> Promise<Void> {
        return Promise.async {
            if self.scannerManager == nil {
                self.scannerManager = ScannerBluetoothManager.shared
            }
            
            let bluetoothInitialized = self.scannerManager?.requestBluetoothPermissions() ?? false
            
            if !bluetoothInitialized {
                throw NSError(domain: "ScannerBluetoothError", code: 1, userInfo: [NSLocalizedDescriptionKey: "Bluetooth permissions not granted"])
            }
        }
    }
    
    func startScanningForScanners(onScannerFound: @escaping ([Device]) -> Void) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            guard manager.isBluetoothSupported() else {
                throw NSError(domain: "ScannerBluetoothError", code: 3, userInfo: [NSLocalizedDescriptionKey: "Bluetooth is not supported on this device"])
            }
            
            guard manager.isBluetoothEnabled() else {
                throw NSError(domain: "ScannerBluetoothError", code: 4, userInfo: [NSLocalizedDescriptionKey: "Please enable bluetooth"])
            }
            
            manager.startScanning(onScannerFound: onScannerFound)
        }
    }
    
    func suspendScanForScanners() throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            _ = await manager.stopScanning()
        }
    }
    
    func connectToScanner(deviceId: String) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            guard manager.isBluetoothSupported() else {
                throw NSError(domain: "ScannerBluetoothError", code: 3, userInfo: [NSLocalizedDescriptionKey: "Bluetooth is not supported on this device"])
            }
            
            guard let uuid = UUID(uuidString: deviceId) else {
                throw NSError(domain: "ScannerBluetoothError", code: 5, userInfo: [NSLocalizedDescriptionKey: "Invalid device ID"])
            }
            
            _ = try await manager.connectToScanner(identifier: uuid)
        }
    }
    
    func disconnectFromScanner(deviceId: String) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            _ = try await manager.disconnect(deviceId: deviceId)
        }
    }
    
    // MARK: - Multi-Scanner Management
    
    func isScannerConnected(deviceId: String) throws -> Promise<Bool> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            return manager.isConnected(deviceId: deviceId)
        }
    }
    
    func getConnectedScanners() throws -> Promise<[Device]> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            return manager.getConnectedScanners()
        }
    }
    
    func disconnectAllScanners() throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            _ = try await manager.disconnectAllScanners()
        }
    }
    
    // MARK: - Scanner Information
    
    func getScannerInfo(deviceId: String) throws -> Promise<ScannerInfo> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            // Get firmware version
            let firmwareVersion = try await manager.sendCommand(deviceId: deviceId, command: "$SW#VER")
            
            return ScannerInfo(
                firmwareVersion: firmwareVersion,
                moduleType: "Unknown", // Could be retrieved with additional commands
                batteryLevel: nil, // Could be retrieved with "%BAT_VOL#"
                isConnected: manager.isConnected(deviceId: deviceId)
            )
        }
    }
    
    func getScannerSettings(deviceId: String) throws -> Promise<ScannerCurrentSettings> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            // This would require multiple commands to get all settings
            // For now, return default settings
            return ScannerCurrentSettings(
                workMode: ScannerMode.hostTrigger,
                beepSettings: BeepSettings(
                    volume: BeepVolume.middle,
                    tone: BeepTone.highTone,
                    enabled: true,
                    customLevel: nil
                ),
                powerSettings: PowerSettings(
                    sleepTimeMinutes: 0,
                    autoSleepEnabled: false
                ),
                dataFormatSettings: DataFormatSettings(
                    prefixEnabled: false,
                    suffixEnabled: false,
                    hideBarcodePrefix: false,
                    hideBarcodeContent: false,
                    hideBarcodeSuffix: false
                ),
                timestampEnabled: false
            )
        }
    }
    
    // MARK: - Scan Operations
    
    func startListening(deviceId: String, onScanResult: @escaping (ScanResult) -> Void) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            try manager.startListening(deviceId: deviceId, onScanResult: onScanResult)
        }
    }
    
    func stopListening(deviceId: String) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            try manager.stopListening(deviceId: deviceId)
        }
    }
    
    func triggerScan(deviceId: String, duration: ScannerTrigger) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            let command = "%SCANTM#\(duration.rawValue)#"
            _ = try await manager.sendCommand(deviceId: deviceId, command: command)
        }
    }
    
    // MARK: - Scanner Configuration
    
    func setScannerMode(deviceId: String, mode: ScannerMode) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            let command: String
            switch mode {
            case .keyHold:
                command = "%SCMD#00#"
            case .continuous:
                command = "%SCMD#01#"
            case .keyPulse:
                command = "%SCMD#02#"
            case .hostTrigger:
                command = "%SCMD#03#"
            }
            
            _ = try await manager.sendCommand(deviceId: deviceId, command: command)
        }
    }
    
    func setBeepSettings(deviceId: String, settings: BeepSettings) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            let command: String
            switch settings.volume {
            case .mute:
                command = "$BUZZ#0"
            case .low:
                command = "$BUZZ#3"
            case .middle:
                command = "$BUZZ#2"
            case .high:
                command = "$BUZZ#1"
            }
            
            _ = try await manager.sendCommand(deviceId: deviceId, command: command)
        }
    }
    
    func setPowerSettings(deviceId: String, settings: PowerSettings) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            let command: String
            switch settings.sleepTimeMinutes {
            case 0:
                command = "$RF#ST00" // Never sleep
            case 1:
                command = "$RF#ST02"
            case 3:
                command = "$RF#ST06"
            case 5:
                command = "$RF#ST10"
            case 10:
                command = "$RF#ST20"
            case 30:
                command = "$RF#ST60"
            case 60:
                command = "$RF#ST<0"
            case 120:
                command = "$RF#STH0"
            default:
                command = "$RF#ST00" // Default to never sleep
            }
            
            _ = try await manager.sendCommand(deviceId: deviceId, command: command)
        }
    }
    
    func setDataFormatSettings(deviceId: String, settings: DataFormatSettings) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            var commands: [String] = []
            
            if settings.prefixEnabled {
                commands.append("$DATA#2")
            }
            if settings.suffixEnabled {
                commands.append("$DATA#1")
            }
            if settings.hideBarcodePrefix {
                commands.append("$DATA#5")
            }
            if settings.hideBarcodeContent {
                commands.append("$DATA#4")
            }
            if settings.hideBarcodeSuffix {
                commands.append("$DATA#3")
            }
            
            for command in commands {
                _ = try await manager.sendCommand(deviceId: deviceId, command: command)
            }
        }
    }
    
    func setTimestamp(deviceId: String, format: TimestampFormat, datetime: String?) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            let command: String
            switch format {
            case .disabled:
                command = "%RTCSTAMP#0"
            case .dateTime, .unixTimestamp:
                command = "%RTCSTAMP#1"
            }
            
            _ = try await manager.sendCommand(deviceId: deviceId, command: command)
        }
    }
    
    // MARK: - Advanced Commands
    
    func restoreFactorySettings(deviceId: String) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            _ = try await manager.sendCommand(deviceId: deviceId, command: "%#IFSNO$B")
        }
    }
    
    func customBeep(deviceId: String, level: Double) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            let clampedLevel = max(0, min(26, Int(level)))
            let command = "$BUZZ#B\(String(format: "%c", clampedLevel + 0x30))"
            
            _ = try await manager.sendCommand(deviceId: deviceId, command: command)
        }
    }
    
    func customBeepTime(deviceId: String, timeMs: Double, type: Double, frequencyHz: Double) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            let clampedTime = max(10, min(2540, Int(timeMs)))
            let clampedType = max(0, min(2, Int(type)))
            let clampedFreq = max(100, min(5200, Int(frequencyHz)))
            
            let temp1 = (clampedTime + 10) / 10
            let s1 = String(format: "%x", temp1)
            let s2 = String(clampedType)
            let temp3 = (clampedFreq - 100) / 20
            let s3 = String(format: "%x", temp3)
            
            let command = "$BUZZ#BK\(s1)\(s2)\(s3)"
            
            _ = try await manager.sendCommand(deviceId: deviceId, command: command)
        }
    }
    
    func powerOff(deviceId: String) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            _ = try await manager.sendCommand(deviceId: deviceId, command: "$POWER#OFF")
        }
    }
    
    // MARK: - Data Management
    
    func getStoredDataCount(deviceId: String) throws -> Promise<Double> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            let response = try await manager.sendCommand(deviceId: deviceId, command: "%#+TCNT")
            // Parse the response to extract count
            // This would need proper parsing based on scanner response format
            return 0 // Placeholder
        }
    }
    
    func uploadStoredData(deviceId: String, clearAfterUpload: Bool) throws -> Promise<[ScanResult]> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            let command = clearAfterUpload ? "%#TXMEM#C" : "%#TXMEM"
            _ = try await manager.sendCommand(deviceId: deviceId, command: command)
            
            // This would need proper implementation to collect multiple scan results
            return []
        }
    }
    
    func clearStoredData(deviceId: String) throws -> Promise<Void> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            _ = try await manager.sendCommand(deviceId: deviceId, command: "%#*NEW*")
        }
    }
    
    // MARK: - Raw Command Interface
    
    func sendRawCommand(deviceId: String, command: String) throws -> Promise<String> {
        return Promise.async {
            guard let manager = self.scannerManager else {
                throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "ScannerBluetoothManager not initialized"])
            }
            
            return try await manager.sendCommand(deviceId: deviceId, command: command)
        }
    }
}