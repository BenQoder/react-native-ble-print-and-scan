import { NitroModules } from 'react-native-nitro-modules'
import type { BlePrintAndScan as BlePrintAndScanSpec } from './specs/ble-print-and-scan.nitro'

export const BlePrintAndScan =
  NitroModules.createHybridObject<BlePrintAndScanSpec>('BlePrintAndScan')