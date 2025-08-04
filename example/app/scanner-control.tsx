import React, { useState } from 'react';
import { StyleSheet, TouchableOpacity, ScrollView, Alert, View, Text } from 'react-native';
import { BleScanner, type ScanResult, ScannerMode, BeepVolume, BeepTone, ScannerTrigger } from "react-native-ble-print-and-scan";
import { useLocalSearchParams, router } from 'expo-router';

export default function ScannerScreen() {
  const { deviceId, deviceName } = useLocalSearchParams<{ deviceId: string; deviceName: string }>();
  const [scanResults, setScanResults] = useState<ScanResult[]>([]);
  const [isListening, setIsListening] = useState(false);

  const startListening = async () => {
    if (!deviceId) {
      Alert.alert('Error', 'No scanner selected');
      return;
    }

    try {
      await BleScanner.startListening(deviceId, (result: ScanResult) => {
        setScanResults(prev => [result, ...prev].slice(0, 50)); // Keep last 50 results
      });
      setIsListening(true);
      Alert.alert('Success', 'Started listening for scan results');
    } catch (error) {
      Alert.alert('Error', `Failed to start listening: ${error}`);
    }
  };

  const stopListening = async () => {
    if (!deviceId) return;

    try {
      await BleScanner.stopListening(deviceId);
      setIsListening(false);
      Alert.alert('Success', 'Stopped listening for scan results');
    } catch (error) {
      Alert.alert('Error', `Failed to stop listening: ${error}`);
    }
  };

  const triggerScan = async () => {
    if (!deviceId) {
      Alert.alert('Error', 'No scanner selected');
      return;
    }

    try {
      await BleScanner.triggerScan(deviceId, ScannerTrigger.SOFT_TRIGGER_3S);
      Alert.alert('Success', 'Scan triggered');
    } catch (error) {
      Alert.alert('Error', `Failed to trigger scan: ${error}`);
    }
  };

  const configureScanner = async () => {
    if (!deviceId) {
      Alert.alert('Error', 'No scanner selected');
      return;
    }

    try {
      // Set scanner to host trigger mode
      await BleScanner.setScannerMode(deviceId, ScannerMode.HOST_TRIGGER);

      // Set beep settings
      await BleScanner.setBeepSettings(deviceId, {
        volume: BeepVolume.MIDDLE,
        tone: BeepTone.HIGH_TONE,
        enabled: true
      });

      Alert.alert('Success', 'Scanner configured');
    } catch (error) {
      Alert.alert('Error', `Failed to configure scanner: ${error}`);
    }
  };

  const clearScanResults = () => {
    setScanResults([]);
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Scanner: {deviceName}</Text>
      </View>

      <View style={styles.content}>

        <View style={styles.buttonContainer}>
          <TouchableOpacity style={styles.button} onPress={configureScanner}>
            <Text style={styles.buttonText}>Configure Scanner</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.button, isListening ? styles.buttonSuccess : styles.button]}
            onPress={isListening ? stopListening : startListening}
          >
            <Text style={styles.buttonText}>
              {isListening ? 'Stop Listening' : 'Start Listening'}
            </Text>
          </TouchableOpacity>

          <TouchableOpacity style={styles.button} onPress={triggerScan}>
            <Text style={styles.buttonText}>Trigger Scan</Text>
          </TouchableOpacity>
        </View>

        {/* Scan Results Section */}
        <View style={styles.scanResultsContainer}>
          <View style={styles.scanResultsHeader}>
            <Text style={styles.scanResultsTitle}>
              Scan Results ({scanResults.length})
            </Text>
            <TouchableOpacity style={styles.clearButton} onPress={clearScanResults}>
              <Text style={styles.clearButtonText}>Clear</Text>
            </TouchableOpacity>
          </View>

          <ScrollView style={styles.scanResultsList}>
            {scanResults.length === 0 ? (
              <Text style={styles.noResultsText}>
                {isListening ? 'Listening for scan results...' : 'No scan results yet. Start listening to see results.'}
              </Text>
            ) : (
              scanResults.map((result, index) => (
                <View key={index} style={styles.scanResultItem}>
                  <Text style={styles.scanResultData}>{result.data}</Text>
                  <Text style={styles.scanResultMeta}>
                    {new Date(result.timestamp).toLocaleTimeString()} • {result.deviceName}
                  </Text>
                </View>
              ))
            )}
          </ScrollView>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 20,
    backgroundColor: 'white',
    borderBottomWidth: 1,
    borderBottomColor: '#eeeeee',
  },
  backButton: {
    marginRight: 15,
  },
  backButtonText: {
    fontSize: 16,
    color: '#007AFF',
    fontWeight: '600',
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    flex: 1,
  },
  content: {
    flex: 1,
    padding: 20,
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
  buttonSuccess: {
    backgroundColor: '#28a745',
  },
  buttonText: {
    color: 'white',
    textAlign: 'center',
    fontSize: 16,
    fontWeight: '600',
  },
  scanResultsContainer: {
    flex: 1,
    backgroundColor: 'white',
    borderRadius: 8,
    padding: 15,
  },
  scanResultsHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 15,
  },
  scanResultsTitle: {
    fontSize: 18,
    fontWeight: 'bold',
  },
  clearButton: {
    backgroundColor: '#ff6b6b',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 4,
  },
  clearButtonText: {
    color: 'white',
    fontSize: 12,
    fontWeight: '600',
  },
  scanResultsList: {
    flex: 1,
  },
  noResultsText: {
    textAlign: 'center',
    color: '#666',
    fontStyle: 'italic',
    padding: 20,
  },
  scanResultItem: {
    padding: 15,
    borderBottomWidth: 1,
    borderBottomColor: '#eeeeee',
    backgroundColor: '#f8f9fa',
    marginBottom: 5,
    borderRadius: 4,
  },
  scanResultData: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 5,
  },
  scanResultMeta: {
    fontSize: 12,
    color: '#666666',
  },
});