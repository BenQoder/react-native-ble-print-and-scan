import { NitroModules } from 'react-native-nitro-modules'
import type { BlePrintAndScan as BlePrintAndScanSpec, Device } from './specs/ble-print-and-scan.nitro'
import type { BleScanner as BleScannerSpec } from './specs/ble-scanner.nitro'

export const BlePrintAndScan =
  NitroModules.createHybridObject<BlePrintAndScanSpec>('BlePrintAndScan')

export const BleScanner =
  NitroModules.createHybridObject<BleScannerSpec>('BleScanner')

export type { Device }
export * from './specs/ble-scanner.nitro'