import React, { useState, useEffect } from 'react';
import { 
  StyleSheet, 
  TouchableOpacity, 
  ScrollView, 
  Alert, 
  View, 
  Text,
  Switch 
} from 'react-native';
import { 
  BleScanner, 
  type ScanResult, 
  type Device,
  ScannerMode, 
  BeepVolume, 
  BeepTone 
} from "react-native-ble-print-and-scan";

export default function KeyPulseMode() {
  const [connectedScanner, setConnectedScanner] = useState<Device | null>(null);
  const [scanResults, setScanResults] = useState<ScanResult[]>([]);
  const [isListening, setIsListening] = useState(false);
  const [isInitialized, setIsInitialized] = useState(false);
  const [beepEnabled, setBeepEnabled] = useState(true);

  useEffect(() => {
    checkExistingConnection();
  }, []);

  const checkExistingConnection = async () => {
    try {
      setIsInitialized(true);
      
      const connected = await BleScanner.getConnectedScanners();
      if (connected.length > 0) {
        setConnectedScanner(connected[0]);
        await configureKeyPulseMode(connected[0]);
        Alert.alert('Success', `Using existing connection to ${connected[0].name}`);
      } else {
        Alert.alert('Info', 'No scanner connected. Please connect a scanner in the Scanner tab first.');
      }
    } catch (error) {
      Alert.alert('Error', `Failed to check scanner connection: ${error}`);
    }
  };

  const configureKeyPulseMode = async (scanner: Device) => {
    try {
      await BleScanner.setScannerMode(scanner.id, ScannerMode.KEY_PULSE);
      await BleScanner.setBeepSettings(scanner.id, {
        volume: beepEnabled ? BeepVolume.MIDDLE : BeepVolume.MUTE,
        tone: BeepTone.HIGH_TONE,
        enabled: beepEnabled
      });
    } catch (error) {
      Alert.alert('Error', `Failed to configure scanner: ${error}`);
    }
  };

  const startListening = async () => {
    if (!connectedScanner) {
      Alert.alert('Error', 'No scanner connected');
      return;
    }

    try {
      await BleScanner.startListening(connectedScanner.id, (result: ScanResult) => {
        setScanResults(prev => [result, ...prev].slice(0, 50));
      });
      setIsListening(true);
    } catch (error) {
      Alert.alert('Error', `Failed to start listening: ${error}`);
    }
  };

  const stopListening = async () => {
    if (!connectedScanner) return;

    try {
      await BleScanner.stopListening(connectedScanner.id);
      setIsListening(false);
    } catch (error) {
      Alert.alert('Error', `Failed to stop listening: ${error}`);
    }
  };

  const updateBeepSettings = async () => {
    if (!connectedScanner) return;

    try {
      await BleScanner.setBeepSettings(connectedScanner.id, {
        volume: beepEnabled ? BeepVolume.MIDDLE : BeepVolume.MUTE,
        tone: BeepTone.HIGH_TONE,
        enabled: beepEnabled
      });
      Alert.alert('Success', `Beep ${beepEnabled ? 'enabled' : 'disabled'}`);
    } catch (error) {
      Alert.alert('Error', `Failed to update beep settings: ${error}`);
    }
  };

  const disconnectScanner = async () => {
    if (!connectedScanner) return;

    try {
      await BleScanner.disconnectFromScanner(connectedScanner.id);
      setConnectedScanner(null);
      setIsListening(false);
      setScanResults([]);
    } catch (error) {
      Alert.alert('Error', `Failed to disconnect: ${error}`);
    }
  };

  return (
    <ScrollView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Key Pulse Mode</Text>
        <Text style={styles.subtitle}>
          Scanner triggers on single button press
        </Text>
      </View>

      {/* Status */}
      <View style={styles.statusContainer}>
        <Text style={styles.statusTitle}>Status</Text>
        <Text style={styles.statusText}>
          • Connected: {connectedScanner ? `✅ ${connectedScanner.name}` : '❌'}
        </Text>
        <Text style={styles.statusText}>
          • Listening: {isListening ? '✅' : '❌'}
        </Text>
        <Text style={styles.statusText}>
          • Scan Results: {scanResults.length}
        </Text>
      </View>

      {/* Connection */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Scanner Connection</Text>
        {!connectedScanner ? (
          <View>
            <Text style={styles.noConnectionText}>
              No scanner connected. Connect a scanner in the Scanner tab first.
            </Text>
            <TouchableOpacity style={styles.button} onPress={checkExistingConnection}>
              <Text style={styles.buttonText}>Check for Connected Scanners</Text>
            </TouchableOpacity>
          </View>
        ) : (
          <View>
            <Text style={styles.connectedText}>
              ✅ Connected to: {connectedScanner.name}
            </Text>
            <TouchableOpacity style={styles.buttonDanger} onPress={disconnectScanner}>
              <Text style={styles.buttonText}>Disconnect Scanner</Text>
            </TouchableOpacity>
          </View>
        )}
      </View>

      {/* Configuration */}
      {connectedScanner && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Configuration</Text>
          <View style={styles.settingRow}>
            <Text style={styles.settingLabel}>Enable Beep</Text>
            <Switch
              value={beepEnabled}
              onValueChange={setBeepEnabled}
              trackColor={{ false: '#767577', true: '#81b0ff' }}
              thumbColor={beepEnabled ? '#ffc107' : '#f4f3f4'}
            />
          </View>
          <TouchableOpacity style={styles.buttonSecondary} onPress={updateBeepSettings}>
            <Text style={styles.buttonText}>Update Beep Settings</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Listening */}
      {connectedScanner && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Listening Control</Text>
          <TouchableOpacity
            style={[styles.button, isListening && styles.buttonSuccess]}
            onPress={isListening ? stopListening : startListening}
          >
            <Text style={styles.buttonText}>
              {isListening ? 'Stop Listening' : 'Start Listening'}
            </Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Instructions */}
      {connectedScanner && isListening && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>How to Use Key Pulse Mode</Text>
          <View style={styles.instructionBox}>
            <Text style={styles.instructionTitle}>📋 Instructions:</Text>
            <Text style={styles.instructionText}>
              1. Scanner is now configured for Key Pulse mode
            </Text>
            <Text style={styles.instructionText}>
              2. Point the scanner at a barcode
            </Text>
            <Text style={styles.instructionText}>
              3. Press the physical scan button ONCE (quick press)
            </Text>
            <Text style={styles.instructionText}>
              4. The scanner will scan once and stop automatically
            </Text>
            <Text style={styles.instructionText}>
              5. Press the button again for another scan
            </Text>
          </View>
          
          <View style={styles.noteBox}>
            <Text style={styles.noteTitle}>💡 Note:</Text>
            <Text style={styles.noteText}>
              In Key Pulse mode, each button press triggers a single scan attempt. 
              This is useful when you want to scan one barcode at a time with precise control.
            </Text>
          </View>
          
          <View style={styles.tipBox}>
            <Text style={styles.tipTitle}>⚡ Tip:</Text>
            <Text style={styles.tipText}>
              This mode is perfect for inventory management where you need to scan items one by one 
              and want to avoid accidental multiple scans.
            </Text>
          </View>
        </View>
      )}

      {/* Scan Results */}
      {scanResults.length > 0 && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Scan Results ({scanResults.length})</Text>
          <TouchableOpacity 
            style={styles.clearButton} 
            onPress={() => setScanResults([])}
          >
            <Text style={styles.clearButtonText}>Clear Results</Text>
          </TouchableOpacity>
          
          <View style={styles.scanResultsList}>
            {scanResults.map((result, index) => (
              <View key={index} style={styles.scanResultItem}>
                <Text style={styles.scanResultData}>{result.data}</Text>
                <Text style={styles.scanResultMeta}>
                  {new Date(result.timestamp).toLocaleTimeString()}
                </Text>
              </View>
            ))}
          </View>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  header: {
    padding: 20,
    backgroundColor: '#ffc107',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#856404',
    marginBottom: 5,
  },
  subtitle: {
    fontSize: 16,
    color: '#856404',
  },
  statusContainer: {
    margin: 20,
    padding: 15,
    backgroundColor: 'white',
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: '#ffc107',
  },
  statusTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 10,
  },
  statusText: {
    fontSize: 16,
    marginBottom: 5,
  },
  section: {
    margin: 20,
    marginTop: 0,
    padding: 15,
    backgroundColor: 'white',
    borderRadius: 8,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 15,
  },
  button: {
    backgroundColor: '#ffc107',
    padding: 15,
    borderRadius: 8,
    marginBottom: 10,
  },
  buttonSuccess: {
    backgroundColor: '#28a745',
  },
  buttonSecondary: {
    backgroundColor: '#6c757d',
    padding: 12,
    borderRadius: 8,
  },
  buttonDanger: {
    backgroundColor: '#dc3545',
    padding: 15,
    borderRadius: 8,
  },
  buttonText: {
    color: '#856404',
    textAlign: 'center',
    fontSize: 16,
    fontWeight: '600',
  },
  settingRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 15,
  },
  settingLabel: {
    fontSize: 16,
  },
  instructionBox: {
    backgroundColor: '#f8f9fa',
    padding: 15,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: '#ffc107',
    marginBottom: 15,
  },
  instructionTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 10,
    color: '#ffc107',
  },
  instructionText: {
    fontSize: 14,
    marginBottom: 5,
    color: '#333',
  },
  noteBox: {
    backgroundColor: '#fff3cd',
    padding: 15,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: '#ffc107',
    marginBottom: 15,
  },
  noteTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 10,
    color: '#856404',
  },
  noteText: {
    fontSize: 14,
    color: '#856404',
    lineHeight: 20,
  },
  tipBox: {
    backgroundColor: '#e7f3ff',
    padding: 15,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: '#007AFF',
  },
  tipTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 10,
    color: '#007AFF',
  },
  tipText: {
    fontSize: 14,
    color: '#007AFF',
    lineHeight: 20,
  },
  clearButton: {
    backgroundColor: '#dc3545',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 4,
    alignSelf: 'flex-end',
    marginBottom: 10,
  },
  clearButtonText: {
    color: 'white',
    fontSize: 12,
    fontWeight: '600',
  },
  scanResultsList: {
    maxHeight: 300,
  },
  scanResultItem: {
    padding: 12,
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
  noConnectionText: {
    fontSize: 16,
    color: '#666',
    marginBottom: 15,
    textAlign: 'center',
    fontStyle: 'italic',
  },
  connectedText: {
    fontSize: 16,
    color: '#28a745',
    marginBottom: 15,
    fontWeight: '600',
  },
});