import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_mapbox/flutter_mapbox.dart';
import 'package:location/location.dart';

class SampleNavigationApp extends StatefulWidget {
  @override
  _SampleNavigationAppState createState() => _SampleNavigationAppState();
}

class _SampleNavigationAppState extends State<SampleNavigationApp> {
  String _platformVersion = 'Unknown';
  String? _instruction = "";

  final _home =
      WayPoint(name: "Home", latitude: 53.211025, longitude: -2.894550);

  final _store =
      WayPoint(name: "Padeswood", latitude: 53.156263, longitude: -3.060583);

  MapBoxNavigation? _directions;
  MapBoxOptions? _options;

  bool _isMultipleStop = false;
  double? _distanceRemaining, _durationRemaining;
  MapBoxNavigationViewController? _controller;
  bool _routeBuilt = false;
  bool _isNavigating = false;

  @override
  void initState() {
    super.initState();
    Future((() => initialize()));
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initialize() async {
    try {
      Location location = Location();
      // If the widget was removed from the tree while the asynchronous platform
      // message was in flight, we want to discard the reply rather than calling
      // setState to update our non-existent appearance.
      if (!mounted) return;

      bool _serviceEnabled;
      PermissionStatus _permissionGranted;
      LocationData _locationData;

      _serviceEnabled = await location.serviceEnabled();
      if (!_serviceEnabled) {
        _serviceEnabled = await location.requestService();
        if (!_serviceEnabled) {
          return;
        }
      }

      _permissionGranted = await location.hasPermission();
      if (_permissionGranted == PermissionStatus.denied) {
        _permissionGranted = await location.requestPermission();
        if (_permissionGranted != PermissionStatus.granted) {
          return;
        }
      }
      _locationData = await location.getLocation();

      List<WayPoint> _pois = [];

      _pois.add(
          WayPoint(name: "HOME", latitude: 53.211025, longitude: -2.894550));
      _pois.add(
          WayPoint(name: "DEST", latitude: 53.156261, longitude: -3.060581));

      _directions = MapBoxNavigation(onRouteEvent: _onEmbeddedRouteEvent);
      var options = MapBoxOptions(
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
          pois: _pois,
          mapStyleUrlDay: "mapbox://styles/mapbox/navigation-day-v1",
          mapStyleUrlNight: "mapbox://styles/mapbox/navigation-night-v1",
          language: "en");

      setState(() {
        _options = options;
      });
    } catch (err) {
      print(err);
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Column(children: <Widget>[
            SingleChildScrollView(
              child: Column(
                children: [
                  Container(
                    color: Colors.teal,
                    width: double.infinity,
                    child: Padding(
                      padding: EdgeInsets.all(10),
                      child: (Text(
                        "Embedded Navigation",
                        style: TextStyle(color: Colors.white),
                        textAlign: TextAlign.center,
                      )),
                    ),
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      ElevatedButton(
                        child: Text(_routeBuilt && !_isNavigating
                            ? "Clear Route"
                            : "Build Route"),
                        onPressed: _isNavigating
                            ? null
                            : () {
                                if (_routeBuilt) {
                                  _controller!.clearRoute();
                                } else {
                                  var wayPoints = <WayPoint>[];
                                  wayPoints.add(_home);
                                  wayPoints.add(_store);
                                  _isMultipleStop = wayPoints.length > 2;
                                  _controller!.buildRoute(
                                      wayPoints: wayPoints, options: _options);
                                }
                              },
                      ),
                      ElevatedButton(
                          child: Text("Update Camera"),
                          onPressed: () {
                            _controller!.reCenterCamera();
                          }),
                      Row(children: [
                        ElevatedButton(
                          child: Text("Start "),
                          onPressed: !_isNavigating
                              ? () {
                                  _controller!.startFullScreenNavigation();
                                }
                              : null,
                        ),
                        ElevatedButton(
                          child: Text("Start 2"),
                          onPressed: !_isNavigating
                              ? () {
                                  // _controller!.finishNavigation();
                                  var wayPoints = <WayPoint>[];
                                  wayPoints.add(_home);
                                  wayPoints.add(_store);
                                  _isMultipleStop = wayPoints.length > 2;
                                  _directions!.startNavigation(
                                      wayPoints: wayPoints, options: _options!);
                                }
                              : null,
                        )
                      ]),
                    ],
                  ),
                  //   Center(
                  //     child: Padding(
                  //       padding: EdgeInsets.all(10),
                  //       child: Text(
                  //         "Long-Press Embedded Map to Set Destination",
                  //         textAlign: TextAlign.center,
                  //       ),
                  //     ),
                  //   ),
                  //   Container(
                  //     color: Colors.grey,
                  //     width: double.infinity,
                  //     child: Padding(
                  //       padding: EdgeInsets.all(10),
                  //       child: (Text(
                  //         _instruction == null || _instruction!.isEmpty
                  //             ? "Banner Instruction Here"
                  //             : _instruction!,
                  //         style: TextStyle(color: Colors.white),
                  //         textAlign: TextAlign.center,
                  //       )),
                  //     ),
                  //   ),
                  //   Padding(
                  //     padding: EdgeInsets.only(
                  //         left: 20.0, right: 20, top: 20, bottom: 10),
                  //     child: Column(
                  //       mainAxisAlignment: MainAxisAlignment.center,
                  //       children: <Widget>[
                  //         Row(
                  //           children: <Widget>[
                  //             Text("Duration Remaining: "),
                  //             Text(_durationRemaining != null
                  //                 ? "${(_durationRemaining! / 60).toStringAsFixed(0)} minutes"
                  //                 : "---")
                  //           ],
                  //         ),
                  //         Row(
                  //           children: <Widget>[
                  //             Text("Distance Remaining: "),
                  //             Text(_distanceRemaining != null
                  //                 ? "${(_distanceRemaining! * 0.000621371).toStringAsFixed(1)} miles"
                  //                 : "---")
                  //           ],
                  //         ),
                  //       ],
                  //     ),
                  //   ),
                  //   Divider()
                ],
              ),
            ),
            if (_options != null)
              Expanded(
                flex: 1,
                child: Container(
                  color: Colors.grey,
                  child: MapBoxNavigationView(
                      options: _options,
                      onRouteEvent: _onEmbeddedRouteEvent,
                      onCreated:
                          (MapBoxNavigationViewController controller) async {
                        _controller = controller;
                        _controller!.initialize();
                      }),
                ),
              )
          ]),
        ),
      ),
    );
  }

  Future<void> _onEmbeddedRouteEvent(e) async {
    // _distanceRemaining = await _controller!.distanceRemaining;
    // _durationRemaining = await _controller!.durationRemaining;

    switch (e.eventType) {
      case MapBoxEvent.annotation_tapped:
        var annotation = _controller!.selectedAnnotation;
        print(annotation);
        break;
      case MapBoxEvent.progress_change:
        var progressEvent = e.data as RouteProgressEvent;
        if (progressEvent.currentStepInstruction != null)
          _instruction = progressEvent.currentStepInstruction;
        break;
      case MapBoxEvent.route_building:
      case MapBoxEvent.route_built:
        setState(() {
          _routeBuilt = true;
        });
        break;
      case MapBoxEvent.route_build_failed:
        setState(() {
          _routeBuilt = false;
        });
        break;
      case MapBoxEvent.navigation_running:
        setState(() {
          _isNavigating = true;
        });
        break;
      case MapBoxEvent.on_arrival:
        if (!_isMultipleStop) {
          await Future.delayed(Duration(seconds: 3));
          await _controller!.finishNavigation();
        } else {}
        break;
      case MapBoxEvent.navigation_finished:
      case MapBoxEvent.navigation_cancelled:
        setState(() {
          _routeBuilt = false;
          _isNavigating = false;
        });
        break;
      default:
        break;
    }
    setState(() {});
  }
}
