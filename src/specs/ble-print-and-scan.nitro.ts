import { type HybridObject } from 'react-native-nitro-modules'

export interface Device {
  id: string
  name: string
}

export interface BlePrintAndScan extends HybridObject<{ android: 'kotlin', ios: 'swift' }> {
  // Legacy function (remove when no longer needed)
  sum(num1: number, num2: number): number
  
  // Bluetooth initialization and management
  initializeBluetooth(): Promise<void>
  startScanningForBluetoothDevices(onDeviceFound: (devices: Device[]) => void): Promise<void>
  suspendScanForBluetoothDevices(): Promise<void>
  connectToBluetoothDevice(deviceId: string): Promise<void>
  disconnectFromBluetoothDevice(): Promise<void>
  
  // Thermal printing functions
  generateBytecode(value: string, printerWidth: number, mtuSize: number): Promise<ArrayBuffer[]>
  generateBytecodeBase64(value: string, printerWidth: number, mtuSize: number): Promise<string[]>
  sendToBluetoothThermalPrinter(value: string, printerWidth: number): Promise<void>
  sendToUsbThermalPrinter(value: string, printerWidth: number, chunkSize: number): Promise<void>
}