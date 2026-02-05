import 'package:flutter_test/flutter_test.dart';
import 'package:switch_simcard_detection/switch_simcard_detection.dart';
import 'package:switch_simcard_detection/switch_simcard_detection_platform_interface.dart';
import 'package:switch_simcard_detection/switch_simcard_detection_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockSwitchSimcardDetectionPlatform
    with MockPlatformInterfaceMixin
    implements SwitchSimcardDetectionPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final SwitchSimcardDetectionPlatform initialPlatform = SwitchSimcardDetectionPlatform.instance;

  test('$MethodChannelSwitchSimcardDetection is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelSwitchSimcardDetection>());
  });

  test('getPlatformVersion', () async {
    SwitchSimcardDetection switchSimcardDetectionPlugin = SwitchSimcardDetection();
    MockSwitchSimcardDetectionPlatform fakePlatform = MockSwitchSimcardDetectionPlatform();
    SwitchSimcardDetectionPlatform.instance = fakePlatform;

    expect(await switchSimcardDetectionPlugin.getPlatformVersion(), '42');
  });
}
