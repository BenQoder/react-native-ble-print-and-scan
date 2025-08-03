import React, { useState, useEffect } from 'react';
import { StyleSheet, TouchableOpacity, ScrollView, Alert, View, Text } from 'react-native';
import { BleScanner, type Device } from "react-native-ble-print-and-scan";
import { router } from 'expo-router';

export default function ScannerScreen() {
  const [scanners, setScanners] = useState<Device[]>([]);
  const [isScanning, setIsScanning] = useState(false);
  const [connectedScanners, setConnectedScanners] = useState<Device[]>([]);
  const [isInitialized, setIsInitialized] = useState(false);

  useEffect(() => {
    initializeScanner();
  }, []);

  const initializeScanner = async () => {
    try {
      await BleScanner.initializeScanner();
      setIsInitialized(true);
      Alert.alert('Success', 'Scanner initialized successfully');
    } catch (error) {
      Alert.alert('Error', `Failed to initialize Scanner: ${error}`);
    }
  };

  const scanForScanners = async () => {
    if (!isInitialized) {
      Alert.alert('Error', 'Scanner not initialized');
      return;
    }

    try {
      setIsScanning(true);
      setScanners([]);

      await BleScanner.startScanningForScanners((foundScanners: Device[]) => {
        setScanners(foundScanners);
      });

    } catch (error) {
      setIsScanning(false);
      Alert.alert('Error', `Failed to scan for scanners: ${error}`);
    }
  };

  const stopScanning = async () => {
    try {
      await BleScanner.suspendScanForScanners();
      setIsScanning(false);
    } catch (error) {
      Alert.alert('Error', `Failed to stop scanning: ${error}`);
    }
  };

  const connectToScanner = async (scanner: Device) => {
    try {
      await BleScanner.connectToScanner(scanner.id);
      
      // Update connected scanners list
      const updatedConnectedScanners = await BleScanner.getConnectedScanners();
      setConnectedScanners(updatedConnectedScanners);
      
      Alert.alert('Success', `Connected to ${scanner.name}`);
    } catch (error) {
      Alert.alert('Error', `Failed to connect to ${scanner.name}: ${error}`);
    }
  };

  const disconnectScanner = async (scanner: Device) => {
    try {
      await BleScanner.disconnectFromScanner(scanner.id);
      
      // Update connected scanners list
      const updatedConnectedScanners = await BleScanner.getConnectedScanners();
      setConnectedScanners(updatedConnectedScanners);
      
      
      Alert.alert('Success', `Disconnected from ${scanner.name}`);
    } catch (error) {
      Alert.alert('Error', `Failed to disconnect from ${scanner.name}: ${error}`);
    }
  };

  const disconnectAllScanners = async () => {
    try {
      await BleScanner.disconnectAllScanners();
      setConnectedScanners([]);
      Alert.alert('Success', 'Disconnected from all scanners');
    } catch (error) {
      Alert.alert('Error', `Failed to disconnect all scanners: ${error}`);
    }
  };

  const openScannerScreen = (scanner: Device) => {
    console.log('Navigating to scanner control for:', scanner.name);
    router.push({
      pathname: '/scanner-control',
      params: { deviceId: scanner.id, deviceName: scanner.name }
    });
  };

  return (
    <View style={styles.container}>
      <View style={styles.statusContainer}>
        <Text style={styles.statusText}>
          Status: {isInitialized ? 'Initialized' : 'Not Initialized'}
        </Text>
        <Text style={styles.statusText}>
          Connected Scanners: {connectedScanners.length}
        </Text>
      </View>

      <View style={styles.buttonContainer}>
        <TouchableOpacity
          style={[styles.button, isScanning && styles.buttonDisabled]}
          onPress={scanForScanners}
          disabled={isScanning}
        >
          <Text style={styles.buttonText}>
            {isScanning ? 'Scanning...' : 'Scan for Scanners'}
          </Text>
        </TouchableOpacity>

        {isScanning && (
          <TouchableOpacity style={styles.button} onPress={stopScanning}>
            <Text style={styles.buttonText}>Stop Scanning</Text>
          </TouchableOpacity>
        )}

        {connectedScanners.length > 0 && (
          <TouchableOpacity style={styles.button} onPress={disconnectAllScanners}>
            <Text style={styles.buttonText}>Disconnect All Scanners</Text>
          </TouchableOpacity>
        )}
      </View>

      <ScrollView style={styles.deviceList}>
        <Text style={styles.deviceListTitle}>Discovered Scanners:</Text>
        {scanners.map((scanner, index) => {
          const isConnected = connectedScanners.some(connectedScanner => connectedScanner.id === scanner.id);
          
          return (
            <TouchableOpacity
              key={index}
              style={[
                styles.deviceItem,
                isConnected && styles.connectedDevice
              ]}
              onPress={() => {
                console.log('Scanner tapped:', scanner.name, 'Connected:', isConnected);
                if (isConnected) {
                  openScannerScreen(scanner);
                } else {
                  connectToScanner(scanner);
                }
              }}
              onLongPress={() => {
                if (isConnected) {
                  disconnectScanner(scanner);
                }
              }}
            >
              <View style={styles.deviceInfo}>
                <Text style={styles.deviceName}>{scanner.name}</Text>
                <Text style={styles.deviceId}>{scanner.id}</Text>
              </View>
              <View style={styles.deviceStatus}>
                {isConnected && <Text style={styles.statusBadge}>Connected</Text>}
              </View>
            </TouchableOpacity>
          );
        })}
        
        {scanners.length === 0 && !isScanning && (
          <Text style={styles.noDevicesText}>No scanners found. Start scanning to discover scanners.</Text>
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
    marginBottom: 10,
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