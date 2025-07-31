import { type HybridObject } from 'react-native-nitro-modules'

export interface BlePrintAndScan extends HybridObject<{ android: 'kotlin', ios: 'swift' }> {
  sum(num1: number, num2: number): number
}