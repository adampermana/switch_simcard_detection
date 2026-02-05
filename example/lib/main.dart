import 'dart:async';

import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:switch_simcard_detection/switch_simcard_detection.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _plugin = SwitchSimcardDetection();

  String _platformVersion = 'Unknown';
  int _currentSIM = -1;
  Map<String, dynamic> _simStatus = {};
  Map<String, bool> _permissions = {};
  String _networkQuality = 'Unknown';
  Map<String, dynamic> _networkInfo = {};
  bool _isRooted = false;
  bool _canSwitch = false;
  bool _autoSwitchEnabled = false;

  final List<String> _logs = [];
  StreamSubscription<SIMSwitchEvent>? _eventSubscription;

  @override
  void initState() {
    super.initState();
    initPlatformState();
    _listenToEvents();
  }

  @override
  void dispose() {
    _eventSubscription?.cancel();
    super.dispose();
  }

  void _addLog(String message) {
    setState(() {
      _logs.insert(
          0, '[${DateTime.now().toString().substring(11, 19)}] $message');
      if (_logs.length > 50) _logs.removeLast();
    });
  }

  void _listenToEvents() {
    _eventSubscription = _plugin.onSIMSwitched.listen((event) {
      _addLog('📡 Event: ${event.event}');
      if (event.event == 'simSwitched') {
        _addLog('   Switched to SIM ${(event.simIndex ?? 0) + 1}');
      } else if (event.event == 'autoSwitched') {
        _addLog(
            '   Auto-switched from SIM ${(event.fromSIM ?? 0) + 1} to SIM ${(event.toSIM ?? 0) + 1}');
        _addLog('   Reason: ${event.reason}');
      } else if (event.event == 'networkRestored') {
        _addLog('   Network restored on SIM ${(event.simIndex ?? 0) + 1}');
      }
      _refreshAll();
    });
  }

  Future<void> initPlatformState() async {
    try {
      final version = await _plugin.getPlatformVersion() ?? 'Unknown';
      setState(() => _platformVersion = version);
      _addLog('✓ Platform: $version');

      await _refreshAll();
    } catch (e) {
      _addLog('✗ Error initializing: $e');
    }
  }

  Future<void> _refreshAll() async {
    await _refreshCurrentSIM();
    await _refreshSIMStatus();
    await _refreshPermissions();
    await _refreshNetworkInfo();
    await _checkRooted();
  }

  Future<void> _refreshCurrentSIM() async {
    try {
      final sim = await _plugin.getCurrentDataSIM();
      setState(() => _currentSIM = sim);
      _addLog('Current SIM: ${sim + 1}');
    } catch (e) {
      _addLog('✗ Error getting current SIM: $e');
    }
  }

  Future<void> _refreshSIMStatus() async {
    try {
      final status = await _plugin.getSIMStatus();
      setState(() => _simStatus = status);
      _addLog('SIM Status refreshed');
    } catch (e) {
      _addLog('✗ Error getting SIM status: $e');
    }
  }

  Future<void> _refreshPermissions() async {
    try {
      final perms = await _plugin.checkPermissions();
      final canSwitch = await _plugin.canSwitchSIM();
      setState(() {
        _permissions = perms;
        _canSwitch = canSwitch;
      });
      _addLog(
          'Permissions: ${perms.values.where((v) => v).length}/${perms.length} granted');
    } catch (e) {
      _addLog('✗ Error checking permissions: $e');
    }
  }

  Future<void> _refreshNetworkInfo() async {
    try {
      final quality = await _plugin.getNetworkQuality();
      final info = await _plugin.getNetworkInfo();
      setState(() {
        _networkQuality = quality;
        _networkInfo = info;
      });
      _addLog('Network quality: $quality');
    } catch (e) {
      _addLog('✗ Error getting network info: $e');
    }
  }

  Future<void> _checkRooted() async {
    try {
      final rooted = await _plugin.isDeviceRooted();
      setState(() => _isRooted = rooted);
      _addLog('Device rooted: $rooted');
    } catch (e) {
      _addLog('✗ Error checking root: $e');
    }
  }

  Future<void> _switchSIM(int simIndex) async {
    try {
      _addLog('Switching to SIM ${simIndex + 1}...');
      final success = await _plugin.switchDataSIM(simIndex);
      if (success) {
        _addLog('✓ Successfully switched to SIM ${simIndex + 1}');
        await _refreshAll();
      } else {
        _addLog('✗ Failed to switch to SIM ${simIndex + 1}');
      }
    } on PermissionDeniedException catch (e) {
      _addLog('✗ Permission denied: $e');
    } on SIMSwitchException catch (e) {
      _addLog('✗ Switch failed: $e');
    } catch (e) {
      _addLog('✗ Error: $e');
    }
  }

  Future<void> _toggleAutoSwitch() async {
    try {
      if (_autoSwitchEnabled) {
        _addLog('Disabling auto-switch...');
        await _plugin.disableAutoSwitch();
        setState(() => _autoSwitchEnabled = false);
        _addLog('✓ Auto-switch disabled');
      } else {
        _addLog('Enabling auto-switch (Primary: SIM1, Fallback: SIM2)...');
        await _plugin.enableAutoSwitch(primarySIM: 0, fallbackSIM: 1);
        setState(() => _autoSwitchEnabled = true);
        _addLog('✓ Auto-switch enabled');
      }
    } on PermissionDeniedException catch (e) {
      _addLog('✗ Permission denied: $e');
    } catch (e) {
      _addLog('✗ Error: $e');
    }
  }

  Future<void> _requestPermissions() async {
    _addLog('Requesting runtime permissions...');

    final statuses = await [
      Permission.phone,
      Permission.location,
    ].request();

    _addLog('Runtime permissions requested');
    await _refreshPermissions();
  }

  void _showPermissionDialog() async {
    final instructions = await _plugin.getPermissionInstructions();

    // Wait for next frame to ensure MaterialApp is built
    await Future.delayed(const Duration(milliseconds: 100));

    if (!mounted) return;

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Permission Required'),
        content: SingleChildScrollView(
          child: Text(instructions),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: Scaffold(
        appBar: AppBar(
          title: const Text('SIM Card Switcher'),
          backgroundColor: Theme.of(context).colorScheme.inversePrimary,
          actions: [
            IconButton(
              icon: const Icon(Icons.refresh),
              onPressed: _refreshAll,
              tooltip: 'Refresh All',
            ),
          ],
        ),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // Platform Info
            _buildCard(
              title: 'Platform Info',
              icon: Icons.info_outline,
              children: [
                _buildInfoRow('Platform', _platformVersion),
                _buildInfoRow('Device Rooted', _isRooted ? 'Yes' : 'No'),
                _buildInfoRow('Can Switch SIM', _canSwitch ? 'Yes ✓' : 'No ✗'),
              ],
            ),

            const SizedBox(height: 16),

            // Current SIM
            _buildCard(
              title: 'Current SIM',
              icon: Icons.sim_card,
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
                        ),
                      ),
                      const SizedBox(height: 16),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                        children: [
                          Expanded(
                            child: Padding(
                              padding: const EdgeInsets.only(right: 4.0),
                              child: ElevatedButton.icon(
                                onPressed:
                                    _canSwitch ? () => _switchSIM(0) : null,
                                icon: const Icon(Icons.sim_card, size: 18),
                                label: const Text('SIM 1'),
                                style: ElevatedButton.styleFrom(
                                  backgroundColor:
                                      _currentSIM == 0 ? Colors.green : null,
                                ),
                              ),
                            ),
                          ),
                          Expanded(
                            child: Padding(
                              padding: const EdgeInsets.only(left: 4.0),
                              child: ElevatedButton.icon(
                                onPressed:
                                    _canSwitch ? () => _switchSIM(1) : null,
                                icon: const Icon(Icons.sim_card, size: 18),
                                label: const Text('SIM 2'),
                                style: ElevatedButton.styleFrom(
                                  backgroundColor:
                                      _currentSIM == 1 ? Colors.green : null,
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),

            const SizedBox(height: 16),

            // Auto Switch
            _buildCard(
              title: 'Automatic Switching',
              icon: Icons.autorenew,
              children: [
                SwitchListTile(
                  title: const Text('Enable Auto-Switch'),
                  subtitle: Text(_autoSwitchEnabled
                      ? 'Primary: SIM1, Fallback: SIM2'
                      : 'Disabled'),
                  value: _autoSwitchEnabled,
                  onChanged: _canSwitch ? (_) => _toggleAutoSwitch() : null,
                ),
                if (_autoSwitchEnabled)
                  const Padding(
                    padding: EdgeInsets.all(8.0),
                    child: Text(
                      'Plugin will automatically switch to SIM2 if SIM1 loses network.',
                      style:
                          TextStyle(fontSize: 12, fontStyle: FontStyle.italic),
                    ),
                  ),
              ],
            ),

            const SizedBox(height: 16),

            // Network Info
            _buildCard(
              title: 'Network Status',
              icon: Icons.signal_cellular_alt,
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
              icon: Icons.security,
              children: [
                ..._permissions.entries.map((e) => _buildInfoRow(
                      e.key,
                      e.value ? '✓ Granted' : '✗ Denied',
                      valueColor: e.value ? Colors.green : Colors.red,
                    )),
                const SizedBox(height: 8),
                ElevatedButton.icon(
                  onPressed: _requestPermissions,
                  icon: const Icon(Icons.vpn_key),
                  label: const Text('Request Permissions'),
                ),
                if (!_canSwitch)
                  TextButton(
                    onPressed: _showPermissionDialog,
                    child: const Text('Show ADB Instructions'),
                  ),
              ],
            ),

            const SizedBox(height: 16),

            // SIM Status
            _buildCard(
              title: 'SIM Status Details',
              icon: Icons.settings_cell,
              children: _simStatus.entries
                  .map((e) => _buildInfoRow(
                        e.key,
                        e.value.toString(),
                      ))
                  .toList(),
            ),

            const SizedBox(height: 16),

            // Logs
            _buildCard(
              title: 'Event Logs',
              icon: Icons.list_alt,
              children: [
                Container(
                  height: 200,
                  decoration: BoxDecoration(
                    color: Colors.black87,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: ListView.builder(
                    padding: const EdgeInsets.all(8),
                    itemCount: _logs.length,
                    itemBuilder: (context, index) => Text(
                      _logs[index],
                      style: const TextStyle(
                        color: Colors.greenAccent,
                        fontFamily: 'monospace',
                        fontSize: 12,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                ElevatedButton.icon(
                  onPressed: () => setState(() => _logs.clear()),
                  icon: const Icon(Icons.clear_all),
                  label: const Text('Clear Logs'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCard({
    required String title,
    required IconData icon,
    required List<Widget> children,
  }) {
    return Card(
      elevation: 2,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(icon, size: 24),
                const SizedBox(width: 8),
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
            const Divider(),
            ...children,
          ],
        ),
      ),
    );
  }

  Widget _buildInfoRow(String label, String value, {Color? valueColor}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: const TextStyle(fontWeight: FontWeight.w500),
          ),
          Text(
            value,
            style: TextStyle(color: valueColor),
          ),
        ],
      ),
    );
  }
}
