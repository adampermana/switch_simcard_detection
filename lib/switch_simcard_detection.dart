import 'switch_simcard_detection_method_channel.dart';
import 'switch_simcard_detection_platform_interface.dart';

export 'switch_simcard_detection_method_channel.dart'
    show SIMSwitchEvent, PermissionDeniedException, SIMSwitchException;

class SwitchSimcardDetection {
  /// Get platform version
  Future<String?> getPlatformVersion() {
    return SwitchSimcardDetectionPlatform.instance.getPlatformVersion();
  }

  /// Get current active data SIM (0 for SIM1, 1 for SIM2)
  Future<int> getCurrentDataSIM() {
    return SwitchSimcardDetectionPlatform.instance.getCurrentDataSIM();
  }

  /// Switch data to specific SIM
  ///
  /// [simIndex]: 0 for SIM1, 1 for SIM2
  ///
  /// Returns true if switch was successful
  ///
  /// Throws [PermissionDeniedException] if missing required permissions
  /// Throws [SIMSwitchException] if switch failed
  Future<bool> switchDataSIM(int simIndex) {
    return SwitchSimcardDetectionPlatform.instance.switchDataSIM(simIndex);
  }

  /// Get status of all SIM cards
  Future<Map<String, dynamic>> getSIMStatus() {
    return SwitchSimcardDetectionPlatform.instance.getSIMStatus();
  }

  /// Enable automatic SIM switching
  ///
  /// [primarySIM]: Primary SIM to use (0 or 1)
  /// [fallbackSIM]: Fallback SIM when primary loses network (0 or 1)
  ///
  /// Returns true if auto-switch was enabled successfully
  ///
  /// Throws [PermissionDeniedException] if missing required permissions
  Future<bool> enableAutoSwitch({
    required int primarySIM,
    required int fallbackSIM,
  }) {
    return SwitchSimcardDetectionPlatform.instance.enableAutoSwitch(
      primarySIM: primarySIM,
      fallbackSIM: fallbackSIM,
    );
  }

  /// Disable automatic SIM switching
  Future<bool> disableAutoSwitch() {
    return SwitchSimcardDetectionPlatform.instance.disableAutoSwitch();
  }

  /// Check all permissions
  ///
  /// Returns a map of permission names to their granted status
  Future<Map<String, bool>> checkPermissions() {
    return SwitchSimcardDetectionPlatform.instance.checkPermissions();
  }

  /// Get instructions for granting required permissions
  Future<String> getPermissionInstructions() {
    return SwitchSimcardDetectionPlatform.instance.getPermissionInstructions();
  }

  /// Check if can switch SIM (has all required permissions)
  Future<bool> canSwitchSIM() {
    return SwitchSimcardDetectionPlatform.instance.canSwitchSIM();
  }

  /// Get current network quality
  ///
  /// Returns: "NONE", "POOR", "GOOD", or "EXCELLENT"
  Future<String> getNetworkQuality() {
    return SwitchSimcardDetectionPlatform.instance.getNetworkQuality();
  }

  /// Get detailed network information
  Future<Map<String, dynamic>> getNetworkInfo() {
    return SwitchSimcardDetectionPlatform.instance.getNetworkInfo();
  }

  /// Check if device is rooted
  Future<bool> isDeviceRooted() {
    return SwitchSimcardDetectionPlatform.instance.isDeviceRooted();
  }

  /// Stream of SIM switch events
  ///
  /// Emits events when:
  /// - SIM is manually switched
  /// - SIM is automatically switched due to network loss
  /// - Network is restored
  Stream<SIMSwitchEvent> get onSIMSwitched {
    return SwitchSimcardDetectionPlatform.instance.onSIMSwitched;
  }
}
