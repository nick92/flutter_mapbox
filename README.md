# flutter_mapbox

[![pub.dev](https://img.shields.io/pub/v/flutter_mapbox.svg)](https://pub.dev/packages/flutter_mapbox)
[![Platform](https://img.shields.io/badge/platform-android%20%7C%20ios-blue.svg)](https://pub.dev/packages/flutter_mapbox)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Add turn-by-turn navigation to your Flutter app using the Mapbox Navigation SDK — without ever leaving your app.

Powered by **Mapbox Navigation SDK v3.23.0** on Android and the equivalent iOS release.

---

## Features

- Embedded map view with full turn-by-turn navigation
- Full-screen navigation mode
- Alternative route selection
- Voice and banner instructions
- Real-time route progress events
- Vehicle dimension restrictions (height, width, weight)
- Route avoidance (motorways, tolls, ferries, custom points)
- Point of interest (POI) annotations with custom icons
- Long-press to set destination
- Free-drive / passive navigation mode
- U-turn support at intermediate waypoints
- Custom day/night map styles
- Route simulation
- Multi-language support

---

## pub.dev

[https://pub.dev/packages/flutter_mapbox](https://pub.dev/packages/flutter_mapbox)

---

## Getting Started

### 1. Add the dependency

```yaml
dependencies:
  flutter_mapbox: ^0.9.6
```

### 2. Mapbox Access Token

You need a [Mapbox account](https://account.mapbox.com/) and a **secret downloads token** (with the `DOWNLOADS:READ` scope) to pull the native SDKs.

#### Android

Add your secret token to `~/.gradle/gradle.properties`:

```properties
MAPBOX_DOWNLOADS_TOKEN=sk.your_secret_token_here
```

Add your public access token to `android/app/src/main/res/values/strings.xml`:

```xml
<string name="mapbox_access_token">pk.your_public_token_here</string>
```

#### iOS

Add your secret token to `~/.netrc`:

```
machine api.mapbox.com
  login mapbox
  password sk.your_secret_token_here
```

Add your public access token to your `Info.plist`:

```xml
<key>MBXAccessToken</key>
<string>pk.your_public_token_here</string>
```

### 3. Platform configuration

#### Android

Set the minimum SDK version in `android/app/build.gradle`:

```gradle
android {
    defaultConfig {
        minSdkVersion 21
    }
}
```

Add location permissions to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

#### iOS

Set the minimum deployment target to **iOS 14** in your `Podfile`:

```ruby
platform :ios, '14.0'
```

Add location usage descriptions to `Info.plist`:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>This app needs your location for navigation.</string>
<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>This app needs your location for navigation.</string>
```

---

## Usage

### Basic setup

```dart
import 'package:flutter_mapbox/flutter_mapbox.dart';
```

### Embedded navigation view

```dart
MapBoxOptions options = MapBoxOptions(
  initialLatitude: 36.1175275,
  initialLongitude: -115.1839524,
  zoom: 13.0,
  tilt: 0.0,
  bearing: 0.0,
  enableRefresh: true,
  alternatives: true,
  voiceInstructionsEnabled: true,
  bannerInstructionsEnabled: true,
  allowsUTurnAtWayPoints: true,
  mode: MapBoxNavigationMode.drivingWithTraffic,
  units: VoiceUnits.imperial,
  simulateRoute: false,
  longPressDestinationEnabled: true,
  mapStyleUrlDay: "mapbox://styles/mapbox/navigation-day-v1",
  mapStyleUrlNight: "mapbox://styles/mapbox/navigation-night-v1",
  language: "en",
);

MapBoxNavigationView(
  options: options,
  onRouteEvent: _onRouteEvent,
  onCreated: (MapBoxNavigationViewController controller) async {
    _controller = controller;
    await _controller.initialize();
  },
);
```

### Building a route

```dart
final origin = WayPoint(
  id: "1",
  name: "Origin",
  latitude: 53.2110237,
  longitude: -2.8944236,
);
final destination = WayPoint(
  id: "2",
  name: "Destination",
  latitude: 53.701198,
  longitude: -2.461296,
);

await _controller.buildRoute(
  wayPoints: [origin, destination],
  options: options,
);
```

### Starting navigation

```dart
// Embedded (in-view) navigation
await _controller.startNavigation();

// Full-screen navigation
await _controller.startFullScreenNavigation();
```

### Listening to route events

```dart
Future<void> _onRouteEvent(RouteEvent e) async {
  switch (e.eventType) {
    case MapBoxEvent.route_built:
      // Route is ready
      break;
    case MapBoxEvent.navigation_running:
      // Navigation started
      break;
    case MapBoxEvent.progress_change:
      final progress = e.data as RouteProgressEvent;
      print(progress.currentStepInstruction);
      break;
    case MapBoxEvent.on_arrival:
      await _controller.finishNavigation();
      break;
    case MapBoxEvent.navigation_finished:
    case MapBoxEvent.navigation_cancelled:
      // Navigation ended
      break;
    default:
      break;
  }
}
```

### Adding POI annotations

```dart
await _controller.setPOI(
  groupName: "fuel",
  iconSize: 0.2,
  image: base64EncodedPngString,
  wayPoints: poiList,
);

// Remove POIs
await _controller.removePOI(groupName: "fuel");
```

### Vehicle dimension restrictions

```dart
options.maxHeight = "4.5"; // metres
options.maxWidth = "2.5";  // metres
options.maxWeight = "3.5"; // metric tons
```

### Route avoidance

```dart
MapBoxOptions(
  avoid: ["motorway", "toll", "ferry"],
  // ...
);
```

---

## API Reference

### `MapBoxOptions`

| Property | Type | Description |
|---|---|---|
| `initialLatitude` | `double?` | Starting map latitude |
| `initialLongitude` | `double?` | Starting map longitude |
| `zoom` | `double?` | Initial zoom level (0–22) |
| `bearing` | `double?` | Camera bearing in degrees |
| `tilt` | `double?` | Camera tilt (0–60°) |
| `language` | `String?` | ISO 639-1 language code for instructions |
| `mode` | `MapBoxNavigationMode?` | Navigation mode (driving, cycling, walking) |
| `units` | `VoiceUnits?` | `imperial` or `metric` |
| `alternatives` | `bool?` | Show alternative routes |
| `simulateRoute` | `bool?` | Simulate driving the route |
| `voiceInstructionsEnabled` | `bool?` | Enable voice prompts |
| `bannerInstructionsEnabled` | `bool?` | Enable turn banners |
| `allowsUTurnAtWayPoints` | `bool?` | Allow U-turns at waypoints |
| `longPressDestinationEnabled` | `bool?` | Set destination via long press |
| `enableFreeDriveMode` | `bool?` | Passive/free-drive navigation |
| `mapStyleUrlDay` | `String?` | Mapbox style URL for day |
| `mapStyleUrlNight` | `String?` | Mapbox style URL for night |
| `maxHeight` | `String?` | Max vehicle height in metres |
| `maxWidth` | `String?` | Max vehicle width in metres |
| `maxWeight` | `String?` | Max vehicle weight in metric tons |
| `avoid` | `List<String>?` | Road types or points to avoid |
| `padding` | `EdgeInsets?` | Map view padding |

### `MapBoxNavigationViewController`

| Method / Property | Description |
|---|---|
| `initialize()` | Start listening for route events |
| `buildRoute(wayPoints, options)` | Build a route between waypoints |
| `startNavigation()` | Start embedded navigation |
| `startFullScreenNavigation()` | Start full-screen navigation |
| `finishNavigation()` | End the navigation session |
| `clearRoute()` | Clear the current route |
| `reCenterCamera()` | Re-center the map on the user |
| `updateCameraPosition(lat, lng)` | Move the camera to a position |
| `setPOI(groupName, image, iconSize, wayPoints)` | Add POI annotations |
| `removePOI(groupName)` | Remove a group of POI annotations |
| `distanceRemaining` | Remaining distance in metres |
| `durationRemaining` | Remaining time in seconds |
| `zoomLevel` | Current camera zoom level |
| `centerCoordinates` | Current map center coordinates |
| `selectedAnnotation` | Last tapped annotation ID |

---

## Contributing

Contributions are welcome. Please open an issue or submit a pull request on [GitHub](https://github.com/nick92/flutter_mapbox).

---

## License

MIT — see [LICENSE](LICENSE) for details.
