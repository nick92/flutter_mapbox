import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_mapbox/flutter_mapbox.dart';
import 'package:flutter_test/flutter_test.dart';

const _viewId = 7;
const _methods = MethodChannel('flutter_mapbox/$_viewId');
const _events = EventChannel('flutter_mapbox/$_viewId/events');

/// Wraps [data] the way both platforms' sendEvent do: the payload is a JSON
/// string inside the event JSON.
String _nativeEvent(String type, [String data = '']) =>
    jsonEncode({'eventType': type, 'data': data});

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  late List<MethodCall> calls;
  late Object? Function(MethodCall) reply;
  late MockStreamHandlerEventSink? sink;
  late List<RouteEvent> received;
  late MapBoxNavigationViewController controller;

  setUp(() {
    calls = [];
    reply = (_) => true;
    sink = null;
    received = [];
    messenger.setMockMethodCallHandler(_methods, (call) async {
      calls.add(call);
      return reply(call);
    });
    messenger.setMockStreamHandler(
      _events,
      MockStreamHandler.inline(onListen: (_, s) => sink = s),
    );
    controller = MapBoxNavigationViewController(_viewId, received.add);
  });

  tearDown(() {
    messenger.setMockMethodCallHandler(_methods, null);
    messenger.setMockStreamHandler(_events, null);
  });

  final origin =
      WayPoint(id: 'origin', name: 'Depot', latitude: 52.48, longitude: -1.89);
  final destination = WayPoint(
      id: 'stop_1', name: 'Customer', latitude: 52.5, longitude: -1.8);

  group('buildRoute', () {
    test('sends ordered waypoints merged with the options', () async {
      final ok = await controller.buildRoute(
        wayPoints: [origin, destination],
        options: MapBoxOptions(
          maxHeight: '4.9',
          mode: MapBoxNavigationMode.drivingWithTraffic,
          avoid: ['point(-1.85 52.49)'],
        ),
      );

      expect(ok, isTrue);
      final call = calls.single;
      expect(call.method, 'buildRoute');
      final args = call.arguments as Map;
      expect(args['maxHeight'], '4.9');
      expect(args['mode'], 'drivingWithTraffic');
      expect(args['avoid'], ['point(-1.85 52.49)']);
      expect(args['wayPoints'], {
        0: {
          'Order': 0,
          'Name': 'Depot',
          'Latitude': 52.48,
          'Longitude': -1.89,
        },
        1: {
          'Order': 1,
          'Name': 'Customer',
          'Latitude': 52.5,
          'Longitude': -1.8,
        },
      });
    });

    test('rejects a single waypoint', () {
      expect(
        () => controller.buildRoute(wayPoints: [origin]),
        throwsA(isA<AssertionError>()),
      );
    });
  });

  group('selectRoute', () {
    test('sends the index', () async {
      expect(await controller.selectRoute(index: 2), isTrue);
      expect(calls.single.method, 'selectRoute');
      expect(calls.single.arguments, {'index': 2});
    });

    test('a null reply from the platform reads as false', () async {
      reply = (_) => null;
      expect(await controller.selectRoute(index: 1), isFalse);
    });
  });

  group('POIs', () {
    test('setPOI sends the group, icon and points with ids', () async {
      await controller.setPOI(
        groupName: 'bridges',
        image: 'base64png',
        iconSize: 2.8,
        wayPoints: [destination],
      );

      final call = calls.single;
      expect(call.method, 'setPOIs');
      expect(call.arguments, {
        'group': 'bridges',
        'icon': 'base64png',
        'iconSize': 2.8,
        'poi': {
          0: {
            'Order': 0,
            'Id': 'stop_1',
            'Name': 'Customer',
            'Latitude': 52.5,
            'Longitude': -1.8,
          },
        },
      });
    });

    test('removePOI sends the group', () async {
      await controller.removePOI(groupName: 'bridges');
      expect(calls.single.method, 'removePOIs');
      expect(calls.single.arguments, {'group': 'bridges'});
    });
  });

  group('navigation calls', () {
    test('start / full-screen / finish / clear / recentre map to methods',
        () async {
      await controller.startNavigation(options: MapBoxOptions(zoom: 15));
      await controller.startFullScreenNavigation();
      await controller.finishNavigation();
      await controller.clearRoute();
      reply = (_) => null;
      await controller.reCenterCamera();

      expect(calls.map((c) => c.method), [
        'startNavigation',
        'startFullScreenNavigation',
        'finishNavigation',
        'clearRoute',
        'reCenter',
      ]);
      expect((calls.first.arguments as Map)['zoom'], 15.0);
      expect(calls[1].arguments, isNull);
    });

    test('updateCameraPosition sends the coordinates', () async {
      await controller.updateCameraPosition(latitude: 52.5, longitude: -1.8);
      expect(calls.single.method, 'updateCamera');
      expect(calls.single.arguments, {'latitude': 52.5, 'longitude': -1.8});
    });

    test('getters read their values from the platform', () async {
      reply = (call) => switch (call.method) {
            'getDistanceRemaining' => 1234.5,
            'getDurationRemaining' => 600.0,
            'getZoomLevel' => 14.0,
            'getSelectedAnnotation' => 'stop_1',
            'getCenterCoordinates' => [52.5, -1.8],
            _ => null,
          };

      expect(await controller.distanceRemaining, 1234.5);
      expect(await controller.durationRemaining, 600.0);
      expect(await controller.zoomLevel, 14.0);
      expect(await controller.selectedAnnotation, 'stop_1');
      expect(await controller.centerCoordinates, [52.5, -1.8]);
    });
  });

  group('route events', () {
    Future<void> emit(String json) async {
      sink!.success(json);
      await pumpEventQueue();
    }

    test('route_built delivers each route with its coordinates (v1 payload)',
        () async {
      await controller.initialize();
      await pumpEventQueue();

      // What EmbeddedNavigationView.routesJson / showCurrentRoute send.
      final routes = jsonEncode([
        {
          'duration': 900.0,
          'distance': 12000.0,
          'coordinates': [
            [-1.89, 52.48],
            [-1.8, 52.5],
          ],
        },
        {'duration': 960.0, 'distance': 12500.0, 'coordinates': []},
      ]);
      await emit(_nativeEvent('route_built', routes));

      final e = received.single;
      expect(e.eventType, MapBoxEvent.route_built);
      final decoded =
          jsonDecode(jsonDecode(e.data as String) as String) as List;
      expect(decoded, hasLength(2));
      expect(decoded.first['coordinates'], [
        [-1.89, 52.48],
        [-1.8, 52.5],
      ]);
      expect(decoded.last['distance'], 12500.0);
    });

    test('progress events arrive as RouteProgressEvent', () async {
      await controller.initialize();
      await pumpEventQueue();

      await emit(jsonEncode({
        'eventType': 'progress_change',
        'data': {
          'arrived': false,
          'distance': 800,
          'duration': 120,
          'legIndex': 0,
          'currentStepInstruction': 'Turn right',
        },
      }));

      final e = received.single;
      expect(e.eventType, MapBoxEvent.progress_change);
      final p = e.data as RouteProgressEvent;
      expect(p.distance, 800.0);
      expect(p.currentStepInstruction, 'Turn right');
    });

    test('other events pass through with their type', () async {
      await controller.initialize();
      await pumpEventQueue();

      await emit(_nativeEvent('route_building'));
      await emit(_nativeEvent('navigation_running'));
      await emit(_nativeEvent('navigation_finished'));

      expect(received.map((e) => e.eventType), [
        MapBoxEvent.route_building,
        MapBoxEvent.navigation_running,
        MapBoxEvent.navigation_finished,
      ]);
    });
  });
}
