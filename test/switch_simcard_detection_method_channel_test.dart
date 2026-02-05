import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:switch_simcard_detection/switch_simcard_detection_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelSwitchSimcardDetection platform = MethodChannelSwitchSimcardDetection();
  const MethodChannel channel = MethodChannel('switch_simcard_detection');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (MethodCall methodCall) async {
        return '42';
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  test('getPlatformVersion', () async {
    expect(await platform.getPlatformVersion(), '42');
  });
}
