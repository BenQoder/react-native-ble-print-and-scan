import { type HybridObject } from 'react-native-nitro-modules'

export interface Device {
  id: string
  name: string
}

export interface BlePrinter extends HybridObject<{ android: 'kotlin', ios: 'swift' }> {
  // Bluetooth connection management
  initializePrinter(): Promise<void>
  startScanningForPrinters(onDeviceFound: (devices: Device[]) => void): Promise<void>
  suspendScanForPrinters(): Promise<void>
  connectToPrinter(deviceId: string): Promise<void>
  disconnectFromPrinter(deviceId: string): Promise<void>
  
  // Connection status and management
  isPrinterConnected(deviceId: string): Promise<boolean>
  getConnectedPrinters(): Promise<Device[]>
  disconnectAllPrinters(): Promise<void>
  
  // Thermal printing functions
  generateBytecode(value: string, printerWidth: number, mtuSize: number): Promise<ArrayBuffer[]>
  generateBytecodeBase64(value: string, printerWidth: number, mtuSize: number): Promise<string[]>
  sendToBluetoothThermalPrinter(deviceId: string, value: string, printerWidth: number): Promise<void>
  sendToUsbThermalPrinter(value: string, printerWidth: number, chunkSize: number): Promise<void>
  
  // Paper control functions
  feedPaper(deviceId: string, lines: number): Promise<void>
  cutPaper(deviceId: string): Promise<void>
}