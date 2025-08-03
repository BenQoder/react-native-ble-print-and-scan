//
//  ScannerBluetoothManager.swift
//  react-native-ble-print-and-scan
//
//  Created by Claude Code on 2025-01-05.
//

import CoreBluetooth
import Foundation
import NitroModules

struct ScannerConnectionInfo {
    let peripheral: CBPeripheral
    var readCharacteristic: CBCharacteristic?
    var writeCharacteristic: CBCharacteristic?
    var isConnected: Bool = false
    var isListening: Bool = false
}

class ScannerBluetoothManager: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    private var centralManager: CBCentralManager!
    private var discoveredPeripherals: [CBPeripheral] = []
    private var scannerConnections: [String: ScannerConnectionInfo] = [:] // deviceId -> ScannerConnectionInfo
    
    // Scanner-specific UUIDs (from natum-ios analysis)
    private let scannerServiceUUIDs = [
        CBUUID(string: "FF00"),
        CBUUID(string: "FFF0")
    ]
    
    private let readCharacteristicUUIDs = [
        CBUUID(string: "FF01"),
        CBUUID(string: "FFF1")
    ]
    
    private let writeCharacteristicUUIDs = [
        CBUUID(string: "FF02"),
        CBUUID(string: "FFF2")
    ]
    
    var onScannerFound: (([Device]) -> Void)?
    
    let serialQueue: DispatchQueue = DispatchQueue(label: "com.benqoder.scanner.bluetooth.serialQueue")
    
    // Connection callbacks per device
    private var connectCallbacks: [String: (Result<Bool, Error>) -> Void] = [:]
    private var disconnectCallbacks: [String: (Result<Bool, Error>) -> Void] = [:]
    
    // Data reception callbacks per device
    private var dataCallbacks: [String: (ScanResult) -> Void] = [:]
    
    // Command response callbacks
    private var commandCallbacks: [String: (Result<String, Error>) -> Void] = [:]
    
    // Service discovery status tracking
    private var servicesCheckStatus: [CBUUID: Bool] = [:]
    
    static let shared = ScannerBluetoothManager()
    
    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }
    
    // MARK: - Public Interface
    
    func requestBluetoothPermissions() -> Bool {
        return centralManager.state == .poweredOn
    }
    
    func isBluetoothSupported() -> Bool {
        return centralManager != nil
    }
    
    func isBluetoothEnabled() -> Bool {
        return centralManager.state == .poweredOn
    }
    
    func startScanning(onScannerFound: @escaping ([Device]) -> Void) {
        self.onScannerFound = onScannerFound
        discoveredPeripherals.removeAll()
        
        guard centralManager.state == .poweredOn else {
            print("Bluetooth not powered on")
            return
        }
        
        // Scan for scanner-specific services
        centralManager.scanForPeripherals(withServices: scannerServiceUUIDs, options: [
            CBCentralManagerScanOptionAllowDuplicatesKey: false
        ])
    }
    
    func stopScanning() async -> Bool {
        centralManager.stopScan()
        return true
    }
    
    func connectToScanner(identifier: UUID) async throws -> Bool {
        return try await withCheckedThrowingContinuation { continuation in
            let deviceId = identifier.uuidString
            
            // Check if already connected
            if let connectionInfo = scannerConnections[deviceId], connectionInfo.isConnected {
                continuation.resume(returning: true)
                return
            }
            
            guard let peripheral = discoveredPeripherals.first(where: { $0.identifier == identifier }) else {
                continuation.resume(throwing: NSError(domain: "ScannerBluetoothError", code: 1, userInfo: [NSLocalizedDescriptionKey: "Could not find scanner with identifier \(identifier)"]))
                return
            }
            
            print("Connecting to scanner \(peripheral.name ?? "Unknown")...")

            // Store callback for this specific device
            connectCallbacks[deviceId] = { result in
                switch result {
                case .success(let success):
                    continuation.resume(returning: success)
                case .failure(let error):
                    continuation.resume(throwing: error)
                }
            }

            centralManager.connect(peripheral, options: [CBConnectPeripheralOptionNotifyOnDisconnectionKey: true])
        }
    }
    
    func disconnect(deviceId: String) async throws -> Bool {
        return try await withCheckedThrowingContinuation { continuation in
            guard let connectionInfo = scannerConnections[deviceId] else {
                continuation.resume(returning: true)
                return
            }
            
            // Store callback for this specific device
            disconnectCallbacks[deviceId] = { result in
                switch result {
                case .success(let success):
                    continuation.resume(returning: success)
                case .failure(let error):
                    continuation.resume(throwing: error)
                }
            }
            
            centralManager.cancelPeripheralConnection(connectionInfo.peripheral)
        }
    }
    
    func disconnectAllScanners() async throws -> Bool {
        var allSuccess = true
        let deviceIds = Array(scannerConnections.keys)
        
        for deviceId in deviceIds {
            do {
                let success = try await disconnect(deviceId: deviceId)
                if !success {
                    allSuccess = false
                }
            } catch {
                allSuccess = false
                print("Failed to disconnect scanner \(deviceId): \(error)")
            }
        }
        
        return allSuccess
    }
    
    func isConnected(deviceId: String) -> Bool {
        return scannerConnections[deviceId]?.isConnected ?? false
    }
    
    func getConnectedScanners() -> [Device] {
        return scannerConnections.compactMap { (deviceId, connectionInfo) in
            guard connectionInfo.isConnected else { return nil }
            return Device(
                id: deviceId,
                name: connectionInfo.peripheral.name ?? "Unknown Scanner"
            )
        }
    }
    
    func startListening(deviceId: String, onScanResult: @escaping (ScanResult) -> Void) throws {
        guard let connectionInfo = scannerConnections[deviceId], connectionInfo.isConnected else {
            throw NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "Scanner \(deviceId) is not connected"])
        }
        
        guard let readCharacteristic = connectionInfo.readCharacteristic else {
            throw NSError(domain: "ScannerBluetoothError", code: 3, userInfo: [NSLocalizedDescriptionKey: "No read characteristic found for scanner \(deviceId)"])
        }
        
        // Store callback
        dataCallbacks[deviceId] = onScanResult
        
        // Enable notifications
        connectionInfo.peripheral.setNotifyValue(true, for: readCharacteristic)
        scannerConnections[deviceId]?.isListening = true
    }
    
    func stopListening(deviceId: String) throws {
        guard let connectionInfo = scannerConnections[deviceId] else { return }
        
        if let readCharacteristic = connectionInfo.readCharacteristic {
            connectionInfo.peripheral.setNotifyValue(false, for: readCharacteristic)
        }
        
        dataCallbacks.removeValue(forKey: deviceId)
        scannerConnections[deviceId]?.isListening = false
    }
    
    func sendCommand(deviceId: String, command: String) async throws -> String {
        return try await withCheckedThrowingContinuation { continuation in
            guard let connectionInfo = scannerConnections[deviceId], connectionInfo.isConnected else {
                continuation.resume(throwing: NSError(domain: "ScannerBluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "Scanner \(deviceId) is not connected"]))
                return
            }
            
            guard let writeCharacteristic = connectionInfo.writeCharacteristic else {
                continuation.resume(throwing: NSError(domain: "ScannerBluetoothError", code: 3, userInfo: [NSLocalizedDescriptionKey: "No write characteristic found for scanner \(deviceId)"]))
                return
            }
            
            let data = command.data(using: .utf8) ?? Data()
            
            // Store callback for command response
            commandCallbacks[deviceId] = { result in
                switch result {
                case .success(let response):
                    continuation.resume(returning: response)
                case .failure(let error):
                    continuation.resume(throwing: error)
                }
            }
            
            connectionInfo.peripheral.writeValue(data, for: writeCharacteristic, type: .withResponse)
            
            // Set timeout for command response
            DispatchQueue.global().asyncAfter(deadline: .now() + 5.0) {
                if self.commandCallbacks[deviceId] != nil {
                    self.commandCallbacks.removeValue(forKey: deviceId)
                    continuation.resume(throwing: NSError(domain: "ScannerBluetoothError", code: 4, userInfo: [NSLocalizedDescriptionKey: "Command timeout"]))
                }
            }
        }
    }
    
    // MARK: - CBCentralManagerDelegate
    
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOff:
            print("Scanner Bluetooth powered off")
        case .poweredOn:
            print("Scanner Bluetooth powered on")
        case .resetting:
            print("Scanner Bluetooth resetting")
        case .unauthorized:
            print("Scanner Bluetooth unauthorized")
        case .unknown:
            print("Scanner Bluetooth unknown state")
        case .unsupported:
            print("Scanner Bluetooth unsupported")
        @unknown default:
            print("Scanner Bluetooth unknown state")
        }
    }
    
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        print("Discovered scanner: \(peripheral.name ?? "Unknown"), RSSI: \(RSSI)")
        
        if !discoveredPeripherals.contains(where: { $0.identifier == peripheral.identifier }) {
            discoveredPeripherals.append(peripheral)
            
            let devices = discoveredPeripherals.map { peripheral in
                Device(id: peripheral.identifier.uuidString, name: peripheral.name ?? "Unknown Scanner")
            }
            
            onScannerFound?(devices)
        }
    }
    
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        let deviceId = peripheral.identifier.uuidString
        print("Connected to scanner: \(peripheral.name ?? "Unknown")")
        
        peripheral.delegate = self
        
        // Initialize connection info
        scannerConnections[deviceId] = ScannerConnectionInfo(peripheral: peripheral, isConnected: true)
        servicesCheckStatus = [:]
        
        // Discover services
        peripheral.discoverServices(scannerServiceUUIDs)
    }
    
    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        if let error = error {
            connectCallbacks[deviceId]?(.failure(error))
            connectCallbacks.removeValue(forKey: deviceId)
        }
    }
    
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        // Update connection state
        scannerConnections[deviceId]?.isConnected = false
        
        if let error = error {
            disconnectCallbacks[deviceId]?(.failure(error))
            print("Error while disconnecting from scanner \(peripheral.name ?? "Unknown"): \(error.localizedDescription)")
        } else {
            disconnectCallbacks[deviceId]?(.success(true))
            print("Successfully disconnected from scanner \(peripheral.name ?? "Unknown")")
        }
        
        // Clean up callbacks and connection info
        disconnectCallbacks.removeValue(forKey: deviceId)
        dataCallbacks.removeValue(forKey: deviceId)
        commandCallbacks.removeValue(forKey: deviceId)
        scannerConnections.removeValue(forKey: deviceId)
    }
    
    // MARK: - CBPeripheralDelegate
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        if let error = error {
            connectCallbacks[deviceId]?(.failure(error))
            connectCallbacks.removeValue(forKey: deviceId)
            return
        }
        
        guard let services = peripheral.services else { return }
        
        for service in services {
            print("Discovered service: \(service.uuid) for scanner \(deviceId)")
            peripheral.discoverCharacteristics(nil, for: service)
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        if let error = error {
            connectCallbacks[deviceId]?(.failure(error))
            connectCallbacks.removeValue(forKey: deviceId)
            return
        }
        
        guard let characteristics = service.characteristics else { return }
        
        for characteristic in characteristics {
            if readCharacteristicUUIDs.contains(characteristic.uuid) {
                print("Found read characteristic: \(characteristic.uuid) for scanner \(deviceId)")
                scannerConnections[deviceId]?.readCharacteristic = characteristic
            }
            
            if writeCharacteristicUUIDs.contains(characteristic.uuid) {
                print("Found write characteristic: \(characteristic.uuid) for scanner \(deviceId)")
                scannerConnections[deviceId]?.writeCharacteristic = characteristic
            }
        }
        
        // Mark the service as "checked"
        servicesCheckStatus[service.uuid] = true
        print("Finished checking characteristics for service \(service.uuid) on scanner \(deviceId).")
        
        // Check if all services have been checked
        if servicesCheckStatus.values.allSatisfy({ $0 }) {
            let hasReadChar = scannerConnections[deviceId]?.readCharacteristic != nil
            let hasWriteChar = scannerConnections[deviceId]?.writeCharacteristic != nil
            
            print("All services checked for scanner \(deviceId). Read: \(hasReadChar), Write: \(hasWriteChar)")
            
            if hasReadChar && hasWriteChar {
                connectCallbacks[deviceId]?(.success(true))
            } else {
                connectCallbacks[deviceId]?(.failure(NSError(domain: "ScannerBluetoothError", code: 5, userInfo: [NSLocalizedDescriptionKey: "Required characteristics not found"])))
            }
            connectCallbacks.removeValue(forKey: deviceId)
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        if let error = error {
            print("Error reading from scanner \(deviceId): \(error.localizedDescription)")
            return
        }
        
        guard let data = characteristic.value else { return }
        
        // Convert data to string
        if let dataString = String(data: data, encoding: .utf8) {
            print("Received data from scanner \(deviceId): \(dataString)")
            
            // Check if this is a command response or scan data
            if let callback = commandCallbacks[deviceId] {
                callback(.success(dataString))
                commandCallbacks.removeValue(forKey: deviceId)
            } else if let scanCallback = dataCallbacks[deviceId] {
                // Create scan result
                let scanResult = ScanResult(
                    data: dataString,
                    timestamp: ISO8601DateFormatter().string(from: Date()),
                    deviceId: deviceId,
                    deviceName: peripheral.name ?? "Unknown Scanner"
                )
                scanCallback(scanResult)
            }
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        if let error = error {
            print("Error writing to scanner \(deviceId): \(error.localizedDescription)")
            commandCallbacks[deviceId]?(.failure(error))
            commandCallbacks.removeValue(forKey: deviceId)
        }
    }
}