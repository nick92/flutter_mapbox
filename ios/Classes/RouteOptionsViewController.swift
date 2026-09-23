import Flutter
import UIKit
import MapboxDirections
import MapboxNavigationCore
@_spi(ExperimentalMapboxAPI) import MapboxNavigationUIKit

// Only instantiated when ALLOW_ROUTE_SELECTION = true in NavigationFactory (currently disabled).
@MainActor
public class RouteOptionsViewController: UIViewController, NavigationMapViewDelegate {
    var routeOptions: NavigationRouteOptions?
    var navigationRoutes: NavigationRoutes?

    init(navigationRoutes: NavigationRoutes, options: NavigationRouteOptions) {
        self.navigationRoutes = navigationRoutes
        self.routeOptions = options
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
    }
}
