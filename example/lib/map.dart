import 'dart:async';
import 'dart:convert';
import 'dart:io';
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
      WayPoint(name: "Home", latitude: 53.2110237, longitude: -2.8944236);

  final _store =
      WayPoint(name: "Padeswood", latitude: 53.217260, longitude: -2.851740);

// -2.8944236,53.2110237;-3.0580621,53.1568208

  MapBoxNavigation? _directions;
  MapBoxOptions? _options;

  bool _isMultipleStop = false;
  double? _distanceRemaining, _durationRemaining;
  MapBoxNavigationViewController? _controller;
  bool _routeBuilt = false;
  bool _isNavigating = false;
  dynamic prevEvent;
  List<WayPoint> _pois = [];
  bool _poisShowing = false;

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

      List<WayPoint> pois = [];

      pois.add(
          WayPoint(name: "HOME", latitude: 53.211025, longitude: -2.894550));
      pois.add(
          WayPoint(name: "DEST", latitude: 53.156082, longitude: -3.063002));

      _directions = MapBoxNavigation(onRouteEvent: _onEmbeddedRouteEvent);
      var options = MapBoxOptions(
          initialLatitude: 36.1175275,
          initialLongitude: -115.1839524,
          // avoid: ["motorway", "toll"],
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
          // pois: _pois,
          mapStyleUrlDay: "mapbox://styles/mapbox/navigation-day-v1",
          mapStyleUrlNight: "mapbox://styles/mapbox/navigation-night-v1",
          language: "en-GB");

      setState(() {
        _options = options;
        _pois = pois;
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
                                  List<String> avoids = [];

                                  var lat = "53.157863";
                                  var lon = "-3.055103";
                                  avoids.add("ferry");
                                  avoids.add("point($lon $lat)");
                                  //"point(\($0.longitude) \($0.latitude)"

                                  // _options.avoid = avoids;
                                  _options!.maxHeight = "5.0";
                                  _options!.maxWeight = "2.5";
                                  _options!.maxWidth = "1.9";

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
                          child: Text("Add POIs"),
                          onPressed: () async {
                            var bytes =
                                "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";

                            if (!_poisShowing) {
                              _controller!.setPOI(
                                  groupName: "petrol",
                                  image: bytes,
                                  wayPoints: _pois);
                            } else {
                              _controller!.removePOI(groupName: "petrol");
                            }
                            setState(() {
                              !_poisShowing;
                            });
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
    print(e.eventType);

    if (prevEvent != null &&
        prevEvent == e.eventType &&
        e.eventType != MapBoxEvent.annotation_tapped &&
        e.eventType != MapBoxEvent.map_position_changed) return;

    switch (e.eventType) {
      case MapBoxEvent.annotation_tapped:
        var annotation = _controller!.selectedAnnotation;
        print(annotation);
        break;
      case MapBoxEvent.map_position_changed:
        var coords = await _controller!.centerCoordinates;
        print("lat:" +
            coords.first.toString() +
            " lon:" +
            coords.last.toString());
        var bytes =
            "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";

        _controller!
            .setPOI(groupName: "parking", image: bytes, wayPoints: _pois);

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
    setState(() {
      prevEvent = e.eventType;
    });
  }
}
