# React Native BLE Print and Scan

A comprehensive React Native library for Bluetooth Low Energy (BLE) printing and scanning functionality, specifically designed for NATUM devices. This package provides separate modules for both printer and scanner operations with full TypeScript support.

[![Version](https://img.shields.io/npm/v/react-native-ble-print-and-scan.svg)](https://www.npmjs.com/package/react-native-ble-print-and-scan)
[![Downloads](https://img.shields.io/npm/dm/react-native-ble-print-and-scan.svg)](https://www.npmjs.com/package/react-native-ble-print-and-scan)
[![License](https://img.shields.io/npm/l/react-native-ble-print-and-scan.svg)](https://github.com/patrickkabwe/react-native-ble-print-and-scan/LICENSE)

## Features

### 🖨️ Printer Module
- **BLE Connection Management**: Connect to multiple thermal printers simultaneously
- **Receipt Printing**: Print formatted receipts with text, images, and styling
- **Device Discovery**: Scan and discover available BLE printers
- **Connection Status**: Real-time connection monitoring
- **Print Job Management**: Queue and manage multiple print jobs

### 📱 Scanner Module  
- **Multi-Platform Support**: BLE on iOS, Classic Bluetooth on Android
- **Real-time Scanning**: Listen for barcode/QR code scan results
- **Scanner Configuration**: Configure scan modes, beep settings, power management
- **Multi-Scanner Support**: Connect and manage multiple scanners
- **Data Management**: Handle stored scan data and timestamps

## Requirements

### System Requirements
- React Native v0.76.0 or higher
- Node 18.0.0 or higher
- iOS 11.0+
- Android API Level 23+
- TypeScript support recommended

> [!IMPORTANT]  
> To Support `Nitro Views` you need to install React Native version v0.78.0 or higher.

### Permissions

#### iOS
Add to `Info.plist`:
```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app uses Bluetooth to connect to printers and scanners</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>This app uses Bluetooth to connect to printers and scanners</string>
```

#### Android
Add to `android/app/src/main/AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- For Android 12+ -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
```

## Installation

```bash
npm install react-native-ble-print-and-scan react-native-nitro-modules
```

### iOS Setup
```bash
cd ios && pod install
```

### Android Setup
No additional setup required for Android.

## Usage

### Printer Module

#### Basic Setup
```typescript  
import { BlePrinter, type Device } from 'react-native-ble-print-and-scan';

// Initialize the printer module
await BlePrinter.initializePrinter();

// Start scanning for printers
await BlePrinter.startScanningForPrinters((devices: Device[]) => {
  console.log('Found printers:', devices);
});

// Connect to a printer
await BlePrinter.connectToPrinter(deviceId);

// Check connection status
const isConnected = await BlePrinter.isPrinterConnected(deviceId);
```

#### Printing Operations
```typescript
// Print using base64 image data (most common approach)
// Convert your receipt content to a base64 image first, then:
const base64ImageData = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==";
const printerWidth = 384; // Standard thermal printer width in pixels

await BlePrinter.sendToBluetoothThermalPrinter(deviceId, base64ImageData, printerWidth);

// For USB printers
await BlePrinter.sendToUsbThermalPrinter(base64ImageData, printerWidth, 1024);

// Generate bytecode for advanced use cases
const bytecodeChunks = await BlePrinter.generateBytecode(base64ImageData, printerWidth, 512);
const base64Chunks = await BlePrinter.generateBytecodeBase64(base64ImageData, printerWidth, 512);

// Paper control functions
await BlePrinter.feedPaper(deviceId, 3); // Feed 3 lines
await BlePrinter.cutPaper(deviceId); // Cut the paper
```

#### Multi-Printer Management
```typescript
// Get all connected printers
const connectedPrinters = await BlePrinter.getConnectedPrinters();

// Disconnect specific printer
await BlePrinter.disconnectFromPrinter(deviceId);

// Disconnect all printers
await BlePrinter.disconnectAllPrinters();

// Stop scanning
await BlePrinter.suspendScanForPrinters();
```

### Scanner Module

#### Basic Setup
```typescript
import { BleScanner, type ScanResult, ScannerMode } from 'react-native-ble-print-and-scan';

// Initialize the scanner module
await BleScanner.initializeScanner();

// Start scanning for scanners
await BleScanner.startScanningForScanners((devices: Device[]) => {
  console.log('Found scanners:', devices);
});

// Connect to a scanner
await BleScanner.connectToScanner(deviceId);
```

#### Scan Operations
```typescript
// Start listening for scan results
await BleScanner.startListening(deviceId, (result: ScanResult) => {
  console.log('Scanned:', result.data);
  console.log('Timestamp:', result.timestamp);
  console.log('Device:', result.deviceName);
});

// Trigger a manual scan (1-7 seconds)
await BleScanner.triggerScan(deviceId, ScannerTrigger.SOFT_TRIGGER_3S);

// Stop listening
await BleScanner.stopListening(deviceId);
```

#### Scanner Configuration
```typescript
// Set scanner mode
await BleScanner.setScannerMode(deviceId, ScannerMode.HOST_TRIGGER);

// Configure beep settings
await BleScanner.setBeepSettings(deviceId, {
  volume: BeepVolume.MIDDLE,
  tone: BeepTone.HIGH_TONE,
  enabled: true
});

// Set power settings
await BleScanner.setPowerSettings(deviceId, {
  sleepTimeMinutes: 30,
  autoSleepEnabled: true
});

// Configure data format
await BleScanner.setDataFormatSettings(deviceId, {
  prefixEnabled: false,
  suffixEnabled: true,
  hideBarcodePrefix: false,
  hideBarcodeContent: false,
  hideBarcodeSuffix: false
});
```

#### Advanced Scanner Operations
```typescript
// Get scanner information
const info = await BleScanner.getScannerInfo(deviceId);
console.log('Firmware:', info.firmwareVersion);
console.log('Battery:', info.batteryLevel);

// Custom beep (level 0-26)
await BleScanner.customBeep(deviceId, 15);

// Custom beep with timing
await BleScanner.customBeepTime(deviceId, 500, 0, 2000); // 500ms, beep+vibration, 2000Hz

// Data management
const storedCount = await BleScanner.getStoredDataCount(deviceId);
const storedData = await BleScanner.uploadStoredData(deviceId, true); // true = clear after upload
await BleScanner.clearStoredData(deviceId);

// Factory reset
await BleScanner.restoreFactorySettings(deviceId);

// Send raw commands
const response = await BleScanner.sendRawCommand(deviceId, "$SW#VER");
```

## Scanner Modes

| Mode | Description | Command |
|------|-------------|---------|
| Key Hold | Button must be held down to scan | `%SCMD#00#` |
| Continuous | Continuous scanning until stopped | `%SCMD#01#` |  
| Key Pulse | Single button press triggers scan | `%SCMD#02#` |
| Host Trigger | App-controlled scanning (default) | `%SCMD#03#` |

## NATUM Device Configuration

### Device Setup Commands
These commands should be sent directly to NATUM devices for initial configuration:

#### Factory Reset
```
%#IFSN0$B
```
Resets the device to factory default settings.

#### Bluetooth Connection Mode
```
%#IFSN0$4
```
Enables Bluetooth connection mode on the device.

#### Platform-Specific Connection Commands

##### iOS Connection Mode
```
AT+MODE=3
```
Configures the device for iOS BLE connection.

##### Android Connection Mode  
```
AT+MODE=1
```
Configures the device for Android Classic Bluetooth connection.

### Important Notes
- Send these commands directly to the device via terminal or manufacturer software
- These are one-time setup commands, not part of the regular app workflow
- Factory reset will erase all custom configurations
- Always configure the appropriate connection mode for your target platform

## API Reference

### Printer Module (BlePrinter)

#### Connection Management
- `initializePrinter(): Promise<void>`
- `startScanningForPrinters(callback): Promise<void>`
- `suspendScanForPrinters(): Promise<void>`
- `connectToPrinter(deviceId): Promise<void>`
- `disconnectFromPrinter(deviceId): Promise<void>`
- `isPrinterConnected(deviceId): Promise<boolean>`
- `getConnectedPrinters(): Promise<Device[]>`
- `disconnectAllPrinters(): Promise<void>`

#### Printing Operations
- `sendToBluetoothThermalPrinter(deviceId, base64Data, printerWidth): Promise<void>`
- `sendToUsbThermalPrinter(base64Data, printerWidth, chunkSize): Promise<void>`
- `generateBytecode(base64Data, printerWidth, mtuSize): Promise<ArrayBuffer[]>`
- `generateBytecodeBase64(base64Data, printerWidth, mtuSize): Promise<string[]>`
- `feedPaper(deviceId, lines): Promise<void>`
- `cutPaper(deviceId): Promise<void>`

### Scanner Module (BleScanner)

#### Connection Management
- `initializeScanner(): Promise<void>`
- `startScanningForScanners(callback): Promise<void>`
- `suspendScanForScanners(): Promise<void>`
- `connectToScanner(deviceId): Promise<void>`
- `disconnectFromScanner(deviceId): Promise<void>`
- `isScannerConnected(deviceId): Promise<boolean>`
- `getConnectedScanners(): Promise<Device[]>`
- `disconnectAllScanners(): Promise<void>`

#### Scanner Information
- `getScannerInfo(deviceId): Promise<ScannerInfo>`
- `getScannerSettings(deviceId): Promise<ScannerCurrentSettings>`

#### Scan Operations
- `startListening(deviceId, callback): Promise<void>`
- `stopListening(deviceId): Promise<void>`
- `triggerScan(deviceId, duration): Promise<void>`

#### Configuration
- `setScannerMode(deviceId, mode): Promise<void>`
- `setBeepSettings(deviceId, settings): Promise<void>`
- `setPowerSettings(deviceId, settings): Promise<void>`
- `setDataFormatSettings(deviceId, settings): Promise<void>`
- `setTimestamp(deviceId, format, datetime?): Promise<void>`

#### Advanced Operations
- `restoreFactorySettings(deviceId): Promise<void>`
- `customBeep(deviceId, level): Promise<void>`
- `customBeepTime(deviceId, timeMs, type, frequencyHz): Promise<void>`
- `powerOff(deviceId): Promise<void>`
- `getStoredDataCount(deviceId): Promise<number>`
- `uploadStoredData(deviceId, clearAfterUpload): Promise<ScanResult[]>`
- `clearStoredData(deviceId): Promise<void>`
- `sendRawCommand(deviceId, command): Promise<string>`

## Type Definitions

### Common Types
```typescript
interface Device {
  id: string;
  name: string;
}

interface ScanResult {
  data: string;
  timestamp: string;
  deviceId: string;
  deviceName: string;
}
```

### Scanner-Specific Types
```typescript
enum ScannerMode {
  KEY_HOLD = 'KEY_HOLD',
  CONTINUOUS = 'CONTINUOUS', 
  KEY_PULSE = 'KEY_PULSE',
  HOST_TRIGGER = 'HOST_TRIGGER'
}

enum BeepVolume {
  MUTE = 'MUTE',
  LOW = 'LOW',
  MIDDLE = 'MIDDLE',
  HIGH = 'HIGH'
}

enum BeepTone {
  HIGH_TONE = 'HIGH_TONE',
  LOW_TONE = 'LOW_TONE'
}

enum ScannerTrigger {
  SOFT_TRIGGER_1S = 'SOFT_TRIGGER_1S',
  SOFT_TRIGGER_2S = 'SOFT_TRIGGER_2S',
  SOFT_TRIGGER_3S = 'SOFT_TRIGGER_3S',
  SOFT_TRIGGER_4S = 'SOFT_TRIGGER_4S',
  SOFT_TRIGGER_5S = 'SOFT_TRIGGER_5S',
  SOFT_TRIGGER_6S = 'SOFT_TRIGGER_6S',
  SOFT_TRIGGER_7S = 'SOFT_TRIGGER_7S'
}
```

## Troubleshooting

### Common Issues

#### Device Not Found
- Ensure Bluetooth is enabled on the device
- Check that the device is in pairing/discoverable mode
- Verify all required permissions are granted
- For Android, ensure location services are enabled

#### Connection Failed
- Try moving closer to the device
- Reset the device using the factory reset command
- Restart the app and try again
- Check that the device isn't already connected to another app

#### Printing Issues
- Verify the printer has paper and is ready
- Check printer connection status before printing
- Ensure the print data is properly formatted
- Try printing a test page first

#### Scanning Issues  
- Configure the scanner to the correct mode (HOST_TRIGGER recommended)
- Ensure the scanner is properly connected before starting to listen
- Check that scan results callback is properly set up
- Verify the barcode/QR code is clear and readable

#### Android-Specific Issues
- Grant location permissions for Bluetooth scanning
- Ensure Classic Bluetooth is enabled for scanner connections
- Check that device names are not filtered out (must have valid names)

#### iOS-Specific Issues
- Ensure BLE permissions are granted in Settings
- Check that the device supports BLE connections
- Verify Core Bluetooth framework availability

### Debug Tips
- Enable console logging to see connection status and errors
- Use `getScannerInfo()` to verify device firmware and status
- Test with known working barcodes/QR codes first
- Check device documentation for specific command requirements

## 📱 Example App & Trigger Mode Demos

The included example app provides comprehensive demonstrations of all scanner trigger modes:

### Available Trigger Mode Examples

1. **Host Trigger Mode** (`/trigger-modes/host-trigger`)
   - Programmatic scan control via app commands
   - Multiple trigger durations (1s, 2s, 3s, 5s)
   - Perfect for automation and app-guided workflows
   - Real-time scan result display

2. **Key Hold Mode** (`/trigger-modes/key-hold`)
   - Continuous scanning while physical button is held
   - Manual control with immediate feedback
   - Ideal for bulk scanning operations

3. **Key Pulse Mode** (`/trigger-modes/key-pulse`)
   - Single scan per button press
   - Prevents duplicate scans
   - Perfect for precise inventory management

4. **Continuous Mode** (`/trigger-modes/continuous`)
   - Automatic scanning when barcode is detected
   - No button interaction required
   - Ideal for high-volume, hands-free operations

### Running the Examples

```bash
cd example
npm install
npx expo run:ios    # or npx expo run:android
```

Navigate to the "Trigger Modes" tab to explore each scanning mode with:
- Step-by-step setup instructions
- Real-time configuration options
- Live scan result display
- Best practice recommendations
- Use case scenarios

### Example Features
- **Interactive Configuration**: Toggle beep settings, scan modes, and connection options
- **Real-time Results**: See scan results immediately with timestamps
- **Connection Management**: Easy scanner discovery and connection
- **Mode Switching**: Compare different trigger modes in the same app
- **Comprehensive Documentation**: In-app explanations and usage tips

## Support

For issues related to:
- **Device compatibility**: Check NATUM device documentation
- **Bluetooth permissions**: Review platform-specific permission requirements  
- **Connection problems**: Follow troubleshooting guide above
- **API usage**: Refer to the comprehensive examples in this README

## Credits

Bootstrapped with [create-nitro-module](https://github.com/patrickkabwe/create-nitro-module).

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## License

This project is licensed under the MIT License.
