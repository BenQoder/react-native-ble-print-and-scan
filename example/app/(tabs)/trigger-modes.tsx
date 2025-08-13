import React from 'react';
import { 
  StyleSheet, 
  TouchableOpacity, 
  ScrollView, 
  View, 
  Text 
} from 'react-native';
import { router } from 'expo-router';

export default function TriggerModesTab() {
  const triggerModes = [
    {
      id: 'host-trigger',
      title: 'Host Trigger Mode',
      description: 'App controls when scanning happens via programmatic commands',
      color: '#007AFF',
      icon: '💻',
      route: '/trigger-modes/host-trigger',
    },
    {
      id: 'key-hold',
      title: 'Key Hold Mode',
      description: 'Scanner scans continuously while button is held down',
      color: '#28a745',
      icon: '🔘',
      route: '/trigger-modes/key-hold',
    },
    {
      id: 'key-pulse',
      title: 'Key Pulse Mode',
      description: 'Single scan triggered by one button press',
      color: '#ffc107',
      icon: '👆',
      route: '/trigger-modes/key-pulse',
    },
    {
      id: 'continuous',
      title: 'Continuous Mode',
      description: 'Automatic scanning when barcode is detected',
      color: '#dc3545',
      icon: '🔄',
      route: '/trigger-modes/continuous',
    }
  ];

  const navigateToMode = (route: string) => {
    router.push(route);
  };

  return (
    <ScrollView style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Scanner Trigger Modes</Text>
        <Text style={styles.subtitle}>
          Explore different ways to control your barcode scanner
        </Text>
      </View>

      <View style={styles.modesContainer}>
        {triggerModes.map((mode) => (
          <TouchableOpacity
            key={mode.id}
            style={[styles.modeCard, { borderLeftColor: mode.color }]}
            onPress={() => navigateToMode(mode.route)}
          >
            <View style={styles.modeHeader}>
              <Text style={styles.modeIcon}>{mode.icon}</Text>
              <View style={styles.modeTitleContainer}>
                <Text style={[styles.modeTitle, { color: mode.color }]}>
                  {mode.title}
                </Text>
                <Text style={styles.modeDescription}>
                  {mode.description}
                </Text>
              </View>
            </View>
            
            <View style={styles.modeFooter}>
              <Text style={[styles.tryButton, { color: mode.color }]}>
                Try {mode.title} →
              </Text>
            </View>
          </TouchableOpacity>
        ))}
      </View>
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
    backgroundColor: '#333',
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: 'white',
    marginBottom: 5,
  },
  subtitle: {
    fontSize: 16,
    color: '#ccc',
  },
  modesContainer: {
    padding: 20,
  },
  modeCard: {
    backgroundColor: 'white',
    borderRadius: 10,
    padding: 20,
    marginBottom: 20,
    borderLeftWidth: 5,
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  modeHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 15,
  },
  modeIcon: {
    fontSize: 32,
    marginRight: 15,
  },
  modeTitleContainer: {
    flex: 1,
  },
  modeTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 5,
  },
  modeDescription: {
    fontSize: 16,
    color: '#666',
    lineHeight: 22,
  },
  modeFooter: {
    borderTopWidth: 1,
    borderTopColor: '#eee',
    paddingTop: 15,
  },
  tryButton: {
    fontSize: 16,
    fontWeight: 'bold',
    textAlign: 'right',
  },
});