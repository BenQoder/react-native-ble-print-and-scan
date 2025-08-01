import React, { useState, useEffect } from 'react';
import { StyleSheet, TouchableOpacity, ScrollView, Alert, Image, Modal, View, Text } from 'react-native';
import * as FileSystem from 'expo-file-system';
import { BlePrintAndScan, type Device } from "react-native-ble-print-and-scan";
import RecieptDOM from '@/components/RecieptDOM';

export default function HomeScreen() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [isScanning, setIsScanning] = useState(false);
  const [connectedDevice, setConnectedDevice] = useState<Device | null>(null);
  const [isInitialized, setIsInitialized] = useState(false);
  const [showPreviewModal, setShowPreviewModal] = useState(false);
  const [cachedScreenshot, setCachedScreenshot] = useState<string | null>(null);

  // Callback function to receive screenshot from DOM component
  const handleScreenshotReady = async (screenshot: string) => {
    setCachedScreenshot(screenshot);
  };

  useEffect(() => {
    initializeBluetooth();
  }, []);

  const initializeBluetooth = async () => {
    try {
      await BlePrintAndScan.initializeBluetooth();
      setIsInitialized(true);
      Alert.alert('Success', 'Bluetooth initialized successfully');
    } catch (error) {
      Alert.alert('Error', `Failed to initialize Bluetooth: ${error}`);
    }
  };

  const scanForDevices = async () => {
    if (!isInitialized) {
      Alert.alert('Error', 'Bluetooth not initialized');
      return;
    }

    try {
      setIsScanning(true);
      setDevices([]);

      await BlePrintAndScan.startScanningForBluetoothDevices((foundDevices: Device[]) => {
        setDevices(foundDevices);
      });

    } catch (error) {
      setIsScanning(false);
      Alert.alert('Error', `Failed to scan for devices: ${error}`);
    }
  };

  const stopScanning = async () => {
    try {
      await BlePrintAndScan.suspendScanForBluetoothDevices();
      setIsScanning(false);
    } catch (error) {
      Alert.alert('Error', `Failed to stop scanning: ${error}`);
    }
  };

  const connectToDevice = async (device: Device) => {
    try {
      await BlePrintAndScan.connectToBluetoothDevice(device.id);
      setConnectedDevice(device);
      Alert.alert('Success', `Connected to ${device.name}`);
    } catch (error) {
      Alert.alert('Error', `Failed to connect to ${device.name}: ${error}`);
    }
  };

  const disconnectDevice = async () => {
    try {
      await BlePrintAndScan.disconnectFromBluetoothDevice();
      setConnectedDevice(null);
      Alert.alert('Success', 'Disconnected from device');
    } catch (error) {
      Alert.alert('Error', `Failed to disconnect: ${error}`);
    }
  };

  const printReceipt = async () => {
    if (!connectedDevice) {
      Alert.alert('Error', 'No device connected');
      return;
    }

    if (!cachedScreenshot) {
      Alert.alert('Error', 'Screenshot not ready yet. Please try again in a moment.');
      return;
    }

    try {
      console.log("Using cached screenshot, length:", cachedScreenshot.length);

      // Extract only the base64 data part (remove the "data:image/png;base64," prefix)
      const base64Data = cachedScreenshot.split(',')[1];
      console.log("Base64 data only, length:", base64Data.length);

      await BlePrintAndScan.sendToBluetoothThermalPrinter(base64Data, 384); // Standard thermal printer width
      Alert.alert('Success', 'Receipt printed successfully');
    } catch (error) {
      console.error('Error printing receipt:', error);
      Alert.alert('Error', 'Failed to print receipt');
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>BLE Print & Scan Example</Text>
      <Text style={styles.subtitle}>Sum Test: {BlePrintAndScan.sum(1, 2)}</Text>

      <View style={styles.statusContainer}>
        <Text style={styles.statusText}>
          Status: {isInitialized ? 'Initialized' : 'Not Initialized'}
        </Text>
        {connectedDevice && (
          <Text style={styles.statusText}>
            Connected: {connectedDevice.name}
          </Text>
        )}
      </View>

      <View style={styles.buttonContainer}>
        <TouchableOpacity
          style={[styles.button, isScanning && styles.buttonDisabled]}
          onPress={scanForDevices}
          disabled={isScanning}
        >
          <Text style={styles.buttonText}>
            {isScanning ? 'Scanning...' : 'Scan for Devices'}
          </Text>
        </TouchableOpacity>

        {isScanning && (
          <TouchableOpacity style={styles.button} onPress={stopScanning}>
            <Text style={styles.buttonText}>Stop Scanning</Text>
          </TouchableOpacity>
        )}

        {connectedDevice && (
          <>
            <TouchableOpacity style={styles.button} onPress={disconnectDevice}>
              <Text style={styles.buttonText}>Disconnect</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={styles.button}
              onPress={() => setShowPreviewModal(true)}
            >
              <Text style={styles.buttonText}>Print Preview Test Receipt</Text>
            </TouchableOpacity>
          </>
        )}
      </View>

      <Modal
        visible={showPreviewModal}
        animationType="slide"
        presentationStyle="pageSheet"
        onRequestClose={() => setShowPreviewModal(false)}
      >
        <View style={styles.modalContainer}>
          <View style={styles.modalHeader}>
            <Text style={styles.modalTitle}>Receipt Preview</Text>
            <TouchableOpacity
              style={styles.closeButton}
              onPress={() => setShowPreviewModal(false)}
            >
              <Text style={styles.closeButtonText}>Close</Text>
            </TouchableOpacity>
          </View>

          <TouchableOpacity style={styles.button} onPress={printReceipt}>
            <Text style={styles.buttonText}>Print Receipt</Text>
          </TouchableOpacity>
          <View style={styles.previewBox}>
            <RecieptDOM dom={{ matchContents: true }} onScreenshotReady={handleScreenshotReady} />
          </View>
        </View>
      </Modal>

      <ScrollView style={styles.deviceList}>
        <Text style={styles.deviceListTitle}>Discovered Devices:</Text>
        {devices.map((device, index) => (
          <TouchableOpacity
            key={index}
            style={[
              styles.deviceItem,
              connectedDevice?.id === device.id && styles.connectedDevice
            ]}
            onPress={() => connectToDevice(device)}
          >
            <Text style={styles.deviceName}>{device.name}</Text>
            <Text style={styles.deviceId}>{device.id}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    backgroundColor: '#f5f5f5',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 10,
    marginTop: 40,
  },
  subtitle: {
    fontSize: 16,
    textAlign: 'center',
    marginBottom: 20,
    color: 'green',
  },
  statusContainer: {
    backgroundColor: 'white',
    padding: 15,
    borderRadius: 8,
    marginBottom: 20,
  },
  statusText: {
    fontSize: 16,
    marginBottom: 5,
  },
  buttonContainer: {
    marginBottom: 20,
  },
  button: {
    backgroundColor: '#007AFF',
    padding: 15,
    borderRadius: 8,
    marginBottom: 10,
  },
  buttonDisabled: {
    backgroundColor: '#cccccc',
  },
  buttonText: {
    color: 'white',
    textAlign: 'center',
    fontSize: 16,
    fontWeight: '600',
  },
  previewContainer: {
    height: 300,
    paddingBottom: 20,
    backgroundColor: '#f0f0f0',
    borderRadius: 8,
    padding: 10,
    marginBottom: 20,
  },
  previewTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 10,
  },
  previewBox: {
    borderWidth: 1,
    borderColor: '#ccc',
    height: "100%",
  },
  deviceList: {
    flex: 1,
    backgroundColor: 'white',
    borderRadius: 8,
    padding: 10,
  },
  deviceListTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 10,
  },
  deviceItem: {
    padding: 15,
    borderBottomWidth: 1,
    borderBottomColor: '#eeeeee',
  },
  connectedDevice: {
    backgroundColor: '#e8f5e8',
  },
  deviceName: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 5,
  },
  deviceId: {
    fontSize: 12,
    color: '#666666',
  },
  modalContainer: {
    flex: 1,
    backgroundColor: '#f0f0f0',
    padding: 20,
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20,
    paddingBottom: 10,
    borderBottomWidth: 1,
    borderBottomColor: '#ccc',
  },
  modalTitle: {
    fontSize: 20,
    fontWeight: 'bold',
  },
  closeButton: {
    backgroundColor: '#007AFF',
    paddingHorizontal: 15,
    paddingVertical: 8,
    borderRadius: 8,
  },
  closeButtonText: {
    color: 'white',
    fontWeight: '600',
  },
});
