import Flutter
import UIKit
import MapboxMaps
@_spi(ExperimentalMapboxAPI) import MapboxNavigationCore
import MapboxNavigationUIKit

@MainActor
public class NavigationFactory: NSObject, FlutterStreamHandler {
    var _navigationViewController: NavigationViewController? = nil
    var _eventSink: FlutterEventSink? = nil

    let ALLOW_ROUTE_SELECTION = false
    let IsMultipleUniqueRoutes = false
    var isEmbeddedNavigation = false

    var _distanceRemaining: Double?
    var _durationRemaining: Double?
    var _navigationMode: String?
    var _navigationRoutes: NavigationRoutes?
    var _wayPoints = [Waypoint]()
    var _lastKnownLocation: CLLocation?
    var _routeBuildResponse: String?

    var _options: NavigationRouteOptions?
    var _simulateRoute = false
    var _allowsUTurnAtWayPoints: Bool?
    var _isOptimized = false
    var _language = "en"
    var _voiceUnits = "imperial"
    var _mapStyleUrlDay: String?
    var _mapStyleUrlNight: String?
    var _maxHeight: Double = 0.0
    var _maxWeight: Double = 0.0
    var _maxWidth: Double = 0.0
    var _avoid: [String]?
    var _alternatives: Bool = false
    var _enableRefresh: Bool = false
    var _zoom: Double = 14.0
    var _pitch: Double = 15.0
    var _bearing: Double = 0.0
    var _animateBuildRoute = true
    var _longPressDestinationEnabled = true
    var _shouldReRoute = true

    var _routeCalculationTask: Task<Void, Never>?

    // MapboxNavigationProvider enforces a process-wide uniqueness guard (see
    // MapboxNavigationProvider.checkInstanceIsUnique) and hard-crashes with
    // "instantiated twice" if a second instance is created before the first
    // has fully deallocated. Each FlutterMapboxNavigationView used to own its
    // own provider and recreate it per-view/per-buildRoute, which raced
    // against Flutter's async platform-view teardown on every navigate →
    // clear → navigate cycle. Sharing one static instance across every
    // NavigationFactory for the life of the process removes the race
    // entirely: there is only ever one instantiation to begin with.
    private static var _sharedMapboxNavigationProvider: MapboxNavigationProvider?

    var mapboxNavigationProvider: MapboxNavigationProvider {
        if Self._sharedMapboxNavigationProvider == nil {
            Self._sharedMapboxNavigationProvider = makeNavigationProvider()
        }
        return Self._sharedMapboxNavigationProvider!
    }

    var mapboxNavigation: MapboxNavigation {
        mapboxNavigationProvider.mapboxNavigation
    }

    private func makeNavigationProvider() -> MapboxNavigationProvider {
        MapboxNavigationProvider(coreConfig: .init(
            locationSource: _simulateRoute ? .simulation(initialLocation: nil) : .live
        ))
    }

    func startNavigation(arguments: NSDictionary?, result: @escaping FlutterResult) {
        _wayPoints.removeAll()

        guard let oWayPoints = arguments?["wayPoints"] as? NSDictionary else { return }

        var locations = [Location]()

        for item in oWayPoints as NSDictionary {
            let point = item.value as! NSDictionary
            guard let oName = point["Name"] as? String else { return }
            guard let oLatitude = point["Latitude"] as? Double else { return }
            guard let oLongitude = point["Longitude"] as? Double else { return }
            let order = point["Order"] as? Int
            locations.append(Location(name: oName, latitude: oLatitude, longitude: oLongitude, order: order))
        }

        // Waypoints cross the platform channel as an NSDictionary keyed by
        // index, whose iteration order is unspecified — `for item in
        // oWayPoints` above does not reliably preserve the order the Dart
        // side sent them in. `order` is always populated by the Dart
        // controller (WayPoint's position in its original list), so sort by
        // it unconditionally rather than gating on `_isOptimized`: this
        // method never calls a real route-optimization endpoint, so
        // `_isOptimized` has nothing to do with whether waypoint order can
        // be trusted here — skipping the sort just let the route get built
        // in whatever arbitrary order the dictionary happened to iterate.
        locations.sort { $0.order ?? 0 < $1.order ?? 0 }

        for loc in locations {
            _wayPoints.append(Waypoint(
                coordinate: CLLocationCoordinate2D(latitude: loc.latitude!, longitude: loc.longitude!),
                name: loc.name
            ))
        }

        _language = arguments?["language"] as? String ?? _language
        _voiceUnits = arguments?["units"] as? String ?? _voiceUnits
        _simulateRoute = arguments?["simulateRoute"] as? Bool ?? _simulateRoute
        _isOptimized = arguments?["isOptimized"] as? Bool ?? _isOptimized
        _allowsUTurnAtWayPoints = arguments?["allowsUTurnAtWayPoints"] as? Bool
        _navigationMode = arguments?["mode"] as? String ?? "drivingWithTraffic"

        if _wayPoints.count > 3 && arguments?["mode"] == nil {
            _navigationMode = "driving"
        }
        _mapStyleUrlDay = arguments?["mapStyleUrlDay"] as? String
        _mapStyleUrlNight = arguments?["mapStyleUrlNight"] as? String

        guard !_wayPoints.isEmpty else { return }

        if IsMultipleUniqueRoutes {
            startNavigationWithWayPoints(
                wayPoints: [_wayPoints.remove(at: 0), _wayPoints.remove(at: 0)],
                flutterResult: result
            )
        } else {
            startNavigationWithWayPoints(wayPoints: _wayPoints, flutterResult: result)
        }
    }

    func startNavigationWithWayPoints(wayPoints: [Waypoint], flutterResult: @escaping FlutterResult) {
        var mode: ProfileIdentifier = .automobileAvoidingTraffic
        if _navigationMode == "cycling" { mode = .cycling }
        else if _navigationMode == "driving" { mode = .automobile }
        else if _navigationMode == "walking" { mode = .walking }

        let options = NavigationRouteOptions(waypoints: wayPoints, profileIdentifier: mode)
        if let allowsUTurn = _allowsUTurnAtWayPoints { options.allowsUTurnAtWaypoint = allowsUTurn }
        options.unitMeasurementSystem = _voiceUnits == "imperial" ? .imperial : .metric
        options.locale = Locale(identifier: _language)

        // mapboxNavigationProvider is now a shared, process-lifetime singleton (see
        // its declaration above) — it is created once on first access and never
        // recreated, so _simulateRoute here only takes effect the very first time
        // any navigation view/session runs in this process.

        _routeCalculationTask?.cancel()
        _routeCalculationTask = Task { [weak self] in
            guard let self else { return }
            do {
                let navigationRoutes = try await self.mapboxNavigation
                    .routingProvider()
                    .calculateRoutes(options: options)
                    .value
                await MainActor.run {
                    self._options = options
                    let dayStyle = self.makeDayStyle()
                    let nightStyle = self.makeNightStyle()
                    let navigationOptions = NavigationOptions(
                        mapboxNavigation: self.mapboxNavigation,
                        voiceController: self.mapboxNavigationProvider.routeVoiceController,
                        eventsManager: self.mapboxNavigationProvider.eventsManager(),
                        styles: [dayStyle, nightStyle],
                        predictiveCacheManager: self.mapboxNavigationProvider.predictiveCacheManager
                    )
                    self.presentNavigation(navigationRoutes: navigationRoutes, navOptions: navigationOptions)
                }
            } catch {
                await MainActor.run {
                    self.sendEvent(eventType: MapBoxEventType.route_build_failed)
                    flutterResult("An error occurred while calculating the route: \(error.localizedDescription)")
                }
            }
        }
    }

    private func presentNavigation(navigationRoutes: NavigationRoutes, navOptions: NavigationOptions) {
        isEmbeddedNavigation = false
        if _navigationViewController == nil {
            _navigationViewController = NavigationViewController(
                navigationRoutes: navigationRoutes,
                navigationOptions: navOptions
            )
            _navigationViewController!.modalPresentationStyle = .fullScreen
            _navigationViewController!.delegate = self
        }
        let flutterViewController = UIApplication.shared.delegate?.window??.rootViewController as! FlutterViewController
        flutterViewController.present(_navigationViewController!, animated: true, completion: nil)
    }

    func endNavigation(result: FlutterResult?) {
        sendEvent(eventType: MapBoxEventType.navigation_finished)
        _routeCalculationTask?.cancel()
        guard let navVC = _navigationViewController else { return }
        if isEmbeddedNavigation {
            navVC.view.removeFromSuperview()
            _navigationViewController = nil
        } else {
            navVC.dismiss(animated: true) { [weak self] in
                self?._navigationViewController = nil
                result?(true)
            }
        }
    }

    func getLastKnownLocation() -> Waypoint {
        Waypoint(coordinate: CLLocationCoordinate2D(
            latitude: _lastKnownLocation!.coordinate.latitude,
            longitude: _lastKnownLocation!.coordinate.longitude
        ))
    }

    func makeDayStyle() -> StandardDayStyle {
        let style = StandardDayStyle()
        if let urlString = _mapStyleUrlDay, let url = URL(string: urlString) {
            style.mapStyleURL = url
        }
        return style
    }

    func makeNightStyle() -> StandardNightStyle {
        let style = StandardNightStyle()
        if let urlString = _mapStyleUrlNight, let url = URL(string: urlString) {
            style.mapStyleURL = url
        }
        return style
    }

    func sendEvent(eventType: MapBoxEventType, data: String = "") {
        let routeEvent = MapBoxRouteEvent(eventType: eventType, data: data)
        let jsonEncoder = JSONEncoder()
        guard let jsonData = try? jsonEncoder.encode(routeEvent),
              let eventJson = String(data: jsonData, encoding: .utf8) else { return }
        _eventSink?(eventJson)
    }

    func downloadOfflineRoute(arguments: NSDictionary?, flutterResult: @escaping FlutterResult) {
        // Offline tile pack download was removed in Navigation SDK v3.
        // Use mapboxNavigationProvider.predictiveCacheManager for predictive caching instead.
        flutterResult(false)
    }

    // MARK: FlutterStreamHandler

    public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        _eventSink = events
        return nil
    }

    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        _eventSink = nil
        return nil
    }
}

extension NavigationFactory: NavigationViewControllerDelegate {
    public func navigationViewController(
        _ navigationViewController: NavigationViewController,
        didUpdate progress: RouteProgress,
        with location: CLLocation,
        rawLocation: CLLocation
    ) {
        _lastKnownLocation = location
        _distanceRemaining = progress.distanceRemaining
        _durationRemaining = progress.durationRemaining
        sendEvent(eventType: MapBoxEventType.navigation_running)

        guard let eventSink = _eventSink else { return }
        let jsonEncoder = JSONEncoder()
        guard let jsonData = try? jsonEncoder.encode(MapBoxRouteProgressEvent(progress: progress)),
              let progressEventJson = String(data: jsonData, encoding: .ascii) else { return }
        eventSink(progressEventJson)

        if progress.isFinalLeg && progress.currentLegProgress.userHasArrivedAtWaypoint {
            _eventSink = nil
        }
    }

    public func navigationViewController(
        _ navigationViewController: NavigationViewController,
        didArriveAt waypoint: Waypoint
    ) -> Bool {
        sendEvent(eventType: MapBoxEventType.on_arrival, data: "true")
        if !_wayPoints.isEmpty && IsMultipleUniqueRoutes {
            // Multi-leg route continuation — calculate next leg
            let nextWaypoints = [getLastKnownLocation(), _wayPoints.remove(at: 0)]
            _routeCalculationTask?.cancel()
            _routeCalculationTask = Task { [weak self] in
                guard let self, let options = self._options else { return }
                options.waypoints = nextWaypoints
                do {
                    _ = try await self.mapboxNavigation.routingProvider()
                        .calculateRoutes(options: options)
                        .value
                    await MainActor.run {
                        self.sendEvent(eventType: MapBoxEventType.route_built)
                    }
                } catch {
                    await MainActor.run {
                        self.sendEvent(eventType: MapBoxEventType.route_build_failed, data: error.localizedDescription)
                    }
                }
            }
            return false
        }
        return true
    }

    public func navigationViewControllerDidDismiss(
        _ navigationViewController: NavigationViewController,
        byCanceling canceled: Bool
    ) {
        if canceled { sendEvent(eventType: MapBoxEventType.navigation_cancelled) }
        endNavigation(result: nil)
    }

    public func navigationViewController(
        _ navigationViewController: NavigationViewController,
        shouldRerouteFrom location: CLLocation
    ) -> Bool {
        _shouldReRoute
    }
}
