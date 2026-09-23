import 'dart:convert';

import 'package:flutter_mapbox/flutter_mapbox.dart';
import 'package:flutter_test/flutter_test.dart';

Map<String, dynamic> _leg({String name = 'A45'}) => {
      'profileIdentifier': 'driving-traffic',
      'name': name,
      'distance': 1200,
      'expectedTravelTime': 90.5,
      'source': {
        'id': 'origin',
        'name': 'Depot',
        'latitude': 52.48,
        'longitude': -1.89,
      },
      'destination': {
        'id': 'stop_1',
        'name': 'Customer',
        'latitude': 52.5,
        'longitude': -1.8,
      },
      'steps': [
        {
          'name': 'High Street',
          'instructions': 'Turn left onto High Street',
          'distance': 300,
          'expectedTravelTime': 40,
        },
      ],
    };

void main() {
  group('WayPoint', () {
    test('fromJson reads id, name and coordinates', () {
      final w = WayPoint.fromJson({
        'id': 'stop_1',
        'name': 'Customer',
        'latitude': 52.5,
        'longitude': -1.8,
      });

      expect(w.id, 'stop_1');
      expect(w.name, 'Customer');
      expect(w.latitude, 52.5);
      expect(w.longitude, -1.8);
    });

    test('toString shows the coordinates', () {
      final w =
          WayPoint(id: '1', name: 'A', latitude: 52.5, longitude: -1.8);
      expect(w.toString(), 'WayPoint{latitude: 52.5, longitude: -1.8}');
    });
  });

  group('RouteStep.fromJson', () {
    test('reads fields and widens ints to doubles', () {
      final s = RouteStep.fromJson(_leg()['steps'][0] as Map<String, dynamic>);

      expect(s.name, 'High Street');
      expect(s.instructions, 'Turn left onto High Street');
      expect(s.distance, 300.0);
      expect(s.expectedTravelTime, 40.0);
    });

    test('missing numbers read as zero', () {
      final s = RouteStep.fromJson({'name': 'X'});
      expect(s.distance, 0.0);
      expect(s.expectedTravelTime, 0.0);
    });
  });

  group('RouteLeg.fromJson', () {
    test('reads the leg, its endpoints and steps', () {
      final l = RouteLeg.fromJson(_leg());

      expect(l.profileIdentifier, 'driving-traffic');
      expect(l.name, 'A45');
      expect(l.distance, 1200.0);
      expect(l.expectedTravelTime, 90.5);
      expect(l.source?.name, 'Depot');
      expect(l.destination?.id, 'stop_1');
      expect(l.steps, hasLength(1));
      expect(l.steps!.first.instructions, 'Turn left onto High Street');
    });

    test('tolerates missing endpoints and steps', () {
      final l = RouteLeg.fromJson({'name': 'A45'});
      expect(l.source, isNull);
      expect(l.destination, isNull);
      expect(l.steps, isNull);
      expect(l.distance, 0.0);
    });
  });

  group('RouteProgressEvent.fromJson', () {
    Map<String, dynamic> progress() => {
          'arrived': false,
          'distance': 5000,
          'duration': 600.5,
          'distanceTraveled': 250,
          'currentLegDistanceTraveled': 250,
          'currentLegDistanceRemaining': 4750,
          'currentStepInstruction': 'Continue on A45',
          'legIndex': 1,
          'stepIndex': 3,
          'currentLeg': _leg(),
          'priorLeg': _leg(name: 'M6'),
          'remainingLegs': [_leg(name: 'A38')],
        };

    test('reads progress and widens ints to doubles', () {
      final p = RouteProgressEvent.fromJson(progress());

      expect(p.isProgressEvent, isTrue);
      expect(p.arrived, isFalse);
      expect(p.distance, 5000.0);
      expect(p.duration, 600.5);
      expect(p.distanceTraveled, 250.0);
      expect(p.currentLegDistanceTraveled, 250.0);
      expect(p.currentLegDistanceRemaining, 4750.0);
      expect(p.currentStepInstruction, 'Continue on A45');
      expect(p.legIndex, 1);
      expect(p.stepIndex, 3);
      expect(p.currentLeg?.name, 'A45');
      expect(p.priorLeg?.name, 'M6');
      expect(p.remainingLegs?.single.name, 'A38');
    });

    test('is a progress event only when "arrived" is present', () {
      // _parseRouteEvent relies on this to tell progress from other events.
      final other = RouteProgressEvent.fromJson(
          {'eventType': 'route_built', 'data': ''});
      expect(other.isProgressEvent, isFalse);
      expect(other.arrived, isFalse);
      expect(other.distance, 0.0);
    });
  });

  group('RouteEvent.fromJson', () {
    test('reads the event type by name', () {
      final e = RouteEvent.fromJson({'eventType': 'navigation_running'});
      expect(e.eventType, MapBoxEvent.navigation_running);
    });

    test('reads the event type by index', () {
      final e = RouteEvent.fromJson(
          {'eventType': MapBoxEvent.on_arrival.index, 'data': ''});
      expect(e.eventType, MapBoxEvent.on_arrival);
    });

    test('unknown event type leaves eventType null rather than throwing', () {
      final e = RouteEvent.fromJson({'eventType': 'not_a_real_event'});
      expect(e.eventType, isNull);
    });

    test('non-progress data is JSON-encoded again (decode twice to read it)',
        () {
      const payload = '[{"distance":1000.0}]';
      final e = RouteEvent.fromJson(
          {'eventType': 'route_built', 'data': payload});

      expect(e.data, jsonEncode(payload));
      expect(jsonDecode(jsonDecode(e.data as String) as String),
          [
            {'distance': 1000.0}
          ]);
    });

    test('progress_change data is parsed into a RouteProgressEvent', () {
      final e = RouteEvent.fromJson({
        'eventType': 'progress_change',
        'data': {'arrived': true, 'distance': 0},
      });

      expect(e.data, isA<RouteProgressEvent>());
      expect((e.data as RouteProgressEvent).arrived, isTrue);
    });
  });
}
