import Combine
import CoreLocation
import MapboxDirections
import Flutter
import UIKit
import MapboxMaps
@_spi(ExperimentalMapboxAPI) import MapboxNavigationCore
import MapboxNavigationUIKit

@MainActor
public class FlutterMapboxNavigationView: NavigationFactory, FlutterPlatformView {
    let frame: CGRect
    let viewId: Int64

    let messenger: FlutterBinaryMessenger
    let channel: FlutterMethodChannel
    let eventChannel: FlutterEventChannel

    var navigationMapView: NavigationMapView!
    var arguments: NSDictionary?

    var selectedRouteIndex = 0
    var routeOptions: NavigationRouteOptions?
    var navigationRoutes: NavigationRoutes? {
        didSet {
            guard navigationRoutes != nil else {
                navigationMapView?.removeRoutes()
                return
            }
            showCurrentRoute()
        }
    }

    var _mapInitialized = false
    var locationManager = CLLocationManager()
    var _selectedAnnotation: String?
    var pointAnnotationManager: PointAnnotationManager?
    var pois = [MapboxPointAnnotation]()
    var mapMoved = false
    var centerCoords: [Double] = []
    var zoomLevel: Double = 0.0

    private var locationBridge = PassthroughSubject<CLLocation, Never>()
    private var routeProgressBridge = PassthroughSubject<RouteProgress?, Never>()
    private var headingBridge = PassthroughSubject<CLHeading, Never>()
    private var providerSubscriptions = Set<AnyCancellable>()

    init(messenger: FlutterBinaryMessenger, frame: CGRect, viewId: Int64, args: Any?) {
        self.frame = frame
        self.viewId = viewId
        self.arguments = args as! NSDictionary?
        self.messenger = messenger
        self.channel = FlutterMethodChannel(name: "flutter_mapbox/\(viewId)", binaryMessenger: messenger)
        self.eventChannel = FlutterEventChannel(name: "flutter_mapbox/\(viewId)/events", binaryMessenger: messenger)

        super.init()
        self.eventChannel.setStreamHandler(self)

        self.channel.setMethodCallHandler { [weak self] (call, result) in
            guard let self else { return }
            let arguments = call.arguments as? NSDictionary
            switch call.method {
            case "getPlatformVersion":
                result("iOS " + UIDevice.current.systemVersion)
            case "buildRoute":
                self.buildRoute(arguments: arguments, flutterResult: result)
            case "clearRoute":
                self.clearRoute(arguments: arguments, result: result)
            case "updateCamera":
                self.updateCamera(arguments: arguments, result: result)
            case "getDistanceRemaining":
                result(self._distanceRemaining)
            case "getDurationRemaining":
                result(self._durationRemaining)
            case "getCenterCoordinates":
                result(self.centerCoords)
            case "getZoomLevel":
                result(self.zoomLevel)
            case "getRouteBuildResponse":
                result(self._routeBuildResponse)
            case "getSelectedAnnotation":
                result(self._selectedAnnotation)
            case "finishNavigation":
                self.endNavigation(result: result)
            case "startNavigation":
                self.startEmbeddedNavigation(arguments: arguments, result: result)
            case "startFullScreenNavigation":
                self.startNonEmbeddedNavigation(arguments: arguments, result: result)
            case "reCenter":
                self.navigationMapView.navigationCamera.update(cameraState: .following)
                result(nil)
            case "setPOIs":
                self.addPOIs(arguments: arguments, result: result)
            case "removePOIs":
                self.removePOIs(arguments: arguments, result: result)
            case "selectRoute":
                self.selectRoute(arguments: arguments, result: result)
            default:
                result(FlutterMethodNotImplemented)
            }
        }
    }

    func showCurrentRoute() {
        guard let navigationRoutes else { return }
        navigationMapView.showRoutes(navigationRoutes)
        _distanceRemaining = navigationRoutes.mainRoute.route.distance
        _durationRemaining = navigationRoutes.mainRoute.route.expectedTravelTime

        // One entry per route, main first. `coordinates` is the route line as
        // [[lng, lat], ...] so the app can check it against its restrictions.
        func routeEntry(_ route: Route) -> [String: Any] {
            let coords = (route.shape?.coordinates ?? []).map { [$0.longitude, $0.latitude] }
            return [
                "duration": route.expectedTravelTime,
                "distance": route.distance,
                "coordinates": coords
            ]
        }
        var routeData: [[String: Any]] = [routeEntry(navigationRoutes.mainRoute.route)]
        for alt in navigationRoutes.alternativeRoutes {
            routeData.append(routeEntry(alt.route))
        }
        if let jsonData = try? JSONSerialization.data(withJSONObject: routeData),
           let jsonString = String(data: jsonData, encoding: .utf8) {
            sendEvent(eventType: MapBoxEventType.route_built, data: jsonString)
        } else {
            sendEvent(eventType: MapBoxEventType.route_built)
        }
    }

    public func view() -> UIView {
        if _mapInitialized { return navigationMapView }
        setupMapView()
        return navigationMapView
    }

    // FlutterPlatformView.dispose() is @optional in the Objective-C protocol,
    // so leaving it unimplemented compiles fine but means the engine never
    // gets a hook to tear this instance down — nothing ever released
    // providerSubscriptions or this view's own state after the Flutter widget
    // was removed.
    //
    // mapboxNavigationProvider itself is a shared, process-lifetime singleton
    // (see NavigationFactory) and is deliberately NOT nil'd here — it belongs
    // to the whole plugin, not this one view, and the next embedded view (or
    // full-screen nav session) reuses it rather than racing to recreate it.
    public func dispose() {
        if let navVC = _navigationViewController {
            navVC.delegate = nil
            if isEmbeddedNavigation {
                navVC.view.removeFromSuperview()
            } else {
                navVC.dismiss(animated: false)
            }
            _navigationViewController = nil
        }

        eventChannel.setStreamHandler(nil)
        channel.setMethodCallHandler(nil)
        _eventSink = nil

        _routeCalculationTask?.cancel()
        _routeCalculationTask = nil

        providerSubscriptions.removeAll()

        locationManager.stopUpdatingLocation()
        locationManager.stopUpdatingHeading()
        locationManager.delegate = nil

        pointAnnotationManager?.delegate = nil
        pointAnnotationManager = nil
        navigationRoutes = nil
        navigationMapView?.delegate = nil
        navigationMapView = nil
        _mapInitialized = false
    }

    private func subscribeToNavigationProvider() {
        providerSubscriptions.removeAll()
        let nav = mapboxNavigation.navigation()
        nav.locationMatching
            .map(\.location)
            .sink { [weak self] in self?.locationBridge.send($0) }
            .store(in: &providerSubscriptions)
        nav.routeProgress
            .map { $0?.routeProgress }
            .sink { [weak self] in self?.routeProgressBridge.send($0) }
            .store(in: &providerSubscriptions)
        nav.heading
            .sink { [weak self] in self?.headingBridge.send($0) }
            .store(in: &providerSubscriptions)
    }

    private func setupMapView() {
        locationManager.delegate = self
        subscribeToNavigationProvider()

        navigationMapView = NavigationMapView(
            location: locationBridge.eraseToAnyPublisher(),
            routeProgress: routeProgressBridge.eraseToAnyPublisher(),
            heading: headingBridge.eraseToAnyPublisher()
        )
        navigationMapView.frame = frame
        navigationMapView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        navigationMapView.delegate = self
        navigationMapView.puckType = .puck2D(Puck2DConfiguration.makeDefault(showBearing: false))
        navigationMapView.puckBearing = .heading

        let mapView = navigationMapView.mapView
        pointAnnotationManager = mapView.annotations.makePointAnnotationManager()
        pointAnnotationManager?.delegate = self

        if let arguments {
            _language = arguments["language"] as? String ?? _language
            _voiceUnits = arguments["units"] as? String ?? _voiceUnits
            _simulateRoute = arguments["simulateRoute"] as? Bool ?? _simulateRoute
            _isOptimized = arguments["isOptimized"] as? Bool ?? _isOptimized
            _alternatives = arguments["alternatives"] as? Bool ?? _alternatives
            _enableRefresh = arguments["enableRefresh"] as? Bool ?? _enableRefresh
            _allowsUTurnAtWayPoints = arguments["allowsUTurnAtWayPoints"] as? Bool
            _navigationMode = arguments["mode"] as? String ?? "drivingWithTraffic"
            _mapStyleUrlDay = arguments["mapStyleUrlDay"] as? String
            _zoom = arguments["zoom"] as? Double ?? _zoom
            _bearing = arguments["bearing"] as? Double ?? _bearing
            _pitch = arguments["tilt"] as? Double ?? _pitch
            _animateBuildRoute = arguments["animateBuildRoute"] as? Bool ?? _animateBuildRoute
            _longPressDestinationEnabled = arguments["longPressDestinationEnabled"] as? Bool ?? _longPressDestinationEnabled
            _avoid = arguments["avoid"] as? [String]

            if let urlString = _mapStyleUrlDay, let url = URL(string: urlString), let styleURI = StyleURI(url: url) {
                navigationMapView.mapView.mapboxMap.mapStyle = MapStyle(uri: styleURI)
            }

            if let maxHeight = arguments["maxHeight"] as? String { _maxHeight = Double(maxHeight) ?? _maxHeight }
            if let maxWidth = arguments["maxWidth"] as? String { _maxWidth = Double(maxWidth) ?? _maxWidth }
            if let maxWeight = arguments["maxWeight"] as? String { _maxWeight = Double(maxWeight) ?? _maxWeight }

            locationManager.requestWhenInUseAuthorization()
            locationManager.desiredAccuracy = kCLLocationAccuracyBest
            locationManager.startUpdatingLocation()
            locationManager.startUpdatingHeading()
            var currentLocation: CLLocation?
            if locationManager.authorizationStatus == .authorizedWhenInUse ||
               locationManager.authorizationStatus == .authorizedAlways {
                currentLocation = locationManager.location
            }

            let initialLatitude = arguments["initialLatitude"] as? Double ?? currentLocation?.coordinate.latitude
            let initialLongitude = arguments["initialLongitude"] as? Double ?? currentLocation?.coordinate.longitude
            if let lat = initialLatitude, let lon = initialLongitude {
                moveCameraToCoordinates(latitude: lat, longitude: lon)
            }
        }

        if _longPressDestinationEnabled {
            let gesture = UILongPressGestureRecognizer(target: self, action: #selector(handleLongPress(_:)))
            gesture.delegate = self
            navigationMapView.addGestureRecognizer(gesture)
        }

        mapView.mapboxMap.onEvery(event: .mapIdle) { [weak self] _ in
            self?.addOnMapIdleListener()
        }
        mapView.mapboxMap.onEvery(event: .cameraChanged) { [weak self] _ in
            self?.onCameraChangeListener()
        }
    }

    func addOnMapIdleListener() {
        let mapView = navigationMapView.mapView
        let coords = mapView.mapboxMap.cameraState.center
        centerCoords = [Double(coords.longitude), Double(coords.latitude)]
        zoomLevel = Double(mapView.cameraState.zoom)
        sendEvent(eventType: MapBoxEventType.map_position_changed)
    }

    func onCameraChangeListener() {
        let mapView = navigationMapView.mapView
        let zoom = mapView.cameraState.zoom
        if zoom < 8 {
            pointAnnotationManager?.annotations = []
        } else {
            pointAnnotationManager?.annotations = pois.flatMap { $0.annotation }
        }
    }

    func addPOIs(arguments: NSDictionary?, result: @escaping FlutterResult) {
        guard self.arguments != nil else { return }
        let oPOIs = arguments?["poi"] as? NSDictionary ?? [:]
        let image = arguments?["icon"] as? String ?? ""
        let groupName = arguments?["group"] as? String ?? ""
        let iconSize = arguments?["iconSize"] as? Double ?? 0.2
        guard let imageData = Data(base64Encoded: image) else { return }

        // Only track the newly created annotations for this group so that
        // removePOIs never touches annotations belonging to other groups.
        var newAnnotations: [PointAnnotation] = []

        for item in oPOIs {
            let point = item.value as! NSDictionary
            guard let oID = point["Id"] as? String,
                  let oName = point["Name"] as? String,
                  let oLatitude = point["Latitude"] as? Double,
                  let oLongitude = point["Longitude"] as? Double else { continue }

            var customPointAnnotation = PointAnnotation(id: oID, coordinate: CLLocationCoordinate2D(latitude: oLatitude, longitude: oLongitude))
            customPointAnnotation.image = .init(image: UIImage(data: imageData)!, name: groupName)
            customPointAnnotation.iconSize = iconSize
            customPointAnnotation.textField = oName
            customPointAnnotation.textSize = 12
            customPointAnnotation.textOffset = [0, 3]

            if let style = _mapStyleUrlDay {
                if style.contains("night") {
                    customPointAnnotation.textColor = StyleColor(.white)
                    customPointAnnotation.textHaloColor = StyleColor(.black)
                } else {
                    customPointAnnotation.textColor = StyleColor(.black)
                    customPointAnnotation.textHaloColor = StyleColor(.white)
                }
                customPointAnnotation.textHaloWidth = 1
            }
            newAnnotations.append(customPointAnnotation)
        }

        pois.append(MapboxPointAnnotation(name: groupName, annotation: newAnnotations))
        var allAnnotations = pointAnnotationManager?.annotations ?? []
        allAnnotations.append(contentsOf: newAnnotations)
        pointAnnotationManager?.annotations = allAnnotations
        result(true)
    }

    func selectRoute(arguments: NSDictionary?, result: @escaping FlutterResult) {
        guard let index = arguments?["index"] as? Int, index > 0 else {
            result(true); return  // index 0 = primary, nothing to do
        }
        let altIndex = index - 1
        guard let alts = navigationRoutes?.alternativeRoutes, altIndex < alts.count else {
            result(false); return
        }
        let alt = alts[altIndex]
        Task { [weak self] in
            guard let self else { return }
            guard let updated = try? await self.navigationRoutes?.selecting(alternativeRoute: alt) else {
                result(false); return
            }
            await MainActor.run {
                self.navigationRoutes = updated  // didSet triggers showCurrentRoute() → route_built event
                result(true)
            }
        }
    }

    func removePOIs(arguments: NSDictionary?, result: @escaping FlutterResult) {
        let groupName = arguments?["group"] as? String ?? ""
        for group in pois where group.name == groupName {
            let ids = Set(group.annotation.map { $0.id })
            pointAnnotationManager?.annotations.removeAll { ids.contains($0.id) }
        }
        pois.removeAll { $0.name == groupName }
        result(true)
    }

    func updateCamera(arguments: NSDictionary?, result: @escaping FlutterResult) {
        if let lat = arguments?["latitude"] as? Double, let lon = arguments?["longitude"] as? Double {
            moveCameraToCoordinates(latitude: lat, longitude: lon)
        }
        result(true)
    }

    func clearRoute(arguments: NSDictionary?, result: @escaping FlutterResult) {
        _wayPoints.removeAll()
        guard navigationRoutes != nil else { result(true); return }
        navigationMapView?.removeRoutes()
        _distanceRemaining = 0
        _durationRemaining = 0
        navigationRoutes = nil
        result(true)
    }

    func buildRoute(arguments: NSDictionary?, flutterResult: @escaping FlutterResult) {
        isEmbeddedNavigation = true
        sendEvent(eventType: MapBoxEventType.route_building)

        guard let oWayPoints = arguments?["wayPoints"] as? NSDictionary else { return }
        var locations = [Location]()

        for item in oWayPoints as NSDictionary {
            let point = item.value as! NSDictionary
            guard let oName = point["Name"] as? String,
                  let oLatitude = point["Latitude"] as? Double,
                  let oLongitude = point["Longitude"] as? Double else { return }
            let order = point["Order"] as? Int
            locations.append(Location(name: oName, latitude: oLatitude, longitude: oLongitude, order: order))
        }

        // See the matching comment in NavigationFactory.startNavigation —
        // waypoints cross the platform channel as an order-unstable
        // NSDictionary, so `order` must be sorted on unconditionally rather
        // than only when `_isOptimized` is false.
        locations.sort { $0.order ?? 0 < $1.order ?? 0 }

        _wayPoints.removeAll()
        for (i, loc) in locations.enumerated() {
            var point = Waypoint(coordinate: CLLocationCoordinate2D(latitude: loc.latitude!, longitude: loc.longitude!))
            if i > 0 && i != locations.count - 1 { point.separatesLegs = false }
            _wayPoints.append(point)
        }

        var mode: ProfileIdentifier = .automobileAvoidingTraffic
        if _navigationMode == "cycling" { mode = .cycling }
        else if _navigationMode == "driving" { mode = .automobile }
        else if _navigationMode == "walking" { mode = .walking }

        let routeOptions = UnscrambledRouteOptions(waypoints: _wayPoints, profileIdentifier: mode, queryItems: [])

        let avoid = arguments?["avoid"] as? [String] ?? _avoid ?? []
        if !avoid.isEmpty { routeOptions.setExcludes(array: avoid) }

        var maxHeight = _maxHeight
        var maxWidth = _maxWidth
        var maxWeight = _maxWeight
        if let v = arguments?["maxHeight"] as? String { maxHeight = Double(v) ?? maxHeight }
        if let v = arguments?["maxWidth"] as? String { maxWidth = Double(v) ?? maxWidth }
        if let v = arguments?["maxWeight"] as? String { maxWeight = Double(v) ?? maxWeight }

        routeOptions.maximumHeight = Measurement<UnitLength>(value: maxHeight, unit: .meters)
        routeOptions.maximumWidth = Measurement<UnitLength>(value: maxWidth, unit: .meters)
        routeOptions.maximumWeight = Measurement<UnitMass>(value: maxWeight, unit: .metricTons)
        if let allowsUTurn = _allowsUTurnAtWayPoints { routeOptions.allowsUTurnAtWaypoint = allowsUTurn }
        routeOptions.refreshingEnabled = _enableRefresh
        routeOptions.includesAlternativeRoutes = _alternatives
        routeOptions.unitMeasurementSystem = _voiceUnits == "imperial" ? .imperial : .metric
        routeOptions.locale = Locale(identifier: _language)
        self.routeOptions = routeOptions

        // mapboxNavigationProvider is a shared, process-lifetime singleton (see
        // NavigationFactory) — re-subscribe this view's Combine bridges to it
        // rather than tearing down and recreating the provider itself, which
        // is what used to race against Flutter's async platform-view teardown
        // and trigger MapboxNavigationProvider's "instantiated twice" crash.
        subscribeToNavigationProvider()

        _routeCalculationTask?.cancel()
        _routeCalculationTask = Task { [weak self] in
            guard let self else { return }
            do {
                let routes = try await self.mapboxNavigation
                    .routingProvider()
                    .calculateRoutes(options: routeOptions)
                    .value
                await MainActor.run {
                    self.navigationRoutes = routes
                    self.navigationMapView?.showcaseRoutes(routes, animated: true)
                    self._distanceRemaining = routes.mainRoute.route.distance
                    self._durationRemaining = routes.mainRoute.route.expectedTravelTime
                    self.sendEvent(eventType: MapBoxEventType.route_built)
                    flutterResult(true)
                }
            } catch {
                await MainActor.run {
                    self._routeBuildResponse = error.localizedDescription
                    self.sendEvent(eventType: MapBoxEventType.route_build_failed)
                    flutterResult(true)
                }
            }
        }
    }

    func startEmbeddedNavigation(arguments: NSDictionary?, result: @escaping FlutterResult) {
        guard let response = self.navigationRoutes, let routeOptions else { return }
        let dayStyle = makeDayStyle()
        let nightStyle = makeNightStyle()
        let navigationOptions = NavigationOptions(
            mapboxNavigation: mapboxNavigation,
            voiceController: mapboxNavigationProvider.routeVoiceController,
            eventsManager: mapboxNavigationProvider.eventsManager(),
            styles: [dayStyle, nightStyle],
            predictiveCacheManager: mapboxNavigationProvider.predictiveCacheManager,
            navigationMapView: navigationMapView
        )
        _navigationViewController = NavigationViewController(
            navigationRoutes: response,
            navigationOptions: navigationOptions
        )
        _navigationViewController!.delegate = self

        guard let flutterViewController = rootFlutterViewController() else { return }
        flutterViewController.addChild(_navigationViewController!)

        let container = self.view()
        container.addSubview(_navigationViewController!.view)
        _navigationViewController!.view.translatesAutoresizingMaskIntoConstraints = false
        constraintsWithPaddingBetween(holderView: container, topView: _navigationViewController!.view, padding: 0.0)
        flutterViewController.didMove(toParent: flutterViewController)
        result(true)
    }

    func startNonEmbeddedNavigation(arguments: NSDictionary?, result: @escaping FlutterResult) {
        guard let response = self.navigationRoutes else { return }
        isEmbeddedNavigation = false

        let dayStyle = makeDayStyle()
        let nightStyle = makeNightStyle()
        let navigationOptions = NavigationOptions(
            mapboxNavigation: mapboxNavigation,
            voiceController: mapboxNavigationProvider.routeVoiceController,
            eventsManager: mapboxNavigationProvider.eventsManager(),
            styles: [dayStyle, nightStyle],
            predictiveCacheManager: mapboxNavigationProvider.predictiveCacheManager
        )

        if _navigationViewController == nil {
            _navigationViewController = NavigationViewController(
                navigationRoutes: response,
                navigationOptions: navigationOptions
            )
            _navigationViewController!.modalPresentationStyle = .fullScreen
            _navigationViewController!.delegate = self
            _navigationViewController!.navigationMapView?.localizeLabels()
        }
        guard let flutterViewController = rootFlutterViewController() else { return }
        flutterViewController.present(_navigationViewController!, animated: true, completion: nil)
    }

    private func rootFlutterViewController() -> FlutterViewController? {
        let keyWindow = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
            ?? UIApplication.shared.delegate?.window ?? nil
        return keyWindow?.rootViewController as? FlutterViewController
    }

    func constraintsWithPaddingBetween(holderView: UIView, topView: UIView, padding: CGFloat) {
        guard holderView.subviews.contains(topView) else { return }
        topView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            topView.topAnchor.constraint(equalTo: holderView.topAnchor, constant: padding),
            topView.bottomAnchor.constraint(equalTo: holderView.bottomAnchor, constant: -padding),
            topView.leadingAnchor.constraint(equalTo: holderView.leadingAnchor, constant: padding),
            topView.trailingAnchor.constraint(equalTo: holderView.trailingAnchor, constant: -padding)
        ])
    }

    func moveCameraToCoordinates(latitude: Double, longitude: Double) {
        let cameraOptions = CameraOptions(
            center: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
            zoom: _zoom,
            bearing: _bearing,
            pitch: _pitch
        )
        navigationMapView.mapView.mapboxMap.setCamera(to: cameraOptions)
    }

    func moveCameraToCenter() {
        let cameraOptions = CameraOptions(
            zoom: _zoom,
            bearing: _bearing,
            pitch: _pitch
        )
        navigationMapView.mapView.mapboxMap.setCamera(to: cameraOptions)
    }
}

extension FlutterMapboxNavigationView: AnnotationInteractionDelegate {
    public func annotationManager(_ manager: AnnotationManager, didDetectTappedAnnotations annotations: [Annotation]) {
        for group in pois {
            for annotation in group.annotation where annotation.id == annotations[0].id {
                _selectedAnnotation = annotation.id
                break
            }
        }
        sendEvent(eventType: MapBoxEventType.annotation_tapped)
    }
}


extension FlutterMapboxNavigationView: NavigationMapViewDelegate {
    public func mapViewDidFinishLoadingMap(_ mapView: NavigationMapView) {
        _mapInitialized = true
        sendEvent(eventType: MapBoxEventType.map_ready)
        moveCameraToCenter()
    }

    public func navigationMapView(_ mapView: NavigationMapView, didSelect alternativeRoute: AlternativeRoute) {
        Task { [weak self] in
            guard let self else { return }
            guard let updated = try? await self.navigationRoutes?.selecting(alternativeRoute: alternativeRoute) else { return }
            await MainActor.run { self.navigationRoutes = updated }
        }
    }
}

extension FlutterMapboxNavigationView: UIGestureRecognizerDelegate {
    public func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc func handleLongPress(_ gesture: UILongPressGestureRecognizer) {
        guard gesture.state == .ended else { return }
        let location = navigationMapView.mapView.mapboxMap.coordinate(for: gesture.location(in: navigationMapView.mapView))
        requestRoute(destination: location)
    }

    func requestRoute(destination: CLLocationCoordinate2D) {
        sendEvent(eventType: MapBoxEventType.route_building)

        guard let userLocation = navigationMapView.mapView.location.latestLocation else { return }
        let location = CLLocation(latitude: userLocation.coordinate.latitude, longitude: userLocation.coordinate.longitude)
        let userWaypoint = Waypoint(coordinate: location.coordinate, name: "Current Location")
        let destinationWaypoint = Waypoint(coordinate: destination)

        var mode: ProfileIdentifier = .automobileAvoidingTraffic
        if _navigationMode == "cycling" { mode = .cycling }
        else if _navigationMode == "driving" { mode = .automobile }
        else if _navigationMode == "walking" { mode = .walking }

        let routeOptions = UnscrambledRouteOptions(waypoints: [userWaypoint, destinationWaypoint], profileIdentifier: mode, queryItems: [])
        routeOptions.maximumHeight = Measurement<UnitLength>(value: _maxHeight, unit: .meters)
        routeOptions.maximumWidth = Measurement<UnitLength>(value: _maxWidth, unit: .meters)
        routeOptions.maximumWeight = Measurement<UnitMass>(value: _maxWeight, unit: .metricTons)
        if let avoid = _avoid { routeOptions.setExcludes(array: avoid) }

        _routeCalculationTask?.cancel()
        _routeCalculationTask = Task { [weak self] in
            guard let self else { return }
            do {
                let routes = try await self.mapboxNavigation.routingProvider()
                    .calculateRoutes(options: routeOptions)
                    .value
                await MainActor.run {
                    self.navigationRoutes = routes
                    self.sendEvent(eventType: MapBoxEventType.route_built)
                    self.navigationMapView?.showcaseRoutes(routes, animated: true)
                }
            } catch {
                await MainActor.run {
                    self.sendEvent(eventType: MapBoxEventType.route_build_failed)
                }
            }
        }
    }
}

extension FlutterMapboxNavigationView: CLLocationManagerDelegate {
    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        locationBridge.send(location)
    }

    public func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        headingBridge.send(newHeading)
    }

    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedWhenInUse ||
           manager.authorizationStatus == .authorizedAlways {
            manager.startUpdatingLocation()
            manager.startUpdatingHeading()
        }
    }
}

class UnscrambledRouteOptions: NavigationRouteOptions {
    public var excludePoints: [String] = []

    override var urlQueryItems: [URLQueryItem] {
        var items = super.urlQueryItems
        if !excludePoints.isEmpty {
            items.append(.init(name: "exclude", value: excludePoints.joined(separator: ",")))
        }
        return items
    }

    func setExcludes(array: [String]) {
        excludePoints = array
    }
}
