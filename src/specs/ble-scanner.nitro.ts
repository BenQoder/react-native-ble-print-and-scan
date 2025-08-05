import type { HybridObject } from 'react-native-nitro-modules'

// Scanner-specific types
export interface ScanResult {
  data: string
  timestamp: string
  deviceId: string
  deviceName: string
}


export enum ScannerMode {
  KEY_HOLD = 0,           // Button hold scan mode
  CONTINUOUS = 1,         // Continuous scan mode  
  KEY_PULSE = 2,          // Button pulse scan mode
  HOST_TRIGGER = 3        // Host trigger mode
}

export enum BeepVolume {
  MUTE = 0,
  LOW = 1, 
  MIDDLE = 2,
  HIGH = 3
}

export enum BeepTone {
  HIGH_TONE = 0,
  LOW_TONE = 1
}

export interface BeepSettings {
  volume: BeepVolume
  tone: BeepTone
  enabled: boolean
  customLevel?: number // 0-26 for custom beep
}

export interface PowerSettings {
  sleepTimeMinutes: number // 0 = never sleep, 1, 3, 5, 10, 30, 60, 120
  autoSleepEnabled: boolean
}

export interface DataFormatSettings {
  prefixEnabled: boolean
  suffixEnabled: boolean
  hideBarcodePrefix: boolean
  hideBarcodeContent: boolean
  hideBarcodeSuffix: boolean
}

export enum TimestampFormat {
  DISABLED = 0,
  DATE_TIME = 1,          // YY/MM/DD,HH:MM:SS format
  UNIX_TIMESTAMP = 2      // 10-digit timestamp
}

export enum ScannerTrigger {
  SOFT_TRIGGER_1S = 1,
  SOFT_TRIGGER_2S = 2,
  SOFT_TRIGGER_3S = 3,
  SOFT_TRIGGER_4S = 4,
  SOFT_TRIGGER_5S = 5,
  SOFT_TRIGGER_6S = 6,
  SOFT_TRIGGER_7S = 7
}

export interface BleScanner extends HybridObject<{ android: 'kotlin', ios: 'swift' }> {
  // HybridObject lifecycle - required by Nitro bridge
  dispose(): void
  
  // Scanner connection management
  initializeScanner(): Promise<void>
  startScanningForScanners(onScannerFound: (devices: Device[]) => void): Promise<void>
  suspendScanForScanners(): Promise<void>
  connectToScanner(deviceId: string): Promise<void>
  disconnectFromScanner(deviceId: string): Promise<void>
  
  // Multi-scanner management
  isScannerConnected(deviceId: string): Promise<boolean>
  getConnectedScanners(): Promise<Device[]>
  disconnectAllScanners(): Promise<void>
  
  
  // Scan operations
  startListening(deviceId: string, onScanResult: (result: ScanResult) => void): Promise<void>
  stopListening(deviceId: string): Promise<void>
  triggerScan(deviceId: string, duration: ScannerTrigger): Promise<void>
  
  // Scanner configuration
  setScannerMode(deviceId: string, mode: ScannerMode): Promise<void>
  setBeepSettings(deviceId: string, settings: BeepSettings): Promise<void>
  setPowerSettings(deviceId: string, settings: PowerSettings): Promise<void>
  setDataFormatSettings(deviceId: string, settings: DataFormatSettings): Promise<void>
  setTimestamp(deviceId: string, format: TimestampFormat, datetime?: string): Promise<void>
  
  // Advanced commands
  restoreFactorySettings(deviceId: string): Promise<void>
  customBeep(deviceId: string, level: number): Promise<void>
  customBeepTime(deviceId: string, timeMs: number, type: number, frequencyHz: number): Promise<void>
  powerOff(deviceId: string): Promise<void>
  
  // Data management (for store mode)
  getStoredDataCount(deviceId: string): Promise<number>
  uploadStoredData(deviceId: string, clearAfterUpload: boolean): Promise<ScanResult[]>
  clearStoredData(deviceId: string): Promise<void>
  
  // Raw command interface for advanced usage
  sendRawCommand(deviceId: string, command: string): Promise<string>
}

// Re-export Device from the main spec
export interface Device {
  id: string
  name: string
}