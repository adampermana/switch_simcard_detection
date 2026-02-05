import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'switch_simcard_detection_method_channel.dart';

abstract class SwitchSimcardDetectionPlatform extends PlatformInterface {
  /// Constructs a SwitchSimcardDetectionPlatform.
  SwitchSimcardDetectionPlatform() : super(token: _token);

  static final Object _token = Object();

  static SwitchSimcardDetectionPlatform _instance =
      MethodChannelSwitchSimcardDetection();

  /// The default instance of [SwitchSimcardDetectionPlatform] to use.
  ///
  /// Defaults to [MethodChannelSwitchSimcardDetection].
  static SwitchSimcardDetectionPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [SwitchSimcardDetectionPlatform] when
  /// they register themselves.
  static set instance(SwitchSimcardDetectionPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  /// Get current active data SIM (0 for SIM1, 1 for SIM2)
  Future<int> getCurrentDataSIM() {
    throw UnimplementedError('getCurrentDataSIM() has not been implemented.');
  }

  /// Switch data to specific SIM
  Future<bool> switchDataSIM(int simIndex) {
    throw UnimplementedError('switchDataSIM() has not been implemented.');
  }

  /// Get status of all SIM cards
  Future<Map<String, dynamic>> getSIMStatus() {
    throw UnimplementedError('getSIMStatus() has not been implemented.');
  }

  /// Enable automatic SIM switching
  Future<bool> enableAutoSwitch({
    required int primarySIM,
    required int fallbackSIM,
  }) {
    throw UnimplementedError('enableAutoSwitch() has not been implemented.');
  }

  /// Disable automatic SIM switching
  Future<bool> disableAutoSwitch() {
    throw UnimplementedError('disableAutoSwitch() has not been implemented.');
  }

  /// Check all permissions
  Future<Map<String, bool>> checkPermissions() {
    throw UnimplementedError('checkPermissions() has not been implemented.');
  }

  /// Get permission instructions
  Future<String> getPermissionInstructions() {
    throw UnimplementedError(
        'getPermissionInstructions() has not been implemented.');
  }

  /// Check if can switch SIM
  Future<bool> canSwitchSIM() {
    throw UnimplementedError('canSwitchSIM() has not been implemented.');
  }

  /// Get network quality
  Future<String> getNetworkQuality() {
    throw UnimplementedError('getNetworkQuality() has not been implemented.');
  }

  /// Get detailed network info
  Future<Map<String, dynamic>> getNetworkInfo() {
    throw UnimplementedError('getNetworkInfo() has not been implemented.');
  }

  /// Check if device is rooted
  Future<bool> isDeviceRooted() {
    throw UnimplementedError('isDeviceRooted() has not been implemented.');
  }

  /// Stream of SIM switch events
  Stream<SIMSwitchEvent> get onSIMSwitched {
    throw UnimplementedError('onSIMSwitched has not been implemented.');
  }
}
