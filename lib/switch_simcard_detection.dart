
import 'switch_simcard_detection_platform_interface.dart';

class SwitchSimcardDetection {
  Future<String?> getPlatformVersion() {
    return SwitchSimcardDetectionPlatform.instance.getPlatformVersion();
  }
}
