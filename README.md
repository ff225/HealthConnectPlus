# HealthConnectPlus

A research Android application for multi-sensor health data collection and activity classification using machine learning.

## Overview

HealthConnectPlus integrates with Android Health Connect and Movesense Bluetooth sensors to collect health metrics (heart rate, steps, accelerometer data) and perform real-time activity classification using TensorFlow Lite CNN models.

## Features

- **Health Connect Integration**: Read and write heart rate and step count data
- **Movesense Sensor Support**: Connect to multiple Bluetooth Movesense sensors simultaneously
- **Activity Classification**: CNN-based machine learning models for activity recognition
- **Data Storage**: Local Room database for offline data persistence
- **Cloud Sync**: InfluxDB integration for data export and analysis
- **Background Monitoring**: Foreground service for continuous data collection

## Requirements

### Device Requirements
- Android device running Android 10 (API 29) or higher
- Bluetooth support (for Movesense sensors)
- Google Health Connect app installed

### Permissions
The app requires the following permissions:
- Health Connect permissions (heart rate, steps)
- Activity Recognition
- Bluetooth (Connect & Scan)
- Foreground Service
- Notifications

## Installation

1. **Install Health Connect** (if not already installed):
   - Download from Google Play Store: [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata)

2. **Build and Install HealthConnectPlus**:
   ```bash
   ./gradlew assembleDebug
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

   Or open the project in Android Studio and run it directly.

## Usage Guide

### First Launch

1. **Launch the app** - You'll see a splash screen while permissions are checked
2. **Grant permissions** - The app will request:
   - Health Connect permissions (heart rate read/write, steps read/write)
   - Activity recognition permission
   - Notification permission
3. Once granted, you'll be taken to the Home screen

### Home Screen

The Home screen provides quick access to all features:

- **Heart Rate**: View heart rate data from Health Connect
- **Steps**: View step count data from Health Connect
- **Movesense**: Connect and configure Movesense Bluetooth sensors
- **TEST**: Run activity classification on collected sensor data
- **Send movesense**: Upload Movesense data to InfluxDB
- **Stop Service**: Stop the background data collection service

### Data Collection Settings

Access Settings from the navigation menu to configure data collection:

1. **Navigate to Settings**
2. **Toggle Data Collection**:
   - **Heart Rate**: Collects heart rate data every 15 minutes from Health Connect
   - **Steps**: Collects step count data every 15 minutes from Health Connect
3. **Configure Movesense**: Tap to set up Bluetooth sensors

### Movesense Sensor Setup

HealthConnectPlus supports multiple Movesense sensor placements for comprehensive activity monitoring:

1. **Navigate to Movesense screen** (from Home or Settings)
2. **Enable Bluetooth and Location**:
   - Grant Bluetooth permissions when prompted
   - Enable location services (required for Bluetooth scanning on Android)
3. **Scan for devices**:
   - Tap the refresh icon to scan for nearby Movesense sensors
   - Available devices will appear in the list
4. **Connect sensors**:
   - Tap on a device to connect
   - You can connect multiple sensors simultaneously
5. **Configure sensor placements**:
   - **Right Pocket**: Phone/sensor in right pocket
   - **Left Wrist**: Sensor on left wrist
   - **Right Ankle**: Sensor on right ankle
   - **Chest**: Sensor on chest
6. **Start data collection**:
   - Toggle the switch to begin collecting accelerometer data
   - Data is stored locally and used for activity classification

### Activity Classification

The app includes pre-trained CNN models for different sensor configurations:

- `cnn_right_pocket.tflite`: Single sensor in right pocket
- `cnn_left_wrist.tflite`: Single sensor on left wrist
- `cnn_rightpocket_leftwrist.tflite`: Two sensors (pocket + wrist)
- `cnn_rightpocket_leftwrist_rightankle.tflite`: Three sensors
- `cnn_rightpocket_leftwrist_rightankle_chest.tflite`: Four sensors

**To run classification**:
1. Ensure sensor data has been collected
2. Tap **TEST** on the Home screen
3. The model will process recent sensor data and classify activities

### Data Export

**Export to InfluxDB**:
1. Configure InfluxDB connection settings (see PreferencesManager)
2. Tap **Send movesense** on the Home screen
3. Collected Movesense data will be uploaded to your InfluxDB instance

**Local Data Access**:
- All data is stored in a local Room database
- Access via the content provider: `content://com.research.healthconnectplus.provider`

## Background Service

The app runs a foreground service (`HealthConnectPlusService`) for continuous monitoring:

- **Automatically starts** when you open the Home screen
- **Notification** shows the service is running
- **Stop service** by tapping "Stop Service" on the Home screen

## Data Privacy

- All health data is stored locally on your device
- Data is only sent to InfluxDB when you explicitly trigger the "Send movesense" action
- The app uses Health Connect's privacy-preserving APIs
- Review permissions in Health Connect settings at any time

## Troubleshooting

### Health Connect Not Available
- **Error**: "HealthConnect SDK is not available on this device"
- **Solution**: Install Health Connect from Google Play Store

### Bluetooth Sensors Not Found
- Ensure Bluetooth is enabled
- Enable Location services (required for Bluetooth scanning)
- Check that Movesense sensors are powered on and nearby
- Grant Bluetooth permissions when prompted

### Permissions Denied
- Go to Android Settings > Apps > HealthConnectPlus > Permissions
- Enable all required permissions
- For Health Connect permissions, open the Health Connect app and manage app permissions

### Data Not Collecting
- Check that data collection is enabled in Settings
- Verify Health Connect permissions are granted
- Ensure the foreground service is running (check notification)

## Development

### Project Structure
```
app/src/main/java/com/research/healthconnectplus/
├── data/              # Database, DAOs, Repositories
├── screen/            # UI screens (Compose)
├── workers/           # Background workers for data sync
├── classifier/        # ML classification logic
├── bluetooth/         # Movesense Bluetooth handling
└── provider/          # Content provider for data access
```

### Build Configuration
- **Min SDK**: 29 (Android 10)
- **Target SDK**: 34 (Android 14)
- **Kotlin Version**: 1.9.20
- **Compose**: Enabled

### Key Dependencies
- Health Connect Client: `androidx.health.connect:connect-client:1.1.0-alpha07`
- Room Database: `androidx.room:room-ktx:2.6.1`
- TensorFlow Lite: `org.tensorflow:tensorflow-lite:2.5.0`
- Movesense SDK: `mdslib-3.15.0`
- InfluxDB Client: `com.influxdb:influxdb-client-kotlin:6.6.0`
- RxAndroidBle: `com.polidea.rxandroidble2:rxandroidble:1.10.2`

## License

This is a research project. Please check with the repository owner for licensing information.

## Support

For issues or questions about using the app, please refer to the project's issue tracker or contact the research team.
