import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'switch_simcard_detection_platform_interface.dart';

/// An implementation of [SwitchSimcardDetectionPlatform] that uses method channels.
class MethodChannelSwitchSimcardDetection
    extends SwitchSimcardDetectionPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('switch_simcard_detection');

  @override
  Future<String?> getPlatformVersion() async {
    final version =
        await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
