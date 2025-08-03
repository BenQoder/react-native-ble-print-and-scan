import React, { useState } from 'react';
import { StyleSheet, TouchableOpacity, View, Text, Alert } from 'react-native';
import { BlePrintAndScan } from "react-native-ble-print-and-scan";
import { useLocalSearchParams, router } from 'expo-router';
import RecieptDOM from '@/components/RecieptDOM';

export default function PrinterScreen() {
  const { deviceId, deviceName } = useLocalSearchParams<{ deviceId: string; deviceName: string }>();
  const [cachedScreenshot, setCachedScreenshot] = useState<string | null>(null);

  // Callback function to receive screenshot from DOM component
  const handleScreenshotReady = async (screenshot: string) => {
    setCachedScreenshot(screenshot);
  };

  const printReceipt = async () => {
    if (!deviceId) {
      Alert.alert('Error', 'No device selected for printing');
      return;
    }

    if (!cachedScreenshot) {
      Alert.alert('Error', 'Receipt not ready yet. Please try again in a moment.');
      return;
    }

    try {
      console.log("Using cached screenshot, length:", cachedScreenshot.length);

      // Extract only the base64 data part (remove the "data:image/png;base64," prefix)
      const base64Data = cachedScreenshot.split(',')[1];
      console.log("Base64 data only, length:", base64Data.length);

      await BlePrintAndScan.sendToBluetoothThermalPrinter(deviceId, base64Data, 384); // Standard thermal printer width
      Alert.alert('Success', `Receipt printed successfully to ${deviceName}`);
    } catch (error) {
      console.error('Error printing receipt:', error);
      Alert.alert('Error', 'Failed to print receipt');
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backButton}>
          <Text style={styles.backButtonText}>← Back</Text>
        </TouchableOpacity>
        <Text style={styles.title}>Printer: {deviceName}</Text>
      </View>

      <View style={styles.content}>
        <Text style={styles.subtitle}>Receipt Preview</Text>
        
        <TouchableOpacity 
          style={[styles.printButton, !cachedScreenshot && styles.buttonDisabled]} 
          onPress={printReceipt}
          disabled={!cachedScreenshot}
        >
          <Text style={styles.printButtonText}>Print Receipt</Text>
        </TouchableOpacity>

        <View style={styles.previewContainer}>
          <RecieptDOM dom={{ matchContents: true }} onScreenshotReady={handleScreenshotReady} />
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
    paddingTop: 60,
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
  subtitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 20,
    textAlign: 'center',
  },
  printButton: {
    backgroundColor: '#007AFF',
    padding: 15,
    borderRadius: 8,
    marginBottom: 20,
  },
  buttonDisabled: {
    backgroundColor: '#cccccc',
  },
  printButtonText: {
    color: 'white',
    textAlign: 'center',
    fontSize: 16,
    fontWeight: '600',
  },
  previewContainer: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#ccc',
    borderRadius: 8,
    backgroundColor: 'white',
  },
});