//
//  BluetoothManager.swift
//  Pods
//
//  Created by BenQoder on 04/12/2024.
//

import CoreBluetooth
import Foundation
import NitroModules

struct ConnectionInfo {
    let peripheral: CBPeripheral
    var writableCharacteristics: [CBCharacteristic] = []
    var isConnected: Bool = false
}

class BluetoothManager: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    private var centralManager: CBCentralManager!
    private var discoveredPeripherals: [CBPeripheral] = []
    private var connections: [String: ConnectionInfo] = [:] // deviceId -> ConnectionInfo
    private var mtu: Int = 20
    private let knownWritableUUIDs = [
        CBUUID(string: "00002AF1-0000-1000-8000-00805F9B34FB")
    ]
    
    var onDeviceFound: (([Device]) -> Void)?
    
    let serialQueue: DispatchQueue = DispatchQueue(label: "com.benqoder.bluetooth.serialQueue");
    
    // Connection callbacks per device
    private var connectCallbacks: [String: (Result<Bool, Error>) -> Void] = [:]
    private var disconnectCallbacks: [String: (Result<Bool, Error>) -> Void] = [:]
    
    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }
    
    func isBluetoothSupported() -> Bool {
        return centralManager.state != .unsupported
    }
    
    func isBluetoothEnabled() -> Bool {
        return centralManager.state == .poweredOn
    }
    
    func requestBluetoothPermissions() -> Bool {
        if #available(iOS 13.0, *) {
            return CBManager.authorization == .allowedAlways
        } else {
            return CBPeripheralManager.authorizationStatus() == .authorized
        }
    }
    
    func startScanning(onDeviceFound: @escaping ([Device]) -> Void) {
        self.onDeviceFound = onDeviceFound
        discoveredPeripherals.removeAll()
        centralManager.scanForPeripherals(withServices: nil, options: nil)
    }
    
    func stopScanning() async -> Bool {
        return await withCheckedContinuation { continuation in
            guard centralManager.isScanning else {
                print("Bluetooth scanning is not currently active.")
                continuation.resume(returning: true)
                return
            }
            
            centralManager.stopScan()
            continuation.resume(returning: true)
        }
    }
    
    func connectToDevice(identifier: UUID) async throws -> Bool {
        return try await withCheckedThrowingContinuation { continuation in
            let deviceId = identifier.uuidString
            
            // Check if already connected
            if let connectionInfo = connections[deviceId], connectionInfo.isConnected {
                continuation.resume(returning: true)
                return
            }
            
            guard let peripheral = discoveredPeripherals.first(where: { $0.identifier == identifier }) else {
                continuation.resume(throwing: NSError(domain: "BluetoothError", code: 1, userInfo: [NSLocalizedDescriptionKey: "Could not find peripheral with identifier \(identifier)"]))
                return
            }
            
            print("Connecting to \(peripheral.name ?? "Unknown")...")

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
            guard let connectionInfo = connections[deviceId] else {
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
    
    func disconnectAllDevices() async throws -> Bool {
        var allSuccess = true
        
        for deviceId in Array(connections.keys) {
            do {
                let success = try await disconnect(deviceId: deviceId)
                if !success {
                    allSuccess = false
                }
            } catch {
                allSuccess = false
                print("Failed to disconnect device \(deviceId): \(error)")
            }
        }
        
        return allSuccess
    }
    
    func getAllowedMtu(deviceId: String) -> Int {
        guard let connectionInfo = connections[deviceId], connectionInfo.isConnected else {
            print("No connected device with ID: \(deviceId)")
            return 20
        }
        
        let mtu = connectionInfo.peripheral.maximumWriteValueLength(for: .withResponse)
        return mtu
    }
    
    func isConnected(deviceId: String) -> Bool {
        return connections[deviceId]?.isConnected ?? false
    }
    
    func getConnectedDevices() -> [Device] {
        return connections.compactMap { (deviceId, connectionInfo) in
            guard connectionInfo.isConnected else { return nil }
            return Device(
                id: deviceId,
                name: connectionInfo.peripheral.name ?? "Unknown Device"
            )
        }
    }
    
    func sendRawData(deviceId: String, data: Data) async throws -> Bool {
        return try await printWithDevice(deviceId: deviceId, lines: [data])
    }
    
    // Writing state per device
    private var writingStates: [String: WritingState] = [:]
    
    struct WritingState {
        var currentChunkIndex = 0
        var dataChunks: [Data] = []
        var currentContinuation: CheckedContinuation<Bool, Error>?
        var isWriting = false
    }
    
    private func chunks(from data: Data, chunkSize: Int) -> [Data] {
        var chunks: [Data] = []
        var start = 0
        while start < data.count {
            let end = min(start + chunkSize, data.count)
            chunks.append(data.subdata(in: start..<end))
            start += chunkSize
        }
        return chunks
    }

    func printWithDevice(deviceId: String, lines: [Data]) async throws -> Bool {
        return try await withCheckedThrowingContinuation { continuation in
            // Check if device is connected
            guard let connectionInfo = connections[deviceId], connectionInfo.isConnected else {
                continuation.resume(throwing: NSError(domain: "BluetoothError", code: 3, userInfo: [NSLocalizedDescriptionKey: "No active connection for device \(deviceId)."]))
                return
            }
            
            // Check if already writing to this device
            if writingStates[deviceId]?.isWriting == true {
                continuation.resume(throwing: NSError(domain: "BluetoothError", code: 2, userInfo: [NSLocalizedDescriptionKey: "Another write operation is in progress for device \(deviceId)."]))
                return
            }
            
            // Initialize writing state for this device
            writingStates[deviceId] = WritingState()
            writingStates[deviceId]?.isWriting = true
            writingStates[deviceId]?.currentChunkIndex = 0
            
            let peripheral = connectionInfo.peripheral
            
            print("Writable characteristics: \(connectionInfo.writableCharacteristics.count) found for device \(deviceId).")
            
            let matchingCharacteristics = connectionInfo.writableCharacteristics.filter { characteristic in
                knownWritableUUIDs.contains(characteristic.uuid)
            }

            // Check if there are matching characteristics
            guard let characteristic = matchingCharacteristics.first else {
                continuation.resume(throwing: NSError(domain: "BluetoothError", code: 4, userInfo: [NSLocalizedDescriptionKey: "No writable characteristic found for device \(deviceId)."]))
                return
            }

            let maxWriteLength = peripheral.maximumWriteValueLength(for: .withResponse)
            
            print("Printing to device \(deviceId) with max write length \(maxWriteLength).")
            writingStates[deviceId]?.dataChunks = lines
            writingStates[deviceId]?.currentContinuation = continuation

            // Start writing the first chunk
            writeNextChunk(deviceId: deviceId, peripheral: peripheral, characteristic: characteristic)
        }
    }

    private func writeNextChunk(deviceId: String, peripheral: CBPeripheral, characteristic: CBCharacteristic) {
        guard let writingState = writingStates[deviceId] else { return }
        
        guard writingState.currentChunkIndex < writingState.dataChunks.count else {
            // All chunks written
            print("All data successfully sent to device \(deviceId).")
            writingStates[deviceId]?.currentContinuation?.resume(returning: true)
            writingStates[deviceId]?.isWriting = false
            writingStates[deviceId]?.dataChunks.removeAll()
            writingStates[deviceId]?.currentContinuation = nil
            return
        }

        let chunk = writingState.dataChunks[writingState.currentChunkIndex]
        peripheral.writeValue(chunk, for: characteristic, type: .withResponse)
        // The `didWriteValueFor` callback will be triggered upon completion or error
    }
    
    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        let deviceId = peripheral.identifier.uuidString
        
        if let error = error {
            print("Error while writing to device \(deviceId): \(error.localizedDescription)")
            writingStates[deviceId]?.currentContinuation?.resume(throwing: error)
        } else {
            // Move to next chunk
            writingStates[deviceId]?.currentChunkIndex += 1
            writeNextChunk(deviceId: deviceId, peripheral: peripheral, characteristic: characteristic)
        }
    }
    
    // MARK: - CBCentralManagerDelegate
    
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state != .poweredOn {
            print("Bluetooth is not powered on or unavailable")
        }
    }
    
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        if !discoveredPeripherals.contains(peripheral) {
            if peripheral.name != nil {
                discoveredPeripherals.append(peripheral)
                
                let deviceList: [Device] = discoveredPeripherals.map { peripheral in
                    Device(
                        id: peripheral.identifier.uuidString,
                        name: peripheral.name ?? "Unknown"
                    )
                }
                
                onDeviceFound?(deviceList)
            }
        }
    }
    
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        let deviceId = peripheral.identifier.uuidString
        peripheral.delegate = self
        
        // Initialize connection info
        connections[deviceId] = ConnectionInfo(peripheral: peripheral, writableCharacteristics: [], isConnected: true)
        servicesCheckStatus = [:]
        
        peripheral.discoverServices(nil)
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
        connections[deviceId]?.isConnected = false
        
        if let error = error {
            disconnectCallbacks[deviceId]?(.failure(error))
            print("Error while disconnecting from peripheral \(peripheral.name ?? "Unknown"): \(error.localizedDescription)")
        } else {
            disconnectCallbacks[deviceId]?(.success(true))
            print("Successfully disconnected from peripheral \(peripheral.name ?? "Unknown")")
        }
        
        // Clean up callbacks and connection info
        disconnectCallbacks.removeValue(forKey: deviceId)
        connections.removeValue(forKey: deviceId)
        writingStates.removeValue(forKey: deviceId)
    }
    
    // MARK: - CBPeripheralDelegate

    private var servicesCheckStatus: [CBUUID: Bool] = [:] // Tracks if characteristics of a service are checked
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error = error {
            print("Service discovery error: \(error.localizedDescription)")
            return
        }
        
        guard let services = peripheral.services else { return }   

        for service in services {
            servicesCheckStatus[service.uuid] = false
        }
        
        for service in services {
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
            if characteristic.properties.contains(.write) {
                connections[deviceId]?.writableCharacteristics.append(characteristic)
            }
        }
        
        // Mark the service as "checked"
        servicesCheckStatus[service.uuid] = true
        print("Finished checking characteristics for service \(service.uuid) on device \(deviceId).")
        
        // Check if all services have been checked
        if servicesCheckStatus.values.allSatisfy({ $0 }) {
            let writableCount = connections[deviceId]?.writableCharacteristics.count ?? 0
            print("All services checked (\(writableCount) writable characteristics). Successfully connected to peripheral \(peripheral.name ?? "Unknown").")
            
            connectCallbacks[deviceId]?(.success(true))
            connectCallbacks.removeValue(forKey: deviceId)
        }
    }
}