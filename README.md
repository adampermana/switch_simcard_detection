# Switch SIM Card Detection Plugin

A Flutter plugin for **automatic SIM card switching** on Android 12+ devices. Detects network loss and automatically switches to a fallback SIM card to maintain connectivity.

## Features

✅ **Manual SIM Switching** - Programmatically switch between SIM cards  
✅ **Automatic Network Monitoring** - Detect network loss and auto-switch to fallback SIM  
✅ **Network Quality Detection** - Monitor signal strength and connectivity  
✅ **Permission Management** - Helper methods for checking and requesting permissions  
✅ **Event Streaming** - Real-time events for SIM switches and network changes  
✅ **Multiple Switching Methods** - Settings-based, service call (root), and hybrid approaches  

## Requirements

- **Android 12+** (API Level 31+)
- **Dual SIM device** (physical or eSIM)
- **WRITE_SECURE_SETTINGS permission** (granted via ADB)

## Installation

Add this to your package's `pubspec.yaml` file:

```yaml
dependencies:
  switch_simcard_detection: ^0.0.1
```

Then run:

```bash
flutter pub get
```

## Permissions Setup

### 1. Add Permissions to AndroidManifest.xml

Add these permissions to your `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_PHONE_NUMBERS" />
```

### 2. Grant WRITE_SECURE_SETTINGS via ADB

This permission **cannot be requested at runtime**. You must grant it via ADB:

```bash
# Enable USB Debugging on your device first
# Then connect via USB and run:
adb shell pm grant com.your.package.name android.permission.WRITE_SECURE_SETTINGS
```

**Note**: This only needs to be done once. The permission persists until the app is uninstalled.

## Usage

### Basic Usage

```dart
import 'package:switch_simcard_detection/switch_simcard_detection.dart';

final plugin = SwitchSimcardDetection();

// Get current active SIM (0 = SIM1, 1 = SIM2)
int currentSIM = await plugin.getCurrentDataSIM();
print('Current SIM: ${currentSIM + 1}');

// Switch to specific SIM
bool success = await plugin.switchDataSIM(1); // Switch to SIM2
if (success) {
  print('Successfully switched to SIM2');
}
```

### Automatic Switching

Enable automatic switching to fallback SIM when primary SIM loses network:

```dart
// Enable auto-switch (Primary: SIM1, Fallback: SIM2)
await plugin.enableAutoSwitch(
  primarySIM: 0,
  fallbackSIM: 1,
);

// Disable auto-switch
await plugin.disableAutoSwitch();
```

### Listen to Events

```dart
plugin.onSIMSwitched.listen((event) {
  print('Event: ${event.event}');
  
  if (event.event == 'simSwitched') {
    print('Switched to SIM ${(event.simIndex ?? 0) + 1}');
  } else if (event.event == 'autoSwitched') {
    print('Auto-switched from SIM ${(event.fromSIM ?? 0) + 1} to SIM ${(event.toSIM ?? 0) + 1}');
    print('Reason: ${event.reason}');
  } else if (event.event == 'networkRestored') {
    print('Network restored on SIM ${(event.simIndex ?? 0) + 1}');
  }
});
```

### Check Permissions

```dart
// Check all permissions
Map<String, bool> permissions = await plugin.checkPermissions();
print('Permissions: $permissions');

// Check if can switch SIM
bool canSwitch = await plugin.canSwitchSIM();
if (!canSwitch) {
  // Show instructions to user
  String instructions = await plugin.getPermissionInstructions();
  print(instructions);
}
```

### Network Monitoring

```dart
// Get network quality
String quality = await plugin.getNetworkQuality();
print('Network quality: $quality'); // NONE, POOR, GOOD, or EXCELLENT

// Get detailed network info
Map<String, dynamic> info = await plugin.getNetworkInfo();
print('Has network: ${info['hasNetwork']}');
print('Signal level: ${info['signalLevel']}');
```

### Get SIM Status

```dart
Map<String, dynamic> status = await plugin.getSIMStatus();
print('SIM Status: $status');
```

## API Reference

### Methods

| Method | Description | Returns |
|--------|-------------|---------|
| `getCurrentDataSIM()` | Get current active data SIM | `Future<int>` (0 or 1) |
| `switchDataSIM(int simIndex)` | Switch to specific SIM | `Future<bool>` |
| `getSIMStatus()` | Get status of all SIM cards | `Future<Map<String, dynamic>>` |
| `enableAutoSwitch({primarySIM, fallbackSIM})` | Enable automatic switching | `Future<bool>` |
| `disableAutoSwitch()` | Disable automatic switching | `Future<bool>` |
| `checkPermissions()` | Check all permissions | `Future<Map<String, bool>>` |
| `getPermissionInstructions()` | Get ADB permission instructions | `Future<String>` |
| `canSwitchSIM()` | Check if can switch SIM | `Future<bool>` |
| `getNetworkQuality()` | Get network quality | `Future<String>` |
| `getNetworkInfo()` | Get detailed network info | `Future<Map<String, dynamic>>` |
| `isDeviceRooted()` | Check if device is rooted | `Future<bool>` |

### Events

| Event | Description | Properties |
|-------|-------------|------------|
| `simSwitched` | Manual SIM switch | `simIndex`, `timestamp` |
| `autoSwitched` | Automatic SIM switch | `fromSIM`, `toSIM`, `reason`, `timestamp` |
| `networkRestored` | Network restored | `simIndex`, `timestamp` |

### Exceptions

- `PermissionDeniedException` - Thrown when required permissions are missing
- `SIMSwitchException` - Thrown when SIM switch fails

## Example App

See the [example](example/) directory for a complete demo app with:
- Manual SIM switching UI
- Auto-switch toggle
- Permission management
- Network status monitoring
- Real-time event logs

## How It Works

The plugin uses multiple methods to switch SIM cards:

1. **Settings-based** (Primary method)
   - Modifies `multi_sim_data_call` global setting
   - Requires `WRITE_SECURE_SETTINGS` permission
   - Most reliable on non-rooted devices

2. **Service Call** (Root method)
   - Uses `service call phone` with root access
   - More powerful but requires rooted device
   - Fallback when settings method fails

3. **Hybrid Approach**
   - Tries settings method first
   - Falls back to service call if rooted
   - Ensures maximum compatibility

## Testing

### Prerequisites
- Physical dual SIM Android device (emulator doesn't support dual SIM)
- Both SIM cards active
- USB debugging enabled
- WRITE_SECURE_SETTINGS permission granted

### Test Manual Switching

```bash
# Run example app
cd example
flutter run

# Grant permission via ADB
adb shell pm grant com.switch_simcard_detection.adpstore.switch_simcard_detection_example android.permission.WRITE_SECURE_SETTINGS

# Test switching via app UI or ADB
adb shell settings put global multi_sim_data_call 2  # Switch to SIM2
adb shell settings get global multi_sim_data_call    # Verify
```

### Test Auto-Switching

1. Enable auto-switch in example app
2. Turn off SIM1 or move to area without SIM1 coverage
3. Plugin should automatically switch to SIM2
4. Check logs for auto-switch event

## Device Compatibility

Tested on:
- ✅ Xiaomi (Android 12+)
- ✅ Samsung (Android 12+)
- ✅ Google Pixel (Android 12+)

**Note**: Some carrier-locked devices may have dual SIM functionality disabled in software.

## Troubleshooting

### "Permission Denied" Error
- Ensure WRITE_SECURE_SETTINGS is granted via ADB
- Check runtime permissions are granted
- Verify package name in ADB command matches your app

### Switch Fails
- Verify both SIM cards are active
- Check device supports dual SIM
- Try enabling airplane mode and disabling it
- Check logcat for detailed error messages

### Auto-Switch Not Working
- Ensure runtime permissions are granted
- Verify network monitoring is enabled
- Check if device has proper network connectivity
- Review event logs for network quality changes

## Limitations

- **Android 12+ only** - Older versions not supported
- **Dual SIM required** - Single SIM devices won't work
- **ADB setup required** - One-time permission grant needed
- **Device-specific** - Some manufacturers may restrict functionality

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues, questions, or feature requests, please file an issue on GitHub.

---

**Made with ❤️ for Flutter developers who need reliable dual SIM management**
