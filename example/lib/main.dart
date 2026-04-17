import 'dart:async';
import 'dart:ui';

import 'package:flutter/cupertino.dart';
import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:switch_simcard_detection/switch_simcard_detection.dart';

// ─────────────────────────────────────────────────────────────────────────────
// Background isolate entry point
// WAJIB @pragma agar tidak di-tree-shake saat release build
// ─────────────────────────────────────────────────────────────────────────────

@pragma('vm:entry-point')
void onStart(ServiceInstance service) async {
  DartPluginRegistrant.ensureInitialized();

  if (service is AndroidServiceInstance) {
    service.setAsForegroundService();
    service.setForegroundNotificationInfo(
      title: 'SIM Monitor',
      content: 'Monitoring SIM network for auto-switching...',
    );
  }

  final plugin = SwitchSimcardDetection();

  try {
    await plugin.enableAutoSwitch(primarySIM: 0, fallbackSIM: 1);
    debugPrint('[BgService] Auto-switch enabled');
  } catch (e) {
    debugPrint('[BgService] Failed to enable auto-switch: $e');
  }

  plugin.onSIMSwitched.listen((event) {
    service.invoke('simEvent', {
      'event': event.event,
      'simIndex': event.simIndex,
      'fromSIM': event.fromSIM,
      'toSIM': event.toSIM,
      'reason': event.reason,
      'timestamp': DateTime.now().millisecondsSinceEpoch,
    });
  });

  service.on('stopService').listen((_) async {
    await plugin.disableAutoSwitch();
    await service.stopSelf();
  });

  service.on('updateSIM').listen((data) async {
    if (data == null) return;
    final primarySIM = data['primarySIM'] as int? ?? 0;
    final fallbackSIM = data['fallbackSIM'] as int? ?? 1;
    await plugin.disableAutoSwitch();
    await plugin.enableAutoSwitch(
        primarySIM: primarySIM, fallbackSIM: fallbackSIM);
  });

  Timer.periodic(const Duration(seconds: 30), (_) async {
    try {
      final sim = await plugin.getCurrentDataSIM();
      final quality = await plugin.getNetworkQuality();

      if (service is AndroidServiceInstance) {
        service.setForegroundNotificationInfo(
          title: 'SIM Monitor',
          content: 'Current SIM: ${sim + 1} | Quality: $quality',
        );
      }

      service.invoke('heartbeat', {'currentSIM': sim, 'quality': quality});
    } catch (_) {}
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Setup background service
// ─────────────────────────────────────────────────────────────────────────────

Future<void> initBackgroundService() async {
  final service = FlutterBackgroundService();

  await service.configure(
    androidConfiguration: AndroidConfiguration(
      onStart: onStart,
      isForegroundMode: true,
      autoStartOnBoot: true,
      // false: distart manual dari _ensureServiceRunning() di initState
      // setelah notification channel dibuat di MainActivity.onCreate()
      autoStart: false,
      notificationChannelId: 'sim_monitor_channel',
      initialNotificationTitle: 'SIM Monitor',
      initialNotificationContent:
          'Monitoring SIM network for auto-switching...',
      foregroundServiceNotificationId: 888,
    ),
    iosConfiguration: IosConfiguration(autoStart: false),
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// main()
// ─────────────────────────────────────────────────────────────────────────────

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initBackgroundService();
  runApp(const MyApp());
}

// ─────────────────────────────────────────────────────────────────────────────
// App
// ─────────────────────────────────────────────────────────────────────────────

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _plugin = SwitchSimcardDetection();
  final _bgService = FlutterBackgroundService();

  String _platformVersion = 'Unknown';
  int _currentSIM = -1;
  Map<String, dynamic> _simStatus = {};
  Map<String, bool> _permissions = {};
  String _networkQuality = 'Unknown';
  Map<String, dynamic> _networkInfo = {};
  bool _isRooted = false;
  bool _canSwitch = false;
  bool bgServiceRunning = false;

  final List<String> _logs = [];
  StreamSubscription<Map<String, dynamic>?>? _bgEventSub;
  StreamSubscription<Map<String, dynamic>?>? _bgHeartbeatSub;
  Timer? _autoRefreshTimer;

  @override
  void initState() {
    super.initState();
    initPlatformState();
    _listenToBackgroundService();
    _startAutoRefresh();
    // Auto-start — aman karena channel sudah dibuat di MainActivity.onCreate()
    _ensureServiceRunning();
  }

  @override
  void dispose() {
    _bgEventSub?.cancel();
    _bgHeartbeatSub?.cancel();
    _autoRefreshTimer?.cancel();
    super.dispose();
  }

  Future<void> _ensureServiceRunning() async {
    final running = await _bgService.isRunning();
    if (!running) {
      await _bgService.startService();
      _addLog('Background service started automatically');
    }
    final nowRunning = await _bgService.isRunning();
    if (mounted) setState(() => bgServiceRunning = nowRunning);
  }

  void _startAutoRefresh() {
    _autoRefreshTimer = Timer.periodic(const Duration(seconds: 5), (_) async {
      _refreshAllSilent();
      final running = await _bgService.isRunning();
      if (mounted) setState(() => bgServiceRunning = running);
    });
  }

  void _addLog(String message) {
    setState(() {
      _logs.insert(
          0, '[${DateTime.now().toString().substring(11, 19)}] $message');
      if (_logs.length > 50) _logs.removeLast();
    });
  }

  void _listenToBackgroundService() {
    _bgEventSub = _bgService.on('simEvent').listen((data) {
      if (data == null) return;
      final event = data['event'] as String? ?? '';
      if (event == 'simSwitched') {
        _addLog('[BG] Switched to SIM ${(data['simIndex'] as int? ?? 0) + 1}');
      } else if (event == 'autoSwitched') {
        _addLog(
            '[BG] Auto-switched SIM${(data['fromSIM'] as int? ?? 0) + 1} → SIM${(data['toSIM'] as int? ?? 0) + 1}');
        _addLog('   Reason: ${data['reason'] ?? 'noInternet'}');
      } else if (event == 'networkRestored') {
        _addLog(
            '[BG] Network restored on SIM${(data['simIndex'] as int? ?? 0) + 1}');
      }
      _refreshAllSilent();
    });

    _bgHeartbeatSub = _bgService.on('heartbeat').listen((data) {
      if (data == null) return;
      final sim = data['currentSIM'] as int? ?? -1;
      final quality = data['quality'] as String? ?? 'UNKNOWN';
      if (mounted) {
        setState(() {
          _currentSIM = sim;
          _networkQuality = quality;
        });
      }
    });
  }

  Future<void> initPlatformState() async {
    try {
      final version = await _plugin.getPlatformVersion() ?? 'Unknown';
      final running = await _bgService.isRunning();
      setState(() {
        _platformVersion = version;
        bgServiceRunning = running;
      });
      _addLog('✓ Platform: $version');
      await _refreshAll();
    } catch (e) {
      _addLog('✗ Error initializing: $e');
    }
  }

  Future<void> _refreshAllSilent() async {
    try {
      final sim = await _plugin.getCurrentDataSIM();
      final status = await _plugin.getSIMStatus();
      final perms = await _plugin.checkPermissions();
      final canSwitch = await _plugin.canSwitchSIM();
      final quality = await _plugin.getNetworkQuality();
      final info = await _plugin.getNetworkInfo();
      final rooted = await _plugin.isDeviceRooted();
      if (mounted) {
        setState(() {
          _currentSIM = sim;
          _simStatus = status;
          _permissions = perms;
          _canSwitch = canSwitch;
          _networkQuality = quality;
          _networkInfo = info;
          _isRooted = rooted;
        });
      }
    } catch (_) {}
  }

  Future<void> _refreshAll() async {
    await _refreshAllSilent();
    _addLog('✓ Data refreshed');
  }

  Future<void> _switchSIM(int simIndex) async {
    try {
      _addLog('Switching to SIM ${simIndex + 1}...');
      final success = await _plugin.switchDataSIM(simIndex);
      if (success) {
        _addLog('Successfully switched to SIM ${simIndex + 1}');
        await _refreshAllSilent();
      } else {
        _addLog('Failed to switch to SIM ${simIndex + 1}');
      }
    } on PermissionDeniedException catch (e) {
      _addLog('Permission denied: $e');
    } on SIMSwitchException catch (e) {
      _addLog('Switch failed: $e');
    } catch (e) {
      _addLog('Error: $e');
    }
  }

  Future<void> _requestPermissions() async {
    _addLog('Requesting runtime permissions...');
    await [Permission.phone, Permission.location, Permission.notification]
        .request();
    _addLog('Runtime permissions requested');
    await _refreshAllSilent();
  }

  void _showPermissionDialog() async {
    final instructions = await _plugin.getPermissionInstructions();
    await Future.delayed(const Duration(milliseconds: 100));
    if (!mounted) return;
    showCupertinoDialog(
      context: context,
      builder: (context) => CupertinoAlertDialog(
        title: const Text('Permission Required'),
        content: SingleChildScrollView(
          child: Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(instructions, style: const TextStyle(fontSize: 13)),
          ),
        ),
        actions: [
          CupertinoDialogAction(
            onPressed: () => Navigator.pop(context),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoApp(
      title: 'SIM Card Switcher',
      theme: const CupertinoThemeData(
        primaryColor: CupertinoColors.systemBlue,
        brightness: Brightness.light,
      ),
      home: CupertinoPageScaffold(
        navigationBar: CupertinoNavigationBar(
          middle: const Text('SIM Card Switcher'),
          trailing: CupertinoButton(
            padding: EdgeInsets.zero,
            onPressed: _refreshAll,
            child: const Icon(CupertinoIcons.refresh),
          ),
        ),
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              const SizedBox(height: 16),

              // Platform Info
              _buildCard(
                title: 'Platform Info',
                icon: CupertinoIcons.info_circle,
                children: [
                  _buildInfoRow('Platform', _platformVersion),
                  _buildInfoRow('Device Rooted', _isRooted ? 'Yes' : 'No'),
                  _buildInfoRow(
                      'Can Switch SIM', _canSwitch ? 'Yes ✓' : 'No ✗'),
                ],
              ),

              const SizedBox(height: 16),

              // Current SIM
              _buildCard(
                title: 'Current SIM',
                icon: CupertinoIcons.creditcard,
                children: [
                  Center(
                    child: Column(
                      children: [
                        Text(
                          _currentSIM == -1
                              ? 'Unknown'
                              : 'SIM ${_currentSIM + 1}',
                          style: const TextStyle(
                            fontSize: 48,
                            fontWeight: FontWeight.bold,
                            color: CupertinoColors.label,
                          ),
                        ),
                        const SizedBox(height: 16),
                        // Row(
                        //   children: [
                        //     Expanded(
                        //       child: Padding(
                        //         padding: const EdgeInsets.only(right: 6),
                        //         child: CupertinoButton(
                        //           padding:
                        //               const EdgeInsets.symmetric(vertical: 10),
                        //           color: _currentSIM == 0
                        //               ? CupertinoColors.systemGreen
                        //               : CupertinoColors.systemBlue,
                        //           onPressed:
                        //               _canSwitch ? () => _switchSIM(0) : null,
                        //           child: const Text('SIM 1',
                        //               style: TextStyle(
                        //                   color: CupertinoColors.white)),
                        //         ),
                        //       ),
                        //     ),
                        //     Expanded(
                        //       child: Padding(
                        //         padding: const EdgeInsets.only(left: 6),
                        //         child: CupertinoButton(
                        //           padding:
                        //               const EdgeInsets.symmetric(vertical: 10),
                        //           color: _currentSIM == 1
                        //               ? CupertinoColors.systemGreen
                        //               : CupertinoColors.systemBlue,
                        //           onPressed:
                        //               _canSwitch ? () => _switchSIM(1) : null,
                        //           child: const Text('SIM 2',
                        //               style: TextStyle(
                        //                   color: CupertinoColors.white)),
                        //         ),
                        //       ),
                        //     ),
                        //   ],
                        // ),
                      ],
                    ),
                  ),
                ],
              ),

              const SizedBox(height: 16),

              // Network Status
              _buildCard(
                title: 'Network Status',
                icon: CupertinoIcons.antenna_radiowaves_left_right,
                children: [
                  _buildInfoRow('Quality', _networkQuality),
                  _buildInfoRow('Has Network',
                      _networkInfo['hasNetwork']?.toString() ?? 'Unknown'),
                  _buildInfoRow('Has Internet',
                      _networkInfo['hasInternet']?.toString() ?? 'Unknown'),
                  _buildInfoRow('Signal Level',
                      _networkInfo['signalLevel']?.toString() ?? 'Unknown'),
                ],
              ),

              const SizedBox(height: 16),

              // Permissions
              _buildCard(
                title: 'Permissions',
                icon: CupertinoIcons.lock_shield,
                children: [
                  ..._permissions.entries.map((e) => _buildInfoRow(
                        e.key,
                        e.value ? '✓ Granted' : '✗ Denied',
                        valueColor: e.value
                            ? CupertinoColors.systemGreen
                            : CupertinoColors.systemRed,
                      )),
                  const SizedBox(height: 10),
                  SizedBox(
                    width: double.infinity,
                    child: CupertinoButton.filled(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      onPressed: _requestPermissions,
                      child: const Text('Request Permissions'),
                    ),
                  ),
                  if (!_canSwitch) ...[
                    const SizedBox(height: 8),
                    SizedBox(
                      width: double.infinity,
                      child: CupertinoButton(
                        onPressed: _showPermissionDialog,
                        child: const Text('Show ADB Instructions'),
                      ),
                    ),
                  ],
                ],
              ),

              const SizedBox(height: 16),

              // SIM Status Details
              _buildCard(
                title: 'SIM Status Details',
                icon: CupertinoIcons.slider_horizontal_3,
                children: _simStatus.entries
                    .map((e) => _buildInfoRow(e.key, e.value.toString()))
                    .toList(),
              ),

              const SizedBox(height: 16),

              // Event Logs
              _buildCard(
                title: 'Event Logs',
                icon: CupertinoIcons.list_bullet,
                children: [
                  Container(
                    height: 200,
                    decoration: BoxDecoration(
                      color: CupertinoColors.black,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: ListView.builder(
                      padding: const EdgeInsets.all(8),
                      itemCount: _logs.length,
                      itemBuilder: (context, index) => Text(
                        _logs[index],
                        style: const TextStyle(
                          color: CupertinoColors.systemGreen,
                          fontFamily: 'monospace',
                          fontSize: 12,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  SizedBox(
                    width: double.infinity,
                    child: CupertinoButton(
                      onPressed: () => setState(() => _logs.clear()),
                      child: const Text('Clear Logs',
                          style: TextStyle(color: CupertinoColors.systemRed)),
                    ),
                  ),
                ],
              ),

              const SizedBox(height: 32),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildCard({
    required String title,
    required IconData icon,
    required List<Widget> children,
  }) {
    return Container(
      decoration: BoxDecoration(
        color: CupertinoColors.systemBackground,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: CupertinoColors.systemGrey.withOpacity(0.15),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 20, color: CupertinoColors.systemBlue),
              const SizedBox(width: 8),
              Text(
                title,
                style: const TextStyle(
                  fontSize: 17,
                  fontWeight: FontWeight.w600,
                  color: CupertinoColors.label,
                ),
              ),
            ],
          ),
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 10),
            child: SizedBox(
              height: 1,
              child: ColoredBox(color: CupertinoColors.separator),
            ),
          ),
          ...children,
        ],
      ),
    );
  }

  Widget _buildInfoRow(String label, String value, {Color? valueColor}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: const TextStyle(
              fontWeight: FontWeight.w500,
              color: CupertinoColors.secondaryLabel,
            ),
          ),
          Text(
            value,
            style: TextStyle(
              color: valueColor ?? CupertinoColors.label,
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }
}
