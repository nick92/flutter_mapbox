#import "FlutterMapboxPlugin.h"
#if __has_include(<flutter_mapbox/flutter_mapbox-Swift.h>)
#import <flutter_mapbox/flutter_mapbox-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "flutter_mapbox-Swift.h"
#endif

@implementation FlutterMapboxPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftFlutterMapboxPlugin registerWithRegistrar:registrar];
}
@end
