import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_mapbox/flutter_mapbox.dart';
import 'package:flutter_mapbox/flutter_mapbox_platform_interface.dart';
import 'package:flutter_mapbox/flutter_mapbox_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockFlutterMapboxPlatform 
    with MockPlatformInterfaceMixin
    implements FlutterMapboxPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final FlutterMapboxPlatform initialPlatform = FlutterMapboxPlatform.instance;

  test('$MethodChannelFlutterMapbox is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelFlutterMapbox>());
  });

  test('getPlatformVersion', () async {
    FlutterMapbox flutterMapboxPlugin = FlutterMapbox();
    MockFlutterMapboxPlatform fakePlatform = MockFlutterMapboxPlatform();
    FlutterMapboxPlatform.instance = fakePlatform;
  
    expect(await flutterMapboxPlugin.getPlatformVersion(), '42');
  });
}
