import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'flutter_mapbox_method_channel.dart';

abstract class FlutterMapboxPlatform extends PlatformInterface {
  /// Constructs a FlutterMapboxPlatform.
  FlutterMapboxPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterMapboxPlatform _instance = MethodChannelFlutterMapbox();

  /// The default instance of [FlutterMapboxPlatform] to use.
  ///
  /// Defaults to [MethodChannelFlutterMapbox].
  static FlutterMapboxPlatform get instance => _instance;
  
  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FlutterMapboxPlatform] when
  /// they register themselves.
  static set instance(FlutterMapboxPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
