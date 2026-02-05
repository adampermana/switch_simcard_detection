import 'package:flutter_test/flutter_test.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:switch_simcard_detection/switch_simcard_detection.dart';
import 'package:switch_simcard_detection/switch_simcard_detection_method_channel.dart';
import 'package:switch_simcard_detection/switch_simcard_detection_platform_interface.dart';

class MockSwitchSimcardDetectionPlatform
    with MockPlatformInterfaceMixin
    implements SwitchSimcardDetectionPlatform {
  @override
  Future<String?> getPlatformVersion() => Future.value('42');

  @override
  Future<int> getCurrentDataSIM() => Future.value(0);

  @override
  Future<bool> switchDataSIM(int simIndex) => Future.value(true);

  @override
  Future<Map<String, dynamic>> getSIMStatus() => Future.value({});

  @override
  Future<bool> enableAutoSwitch(
          {required int primarySIM, required int fallbackSIM}) =>
      Future.value(true);

  @override
  Future<bool> disableAutoSwitch() => Future.value(true);

  @override
  Future<Map<String, bool>> checkPermissions() => Future.value({});

  @override
  Future<String> getPermissionInstructions() => Future.value('');

  @override
  Future<bool> canSwitchSIM() => Future.value(true);

  @override
  Future<String> getNetworkQuality() => Future.value('GOOD');

  @override
  Future<Map<String, dynamic>> getNetworkInfo() => Future.value({});

  @override
  Future<bool> isDeviceRooted() => Future.value(false);

  @override
  Stream<SIMSwitchEvent> get onSIMSwitched => Stream.empty();
}

void main() {
  final SwitchSimcardDetectionPlatform initialPlatform =
      SwitchSimcardDetectionPlatform.instance;

  test('$MethodChannelSwitchSimcardDetection is the default instance', () {
    expect(
        initialPlatform, isInstanceOf<MethodChannelSwitchSimcardDetection>());
  });

  test('getPlatformVersion', () async {
    SwitchSimcardDetection switchSimcardDetectionPlugin =
        SwitchSimcardDetection();
    MockSwitchSimcardDetectionPlatform fakePlatform =
        MockSwitchSimcardDetectionPlatform();
    SwitchSimcardDetectionPlatform.instance = fakePlatform;

    expect(await switchSimcardDetectionPlugin.getPlatformVersion(), '42');
  });
}
