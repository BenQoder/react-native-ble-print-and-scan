import React, { useState, useEffect } from 'react';
import { StyleSheet, TouchableOpacity, ScrollView, Alert, View, Text } from 'react-native';
import { BlePrinter, type Device } from "react-native-ble-print-and-scan";
import { router } from 'expo-router';

export default function HomeScreen() {
  const [devices, setDevices] = useState<Device[]>([]);
  const [isScanning, setIsScanning] = useState(false);
  const [connectedDevices, setConnectedDevices] = useState<Device[]>([]);
  const [isInitialized, setIsInitialized] = useState(false);

  useEffect(() => {
    initializePrinter();
  }, []);

  const initializePrinter = async () => {
    try {
      await BlePrinter.initializePrinter();
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

      await BlePrinter.startScanningForPrinters((foundDevices: Device[]) => {
        setDevices(foundDevices);
      });

    } catch (error) {
      setIsScanning(false);
      Alert.alert('Error', `Failed to scan for devices: ${error}`);
    }
  };

  const stopScanning = async () => {
    try {
      await BlePrinter.suspendScanForPrinters();
      setIsScanning(false);
    } catch (error) {
      Alert.alert('Error', `Failed to stop scanning: ${error}`);
    }
  };

  const connectToDevice = async (device: Device) => {
    try {
      await BlePrinter.connectToPrinter(device.id);
      
      // Update connected devices list
      const updatedConnectedDevices = await BlePrinter.getConnectedPrinters();
      setConnectedDevices(updatedConnectedDevices);
      
      Alert.alert('Success', `Connected to ${device.name}`);
    } catch (error) {
      Alert.alert('Error', `Failed to connect to ${device.name}: ${error}`);
    }
  };

  const disconnectDevice = async (device: Device) => {
    try {
      await BlePrinter.disconnectFromBluetoothDevice(device.id);
      
      // Update connected devices list
      const updatedConnectedDevices = await BlePrinter.getConnectedPrinters();
      setConnectedDevices(updatedConnectedDevices);
      
      
      Alert.alert('Success', `Disconnected from ${device.name}`);
    } catch (error) {
      Alert.alert('Error', `Failed to disconnect from ${device.name}: ${error}`);
    }
  };

  const disconnectAllDevices = async () => {
    try {
      await BlePrinter.disconnectAllDevices();
      setConnectedDevices([]);
      Alert.alert('Success', 'Disconnected from all devices');
    } catch (error) {
      Alert.alert('Error', `Failed to disconnect all devices: ${error}`);
    }
  };

  const openPrinterScreen = (device: Device) => {
    router.push({
      pathname: '/printer',
      params: { deviceId: device.id, deviceName: device.name }
    });
  };

  return (
    <View style={styles.container}>

      <View style={styles.statusContainer}>
        <Text style={styles.statusText}>
          Status: {isInitialized ? 'Initialized' : 'Not Initialized'}
        </Text>
        <Text style={styles.statusText}>
          Connected Devices: {connectedDevices.length}
        </Text>
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

        {connectedDevices.length > 0 && (
          <TouchableOpacity style={styles.button} onPress={disconnectAllDevices}>
            <Text style={styles.buttonText}>Disconnect All Devices</Text>
          </TouchableOpacity>
        )}
      </View>

      <ScrollView style={styles.deviceList}>
        <Text style={styles.deviceListTitle}>Discovered Devices:</Text>
        {devices.map((device, index) => {
          const isConnected = connectedDevices.some(connectedDevice => connectedDevice.id === device.id);
          
          return (
            <TouchableOpacity
              key={index}
              style={[
                styles.deviceItem,
                isConnected && styles.connectedDevice
              ]}
              onPress={() => {
                if (isConnected) {
                  // If connected, open printer screen
                  openPrinterScreen(device);
                } else {
                  // If not connected, connect to device
                  connectToDevice(device);
                }
              }}
              onLongPress={() => {
                if (isConnected) {
                  // Long press to disconnect
                  disconnectDevice(device);
                }
              }}
            >
              <View style={styles.deviceInfo}>
                <Text style={styles.deviceName}>{device.name}</Text>
                <Text style={styles.deviceId}>{device.id}</Text>
              </View>
              <View style={styles.deviceStatus}>
                {isConnected && <Text style={styles.statusBadge}>Connected</Text>}
              </View>
            </TouchableOpacity>
          );
        })}
        
        {devices.length === 0 && !isScanning && (
          <Text style={styles.noDevicesText}>No devices found. Start scanning to discover devices.</Text>
        )}
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
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 15,
    borderBottomWidth: 1,
    borderBottomColor: '#eeeeee',
  },
  connectedDevice: {
    backgroundColor: '#e8f5e8',
  },
  deviceInfo: {
    flex: 1,
  },
  deviceStatus: {
    flexDirection: 'row',
    gap: 5,
  },
  statusBadge: {
    fontSize: 10,
    color: '#007AFF',
    backgroundColor: '#e8f0ff',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
    overflow: 'hidden',
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
  noDevicesText: {
    textAlign: 'center',
    color: '#666666',
    marginTop: 20,
    fontStyle: 'italic',
  },
});
