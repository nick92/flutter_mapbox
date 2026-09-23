import 'package:flutter/widgets.dart';
import 'package:flutter_mapbox/flutter_mapbox.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('MapBoxOptions.toMap', () {
    test('sends enums by name', () {
      final map = MapBoxOptions(
        mode: MapBoxNavigationMode.drivingWithTraffic,
        units: VoiceUnits.metric,
      ).toMap();

      expect(map['mode'], 'drivingWithTraffic');
      expect(map['units'], 'metric');
    });

    test('sends vehicle dimensions as strings, as the platforms parse them', () {
      final map = MapBoxOptions(
        maxHeight: '4.5',
        maxWidth: '2.55',
        maxWeight: '26.0',
      ).toMap();

      expect(map['maxHeight'], '4.5');
      expect(map['maxWidth'], '2.55');
      expect(map['maxWeight'], '26.0');
    });

    test('sends avoid list, including point exclusions', () {
      final map = MapBoxOptions(
        avoid: ['motorway', 'point(-1.89 52.48)'],
      ).toMap();

      expect(map['avoid'], ['motorway', 'point(-1.89 52.48)']);
    });

    test('sends the remaining route and display options', () {
      final map = MapBoxOptions(
        initialLatitude: 52.48,
        initialLongitude: -1.89,
        language: 'en-GB',
        zoom: 13,
        bearing: 90,
        tilt: 30,
        alternatives: true,
        allowsUTurnAtWayPoints: false,
        enableRefresh: true,
        voiceInstructionsEnabled: true,
        bannerInstructionsEnabled: false,
        longPressDestinationEnabled: true,
        simulateRoute: true,
        isOptimized: false,
        animateBuildRoute: true,
        mapStyleUrlDay: 'mapbox://styles/mapbox/navigation-day-v1',
        mapStyleUrlNight: 'mapbox://styles/mapbox/navigation-night-v1',
      ).toMap();

      expect(map, containsPair('initialLatitude', 52.48));
      expect(map, containsPair('initialLongitude', -1.89));
      expect(map, containsPair('language', 'en-GB'));
      expect(map, containsPair('zoom', 13.0));
      expect(map, containsPair('bearing', 90.0));
      expect(map, containsPair('tilt', 30.0));
      expect(map, containsPair('alternatives', true));
      expect(map, containsPair('allowsUTurnAtWayPoints', false));
      expect(map, containsPair('enableRefresh', true));
      expect(map, containsPair('voiceInstructionsEnabled', true));
      expect(map, containsPair('bannerInstructionsEnabled', false));
      expect(map, containsPair('longPressDestinationEnabled', true));
      expect(map, containsPair('simulateRoute', true));
      expect(map, containsPair('isOptimized', false));
      expect(map, containsPair('animateBuildRoute', true));
      expect(map['mapStyleUrlDay'], 'mapbox://styles/mapbox/navigation-day-v1');
      expect(
          map['mapStyleUrlNight'], 'mapbox://styles/mapbox/navigation-night-v1');
    });

    test('omits unset options so the platform keeps its defaults', () {
      final map = MapBoxOptions().toMap();

      for (final key in [
        'mode',
        'units',
        'language',
        'zoom',
        'alternatives',
        'avoid',
        'maxHeight',
        'maxWidth',
        'maxWeight',
        'simulateRoute',
        'mapStyleUrlDay',
      ]) {
        expect(map.containsKey(key), isFalse, reason: key);
      }
    });

    test('sends padding as [top, left, bottom, right]', () {
      final map = MapBoxOptions(
        padding: const EdgeInsets.fromLTRB(1, 2, 3, 4),
      ).toMap();

      expect(map['padding'], [2.0, 1.0, 4.0, 3.0]);
    });
  });

  group('MapBoxOptions.updatesMap', () {
    test('returns only the options that changed', () {
      final before = MapBoxOptions(zoom: 13, language: 'en', simulateRoute: false);
      final after = MapBoxOptions(zoom: 15, language: 'en', simulateRoute: true);

      final updates = before.updatesMap(after);

      expect(updates, containsPair('zoom', 15.0));
      expect(updates, containsPair('simulateRoute', true));
      expect(updates.containsKey('language'), isFalse);
    });
  });
}
