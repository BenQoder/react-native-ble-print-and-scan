import { NitroModules } from 'react-native-nitro-modules'
import type { BlePrintAndScan as BlePrintAndScanSpec, Device } from './specs/ble-print-and-scan.nitro'

export const BlePrintAndScan =
  NitroModules.createHybridObject<BlePrintAndScanSpec>('BlePrintAndScan')

export type { Device }