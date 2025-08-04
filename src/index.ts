import { NitroModules } from 'react-native-nitro-modules'
import type { BlePrinter as BlePrinterSpec } from './specs/ble-printer.nitro'
import type { BleScanner as BleScannerSpec } from './specs/ble-scanner.nitro'

export const BlePrinter =
  NitroModules.createHybridObject<BlePrinterSpec>('BlePrinter')

export const BleScanner =
  NitroModules.createHybridObject<BleScannerSpec>('BleScanner')

export type { Device } from './specs/ble-printer.nitro'
export * from './specs/ble-printer.nitro'
export * from './specs/ble-scanner.nitro'