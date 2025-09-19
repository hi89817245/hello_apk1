
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

const MethodChannel _channel = MethodChannel('com.example.hello_apk1/camera');

const Map<String, Object> _defaults = {
  'pama0': '大鴻',
  'pama1': 3.0,
  'pama2': 1.2,
  'pama3': '完成',
  'pama4': 3.0,
  'pama5': 'Pictures/PigeonEyeRecords',
  'pama6': true,
  'pama7': '1:1',
  'pama8': 0.78,
  'pama9': 1500.0,
  'pama10': 'y',
};

const String _statsTotalKey = 'stats_totalSessions';
const String _statsUniqueKey = 'stats_uniqueCount';
const String _statsHistoryKey = 'stats_uidHistory';
const String _statsLastKey = 'stats_lastCaptureAt';
class CaptureSettings {
  CaptureSettings({
    required this.clubName,
    required this.zoomRatio,
    required this.stabilizeSeconds,
    required this.successVoice,
    required this.previewSeconds,
    required this.storagePath,
    required this.allowMultiple,
    required this.aspectRatio,
    required this.overlayScale,
    required this.detectionVariance,
    required this.allowRetake,
  });

  String clubName;
  double zoomRatio;
  double stabilizeSeconds;
  String successVoice;
  double previewSeconds;
  String storagePath;
  bool allowMultiple;
  String aspectRatio;
  double overlayScale;
  double detectionVariance;
  bool allowRetake;

  factory CaptureSettings.fromPreferences(SharedPreferences prefs) {
    return CaptureSettings(
      clubName: prefs.getString('pama0') ?? _defaults['pama0'] as String,
      zoomRatio: prefs.getDouble('pama1') ?? _defaults['pama1'] as double,
      stabilizeSeconds: prefs.getDouble('pama2') ?? _defaults['pama2'] as double,
      successVoice: prefs.getString('pama3') ?? _defaults['pama3'] as String,
      previewSeconds: prefs.getDouble('pama4') ?? _defaults['pama4'] as double,
      storagePath: prefs.getString('pama5') ?? _defaults['pama5'] as String,
      allowMultiple: prefs.getBool('pama6') ?? _defaults['pama6'] as bool,
      aspectRatio: prefs.getString('pama7') ?? _defaults['pama7'] as String,
      overlayScale: prefs.getDouble('pama8') ?? _defaults['pama8'] as double,
      detectionVariance: prefs.getDouble('pama9') ?? _defaults['pama9'] as double,
      allowRetake: (prefs.getString('pama10') ?? _defaults['pama10'] as String).toLowerCase() == 'y',
    );
  }

  CaptureSettings copyWith({
    String? clubName,
    double? zoomRatio,
    double? stabilizeSeconds,
    String? successVoice,
    double? previewSeconds,
    String? storagePath,
    bool? allowMultiple,
    String? aspectRatio,
    double? overlayScale,
    double? detectionVariance,
    bool? allowRetake,
  }) {
    return CaptureSettings(
      clubName: clubName ?? this.clubName,
      zoomRatio: zoomRatio ?? this.zoomRatio,
      stabilizeSeconds: stabilizeSeconds ?? this.stabilizeSeconds,
      successVoice: successVoice ?? this.successVoice,
      previewSeconds: previewSeconds ?? this.previewSeconds,
      storagePath: storagePath ?? this.storagePath,
      allowMultiple: allowMultiple ?? this.allowMultiple,
      aspectRatio: aspectRatio ?? this.aspectRatio,
      overlayScale: overlayScale ?? this.overlayScale,
      detectionVariance: detectionVariance ?? this.detectionVariance,
      allowRetake: allowRetake ?? this.allowRetake,
    );
  }

  Future<void> persist(SharedPreferences prefs) async {
    await prefs.setString('pama0', clubName);
    await prefs.setDouble('pama1', zoomRatio);
    await prefs.setDouble('pama2', stabilizeSeconds);
    await prefs.setString('pama3', successVoice);
    await prefs.setDouble('pama4', previewSeconds);
    await prefs.setString('pama5', storagePath);
    await prefs.setBool('pama6', allowMultiple);
    await prefs.setString('pama7', aspectRatio);
    await prefs.setDouble('pama8', overlayScale);
    await prefs.setDouble('pama9', detectionVariance);
    await prefs.setString('pama10', allowRetake ? 'y' : 'n');
  }

  Map<String, Object> toNativeConfig() {
    return {
      'pama1': zoomRatio,
      'pama2': stabilizeSeconds,
      'pama3': successVoice,
      'pama4': previewSeconds,
      'pama5': storagePath,
      'pama6': allowMultiple ? 'y' : 'n',
      'pama7': aspectRatio,
      'pama8': overlayScale,
      'pama9': detectionVariance,
      'pama10': allowRetake ? 'y' : 'n',
    };
  }
}
class CaptureStats {
  const CaptureStats({
    required this.totalSessions,
    required this.uniqueUids,
    required this.lastCaptureAt,
  });

  final int totalSessions;
  final int uniqueUids;
  final DateTime? lastCaptureAt;

  CaptureStats copyWith({
    int? totalSessions,
    int? uniqueUids,
    DateTime? lastCaptureAt,
  }) {
    return CaptureStats(
      totalSessions: totalSessions ?? this.totalSessions,
      uniqueUids: uniqueUids ?? this.uniqueUids,
      lastCaptureAt: lastCaptureAt ?? this.lastCaptureAt,
    );
  }

  static const CaptureStats empty = CaptureStats(
    totalSessions: 0,
    uniqueUids: 0,
    lastCaptureAt: null,
  );
}
void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const TelephotoApp());
}

class TelephotoApp extends StatelessWidget {
  const TelephotoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: '賽鴿虹膜建檔系統',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFFE53935)),
        useMaterial3: true,
      ),
      home: const TelephotoHomePage(),
    );
  }
}
class TelephotoHomePage extends StatefulWidget {
  const TelephotoHomePage({super.key});

  @override
  State<TelephotoHomePage> createState() => _TelephotoHomePageState();
}

class _TelephotoHomePageState extends State<TelephotoHomePage> {
  late SharedPreferences _prefs;
  CaptureSettings? _settings;
  CaptureStats _stats = CaptureStats.empty;
  final ValueNotifier<CaptureStats> _statsNotifier = ValueNotifier<CaptureStats>(CaptureStats.empty);
  final Set<String> _processedUids = <String>{};
  bool _loading = true;
  String? _lastUid;
  final TextEditingController _manualUidController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadInitialData();
  }
  Future<void> _loadInitialData() async {
    _prefs = await SharedPreferences.getInstance();
    final settings = CaptureSettings.fromPreferences(_prefs);
    final storedUids = _prefs.getStringList(_statsHistoryKey) ?? const <String>[];
    _processedUids
      ..clear()
      ..addAll(storedUids);
    final total = _prefs.getInt(_statsTotalKey) ?? storedUids.length;
    final unique = _prefs.getInt(_statsUniqueKey) ?? _processedUids.length;
    final lastIso = _prefs.getString(_statsLastKey);
    final last = lastIso == null ? null : DateTime.tryParse(lastIso);

    setState(() {
      _settings = settings;
      _stats = CaptureStats(
        totalSessions: total,
        uniqueUids: unique,
        lastCaptureAt: last,
      );
      _statsNotifier.value = _stats;
      _lastUid = _processedUids.isEmpty ? null : _processedUids.last;
      _loading = false;
    });
  }
  Future<void> _recordSession(String uid) async {
    final now = DateTime.now();
    _processedUids.add(uid);
    _stats = _stats.copyWith(
      totalSessions: _stats.totalSessions + 1,
      uniqueUids: _processedUids.length,
      lastCaptureAt: now,
    );
    _statsNotifier.value = _stats;
    setState(() {
      _lastUid = uid;
    });

    await _prefs.setInt(_statsTotalKey, _stats.totalSessions);
    await _prefs.setInt(_statsUniqueKey, _stats.uniqueUids);
    await _prefs.setStringList(_statsHistoryKey, _processedUids.toList());
    await _prefs.setString(_statsLastKey, now.toIso8601String());
  }

  Future<void> _updateSettings(CaptureSettings updated) async {
    await updated.persist(_prefs);
    setState(() {
      _settings = updated;
    });
  }

  Future<void> _openSettings() async {
    final settings = _settings;
    if (settings == null) return;
    final updated = await showSettingsSheet(context, settings);
    if (updated != null) {
      await _updateSettings(updated);
    }
  }

  Future<void> _startWorkflow({String? initialUid}) async {
    final settings = _settings;
    if (settings == null) return;
    await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (context) => CaptureWorkflowPage(
          settings: settings,
          statsNotifier: _statsNotifier,
          onRecordSession: _recordSession,
          onSettingsChanged: _updateSettings,
          initialUid: initialUid,
        ),
      ),
    );
  }
  @override
  Widget build(BuildContext context) {
    final settings = _settings;
    return Scaffold(
      appBar: AppBar(
        title: const Text('賽鴿虹膜建檔系統'),
        actions: [
          IconButton(
            tooltip: '系統參數',
            onPressed: _openSettings,
            icon: const Icon(Icons.settings),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : Padding(
              padding: const EdgeInsets.all(24),
              child: ListView(
                children: [
                  Text('系統名稱：賽鴿虹膜建檔系統', style: Theme.of(context).textTheme.titleMedium),
                  Text('鴿會名稱：'),
                  if (_lastUid != null) ...[
                    const SizedBox(height: 12),
                    Text('最近感應 UID：'),
                  ],
                  const SizedBox(height: 16),
                  ValueListenableBuilder<CaptureStats>(
                    valueListenable: _statsNotifier,
                    builder: (context, stats, _) {
                      return Card(
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('本機統計', style: Theme.of(context).textTheme.titleSmall),
                              const SizedBox(height: 8),
                              Text('累計啟動建檔： 次'),
                              Text('累計 UID 數量：'),
                              Text('最近建檔：'),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: const [
                          Text('操作說明', style: TextStyle(fontWeight: FontWeight.w600)),
                          SizedBox(height: 8),
                          Text('1. 感應賽鴿環 UID（左側讀取器）。'),
                          Text('2. 將賽鴿眼睛置於螢幕中央瞄準框內，依顏色提示進行：灰白→未偵測、金色→請保持穩定、綠色→準備拍攝。'),
                          Text('3. 拍攝完成後依語音或指示確認，若需要重拍請依設定流程操作。'),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 24),
                  FilledButton.icon(
                    onPressed: () => _startWorkflow(),
                    icon: const Icon(Icons.play_circle),
                    label: const Padding(
                      padding: EdgeInsets.symmetric(vertical: 12),
                      child: Text('開始作業流程'),
                    ),
                  ),
                  const SizedBox(height: 24),
                  ExpansionTile(
                    title: const Text('工程測試模式'),
                    subtitle: const Text('僅供除錯或無 HID 裝置時使用'),
                    children: [
                      TextField(
                        controller: _manualUidController,
                        decoration: const InputDecoration(
                          labelText: '手動輸入賽鴿 UID',
                          border: OutlineInputBorder(),
                        ),
                      ),
                      const SizedBox(height: 12),
                      SizedBox(
                        width: double.infinity,
                        child: OutlinedButton.icon(
                          onPressed: () {
                            final uid = _manualUidController.text.trim();
                            if (uid.isEmpty) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(content: Text('請先輸入 UID')),
                              );
                              return;
                            }
                            _startWorkflow(initialUid: uid);
                          },
                          icon: const Icon(Icons.warning_amber),
                          label: const Text('手動啟動測試流程'),
                        ),
                      ),
                      const SizedBox(height: 12),
                    ],
                  ),
                ],
              ),
            ),
    );
  }
}
class CaptureWorkflowPage extends StatefulWidget {
  const CaptureWorkflowPage({
    super.key,
    required this.settings,
    required this.statsNotifier,
    required this.onRecordSession,
    required this.onSettingsChanged,
    this.initialUid,
  });

  final CaptureSettings settings;
  final ValueNotifier<CaptureStats> statsNotifier;
  final Future<void> Function(String uid) onRecordSession;
  final Future<void> Function(CaptureSettings settings) onSettingsChanged;
  final String? initialUid;

  @override
  State<CaptureWorkflowPage> createState() => _CaptureWorkflowPageState();
}

class _CaptureWorkflowPageState extends State<CaptureWorkflowPage> {
  final FocusNode _hidFocusNode = FocusNode();
  final StringBuffer _hidBuffer = StringBuffer();
  CaptureSettings? _settings;
  String? _currentUid;
  String _statusMessage = '等待感應賽鴿環 UID…';
  bool _invokingSession = false;
  Timer? _elapsedTimer;
  Duration _elapsed = Duration.zero;

  @override
  void initState() {
    super.initState();
    _settings = widget.settings;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _hidFocusNode.requestFocus();
      final initial = widget.initialUid?.trim();
      if (initial != null && initial.isNotEmpty) {
        _handleUid(initial);
      }
    });
  }

  @override
  void dispose() {
    _elapsedTimer?.cancel();
    _hidFocusNode.dispose();
    super.dispose();
  }
  void _startElapsedTimer() {
    _elapsedTimer?.cancel();
    _elapsed = Duration.zero;
    _elapsedTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      setState(() {
        _elapsed = Duration(seconds: timer.tick);
      });
    });
  }

  Future<void> _handleUid(String uid) async {
    if (_invokingSession) return;
    setState(() {
      _currentUid = uid;
      _statusMessage = 'UID：\n請依照螢幕與語音指示瞄準虹膜';
      _invokingSession = true;
    });
    await widget.onRecordSession(uid);
    _startElapsedTimer();
    try {
      final config = _settings?.toNativeConfig() ?? {};
      await _channel.invokeMethod('startCaptureSession', {
        'uid': uid,
        'config': config,
      });
      setState(() {
        _statusMessage = 'UID：\n等待虹膜對準…請跟隨顏色框指示';
      });
    } on PlatformException catch (error) {
      setState(() {
        _statusMessage = '啟動相機失敗：';
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_statusMessage)),
      );
    } finally {
      setState(() {
        _invokingSession = false;
      });
    }
  }

  void _handleRawKeyEvent(RawKeyEvent event) {
    if (event is! RawKeyDownEvent) return;
    if (event.logicalKey == LogicalKeyboardKey.enter) {
      final uid = _hidBuffer.toString().trim();
      _hidBuffer.clear();
      if (uid.isNotEmpty) {
        _handleUid(uid);
      }
      return;
    }
    String? character = event.character;
    if (event.data is RawKeyEventDataAndroid && (character == null || character.isEmpty)) {
      character = (event.data as RawKeyEventDataAndroid).keyLabel;
    }
    if (character != null && character.isNotEmpty && character.codeUnitAt(0) >= 32) {
      _hidBuffer.write(character);
    }
  }

  Future<void> _openSettings() async {
    final settings = _settings;
    if (settings == null) return;
    final updated = await showSettingsSheet(context, settings);
    if (updated != null) {
      await widget.onSettingsChanged(updated);
      setState(() {
        _settings = updated;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('設定已更新，下一次啟動生效。')),
      );
    }
  }
  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return RawKeyboardListener(
      focusNode: _hidFocusNode,
      onKey: _handleRawKeyEvent,
      child: Scaffold(
        appBar: AppBar(
          title: Text(_currentUid == null ? '等待感應' : 'UID：'),
          actions: [
            IconButton(
              tooltip: '設定',
              onPressed: _openSettings,
              icon: const Icon(Icons.settings_suggest),
            ),
          ],
        ),
        body: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ValueListenableBuilder<CaptureStats>(
                valueListenable: widget.statsNotifier,
                builder: (_, stats, __) {
                  return Row(
                    children: [
                      _InfoChip(label: '累計建檔', value: ''),
                      const SizedBox(width: 12),
                      _InfoChip(label: '唯一 UID', value: ''),
                      const SizedBox(width: 12),
                      _InfoChip(label: '已用時間', value: _formatDuration(_elapsed)),
                    ],
                  );
                },
              ),
              const SizedBox(height: 24),
              Text(
                _statusMessage,
                style: theme.textTheme.titleMedium,
              ),
              const SizedBox(height: 16),
              Text(
                '若語音提示超過 30 秒仍無法完成，系統會自動請作業人員協助。',
                style: theme.textTheme.bodySmall,
              ),
              const Spacer(),
              Row(
                children: [
                  Expanded(
                    child: FilledButton.icon(
                      onPressed: _invokingSession
                          ? null
                          : () {
                              setState(() {
                                _currentUid = null;
                                _statusMessage = '等待感應賽鴿環 UID…';
                                _elapsedTimer?.cancel();
                                _elapsed = Duration.zero;
                              });
                            },
                      icon: const Icon(Icons.restart_alt),
                      label: const Padding(
                        padding: EdgeInsets.symmetric(vertical: 12),
                        child: Text('等待下一個 UID'),
                      ),
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: () => Navigator.of(context).pop(),
                      icon: const Icon(Icons.logout),
                      label: const Padding(
                        padding: EdgeInsets.symmetric(vertical: 12),
                        child: Text('結束並返回首頁'),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
class _InfoChip extends StatelessWidget {
  const _InfoChip({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: theme.colorScheme.primaryContainer,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: theme.textTheme.labelSmall),
          Text(
            value,
            style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
          ),
        ],
      ),
    );
  }
}

Future<CaptureSettings?> showSettingsSheet(BuildContext context, CaptureSettings settings) {
  final TextEditingController voiceController = TextEditingController(text: settings.successVoice);
  final TextEditingController pathController = TextEditingController(text: settings.storagePath);
  final TextEditingController clubController = TextEditingController(text: settings.clubName);
  CaptureSettings current = settings;

  return showModalBottomSheet<CaptureSettings>(
    context: context,
    isScrollControlled: true,
    builder: (context) {
      return StatefulBuilder(
        builder: (context, setState) {
          return Padding(
            padding: EdgeInsets.only(
              left: 24,
              right: 24,
              bottom: 24 + MediaQuery.of(context).viewInsets.bottom,
              top: 24,
            ),
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('系統參數設定', style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: 16),
                  TextField(
                    controller: clubController,
                    decoration: const InputDecoration(
                      labelText: 'pama0 － 鴿會名稱',
                      border: OutlineInputBorder(),
                    ),
                    onChanged: (value) {
                      setState(() {
                        current = current.copyWith(clubName: value.trim().isEmpty ? settings.clubName : value.trim());
                      });
                    },
                  ),
                  const SizedBox(height: 16),
                  _SettingsSlider(
                    label: 'pama1 － 鏡頭預設倍率',
                    min: 1,
                    max: 10,
                    step: 0.1,
                    value: current.zoomRatio,
                    display: '${current.zoomRatio.toStringAsFixed(1)}x',
                    onChanged: (value) {
                      setState(() {
                        current = current.copyWith(zoomRatio: value);
                      });
                    },
                  ),
                  _SettingsSlider(
                    label: 'pama2 － 穩定判定秒數',
                    min: 0.2,
                    max: 2.0,
                    step: 0.1,
                    value: current.stabilizeSeconds,
                    display: '${current.stabilizeSeconds.toStringAsFixed(1)} 秒',
                    onChanged: (value) {
                      setState(() {
                        current = current.copyWith(stabilizeSeconds: value);
                      });
                    },
                  ),
                  _SettingsSlider(
                    label: 'pama4 － 拍攝後預覽秒數',
                    min: 3,
                    max: 30,
                    step: 1,
                    value: current.previewSeconds,
                    display: '${current.previewSeconds.toStringAsFixed(0)} 秒',
                    onChanged: (value) {
                      setState(() {
                        current = current.copyWith(previewSeconds: value);
                      });
                    },
                  ),
                  _SettingsSlider(
                    label: 'pama8 － 瞄準框尺寸佔比',
                    min: 0.4,
                    max: 0.95,
                    step: 0.01,
                    value: current.overlayScale,
                    display: '${(current.overlayScale * 100).toStringAsFixed(0)}%',
                    onChanged: (value) {
                      setState(() {
                        current = current.copyWith(overlayScale: value);
                      });
                    },
                  ),
                  _SettingsSlider(
                    label: 'pama9 － 虹膜偵測敏感度',
                    min: 300,
                    max: 4000,
                    step: 50,
                    value: current.detectionVariance,
                    display: current.detectionVariance.toStringAsFixed(0),
                    onChanged: (value) {
                      setState(() {
                        current = current.copyWith(detectionVariance: value);
                      });
                    },
                  ),
                  SwitchListTile(
                    title: const Text('pama6 － 同一 UID 允許多張'),
                    value: current.allowMultiple,
                    onChanged: (value) {
                      setState(() {
                        current = current.copyWith(allowMultiple: value);
                      });
                    },
                  ),
                  SwitchListTile(
                    title: const Text('pama10 － 感應後允許重拍'),
                    value: current.allowRetake,
                    onChanged: (value) {
                      setState(() {
                        current = current.copyWith(allowRetake: value);
                      });
                    },
                  ),
                  DropdownButtonFormField<String>(
                    value: current.aspectRatio,
                    decoration: const InputDecoration(
                      labelText: 'pama7 － 圖片比例',
                      border: OutlineInputBorder(),
                    ),
                    items: const [
                      DropdownMenuItem(value: '1:1', child: Text('1:1 正方形')),
                      DropdownMenuItem(value: '4:3', child: Text('4:3 標準')),
                      DropdownMenuItem(value: '16:9', child: Text('16:9 寬')),
                    ],
                    onChanged: (value) {
                      if (value == null) return;
                      setState(() {
                        current = current.copyWith(aspectRatio: value);
                      });
                    },
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: voiceController,
                    decoration: const InputDecoration(
                      labelText: 'pama3 － 成功語音內容',
                      border: OutlineInputBorder(),
                    ),
                    onChanged: (value) {
                      current = current.copyWith(successVoice: value.isEmpty ? settings.successVoice : value);
                    },
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: pathController,
                    decoration: const InputDecoration(
                      labelText: 'pama5 － 圖片儲存路徑',
                      border: OutlineInputBorder(),
                    ),
                    onChanged: (value) {
                      current = current.copyWith(storagePath: value.isEmpty ? settings.storagePath : value);
                    },
                  ),
                  const SizedBox(height: 24),
                  Row(
                    children: [
                      Expanded(
                        child: TextButton(
                          onPressed: () => Navigator.of(context).pop(),
                          child: const Text('取消'),
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: FilledButton(
                          onPressed: () => Navigator.of(context).pop(current),
                          child: const Text('儲存設定'),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          );
        },
      );
    },
  );
}

class _SettingsSlider extends StatelessWidget {
  const _SettingsSlider({
    required this.label,
    required this.value,
    required this.min,
    required this.max,
    required this.step,
    required this.display,
    required this.onChanged,
  });

  final String label;
  final double value;
  final double min;
  final double max;
  final double step;
  final String display;
  final ValueChanged<double> onChanged;

  @override
  Widget build(BuildContext context) {
    final divisions = ((max - min) / step).round();
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: Theme.of(context).textTheme.titleSmall),
          Row(
            children: [
              Expanded(
                child: Slider(
                  value: value,
                  min: min,
                  max: max,
                  divisions: divisions > 0 ? divisions : null,
                  label: display,
                  onChanged: onChanged,
                ),
              ),
              SizedBox(
                width: 80,
                child: Text(
                  display,
                  textAlign: TextAlign.end,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

String _formatDateTime(DateTime? value) {
  if (value == null) {
    return '尚未紀錄';
  }
  final month = value.month.toString().padLeft(2, '0');
  final day = value.day.toString().padLeft(2, '0');
  final hour = value.hour.toString().padLeft(2, '0');
  final minute = value.minute.toString().padLeft(2, '0');
  return '${value.year}/$month/$day $hour:$minute';
}

String _formatDuration(Duration duration) {
  final minutes = duration.inMinutes.remainder(60).toString().padLeft(2, '0');
  final seconds = duration.inSeconds.remainder(60).toString().padLeft(2, '0');
  final hours = duration.inHours;
  if (hours > 0) {
    final hourStr = hours.toString().padLeft(2, '0');
    return '$hourStr:$minutes:$seconds';
  }
  return '$minutes:$seconds';
}
