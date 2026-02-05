import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'switch_simcard_detection_method_channel.dart';

abstract class SwitchSimcardDetectionPlatform extends PlatformInterface {
  /// Constructs a SwitchSimcardDetectionPlatform.
  SwitchSimcardDetectionPlatform() : super(token: _token);

  static final Object _token = Object();

  static SwitchSimcardDetectionPlatform _instance = MethodChannelSwitchSimcardDetection();

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
}
