import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'switch_simcard_detection_platform_interface.dart';

/// An implementation of [SwitchSimcardDetectionPlatform] that uses method channels.
class MethodChannelSwitchSimcardDetection
    extends SwitchSimcardDetectionPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('switch_simcard_detection');

  /// The event channel for SIM switch events
  @visibleForTesting
  final eventChannel = const EventChannel('switch_simcard_detection/events');

  @override
  Future<String?> getPlatformVersion() async {
    final version =
        await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }

  @override
  Future<int> getCurrentDataSIM() async {
    try {
      final result = await methodChannel.invokeMethod<int>('getCurrentDataSIM');
      return result ?? -1;
    } on PlatformException catch (e) {
      throw Exception('Failed to get current data SIM: ${e.message}');
    }
  }

  @override
  Future<bool> switchDataSIM(int simIndex) async {
    try {
      final result = await methodChannel.invokeMethod<bool>(
        'switchDataSIM',
        {'simIndex': simIndex},
      );
      return result ?? false;
    } on PlatformException catch (e) {
      if (e.code == 'PERMISSION_DENIED') {
        throw PermissionDeniedException(e.message ?? 'Permission denied');
      } else if (e.code == 'SWITCH_FAILED') {
        throw SIMSwitchException(e.message ?? 'Failed to switch SIM');
      }
      throw Exception('Failed to switch SIM: ${e.message}');
    }
  }

  @override
  Future<Map<String, dynamic>> getSIMStatus() async {
    try {
      final result = await methodChannel.invokeMethod<Map>('getSIMStatus');
      return Map<String, dynamic>.from(result ?? {});
    } on PlatformException catch (e) {
      throw Exception('Failed to get SIM status: ${e.message}');
    }
  }

  @override
  Future<bool> enableAutoSwitch({
    required int primarySIM,
    required int fallbackSIM,
  }) async {
    try {
      final result = await methodChannel.invokeMethod<bool>(
        'enableAutoSwitch',
        {
          'primarySIM': primarySIM,
          'fallbackSIM': fallbackSIM,
        },
      );
      return result ?? false;
    } on PlatformException catch (e) {
      if (e.code == 'PERMISSION_DENIED') {
        throw PermissionDeniedException(e.message ?? 'Permission denied');
      }
      throw Exception('Failed to enable auto-switch: ${e.message}');
    }
  }

  @override
  Future<bool> disableAutoSwitch() async {
    try {
      final result =
          await methodChannel.invokeMethod<bool>('disableAutoSwitch');
      return result ?? false;
    } on PlatformException catch (e) {
      throw Exception('Failed to disable auto-switch: ${e.message}');
    }
  }

  @override
  Future<Map<String, bool>> checkPermissions() async {
    try {
      final result = await methodChannel.invokeMethod<Map>('checkPermissions');
      return Map<String, bool>.from(result ?? {});
    } on PlatformException catch (e) {
      throw Exception('Failed to check permissions: ${e.message}');
    }
  }

  @override
  Future<String> getPermissionInstructions() async {
    try {
      final result =
          await methodChannel.invokeMethod<String>('getPermissionInstructions');
      return result ?? '';
    } on PlatformException catch (e) {
      throw Exception('Failed to get permission instructions: ${e.message}');
    }
  }

  @override
  Future<bool> canSwitchSIM() async {
    try {
      final result = await methodChannel.invokeMethod<bool>('canSwitchSIM');
      return result ?? false;
    } on PlatformException catch (e) {
      throw Exception('Failed to check if can switch SIM: ${e.message}');
    }
  }

  @override
  Future<String> getNetworkQuality() async {
    try {
      final result =
          await methodChannel.invokeMethod<String>('getNetworkQuality');
      return result ?? 'UNKNOWN';
    } on PlatformException catch (e) {
      throw Exception('Failed to get network quality: ${e.message}');
    }
  }

  @override
  Future<Map<String, dynamic>> getNetworkInfo() async {
    try {
      final result = await methodChannel.invokeMethod<Map>('getNetworkInfo');
      return Map<String, dynamic>.from(result ?? {});
    } on PlatformException catch (e) {
      throw Exception('Failed to get network info: ${e.message}');
    }
  }

  @override
  Future<bool> isDeviceRooted() async {
    try {
      final result = await methodChannel.invokeMethod<bool>('isDeviceRooted');
      return result ?? false;
    } on PlatformException catch (e) {
      throw Exception('Failed to check if device is rooted: ${e.message}');
    }
  }

  @override
  Stream<SIMSwitchEvent> get onSIMSwitched {
    return eventChannel.receiveBroadcastStream().map((event) {
      final map = Map<String, dynamic>.from(event as Map);
      return SIMSwitchEvent.fromMap(map);
    });
  }
}

/// Exception thrown when permission is denied
class PermissionDeniedException implements Exception {
  final String message;
  PermissionDeniedException(this.message);

  @override
  String toString() => 'PermissionDeniedException: $message';
}

/// Exception thrown when SIM switch fails
class SIMSwitchException implements Exception {
  final String message;
  SIMSwitchException(this.message);

  @override
  String toString() => 'SIMSwitchException: $message';
}

/// Event emitted when SIM is switched
class SIMSwitchEvent {
  final String event;
  final int? simIndex;
  final int? fromSIM;
  final int? toSIM;
  final String? reason;
  final int timestamp;

  SIMSwitchEvent({
    required this.event,
    this.simIndex,
    this.fromSIM,
    this.toSIM,
    this.reason,
    required this.timestamp,
  });

  factory SIMSwitchEvent.fromMap(Map<String, dynamic> map) {
    return SIMSwitchEvent(
      event: map['event'] as String,
      simIndex: map['simIndex'] as int?,
      fromSIM: map['fromSIM'] as int?,
      toSIM: map['toSIM'] as int?,
      reason: map['reason'] as String?,
      timestamp: map['timestamp'] as int,
    );
  }

  @override
  String toString() {
    return 'SIMSwitchEvent(event: $event, simIndex: $simIndex, fromSIM: $fromSIM, toSIM: $toSIM, reason: $reason, timestamp: $timestamp)';
  }
}
