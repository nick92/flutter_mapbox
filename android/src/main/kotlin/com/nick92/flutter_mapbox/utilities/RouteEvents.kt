package com.nick92.flutter_mapbox.utilities

import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.geojson.LineString
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.core.directions.session.RoutesExtra
import com.mapbox.navigation.core.directions.session.RoutesUpdatedResult
import com.nick92.flutter_mapbox.models.MapBoxEvents

/** Route payloads shared by route_built and reroute_along. */
object RouteEvents {

    // The embedded map and the full-screen activity both observe the one
    // shared MapboxNavigation, so each reroute reaches two observers — only
    // the first may report it.
    private var lastReroutedRouteId: String? = null

    /**
     * One entry per route, primary first: duration, distance, and
     * `coordinates` — the route line as [[lng, lat], ...], so the app can
     * check it against its own restrictions data.
     */
    fun routesJson(routes: List<NavigationRoute>): String {
        val routesJson = org.json.JSONArray()
        for (r in routes) {
            val obj = org.json.JSONObject()
            obj.put("duration", r.directionsRoute.duration() ?: 0.0)
            obj.put("distance", r.directionsRoute.distance() ?: 0.0)
            val coords = org.json.JSONArray()
            val geometry = r.directionsRoute.geometry()
            if (geometry != null) {
                // applyDefaultNavigationOptions requests polyline6.
                val precision =
                    if (r.directionsRoute.routeOptions()?.geometries() == DirectionsCriteria.GEOMETRY_POLYLINE) 5 else 6
                for (p in LineString.fromPolyline(geometry, precision).coordinates()) {
                    coords.put(org.json.JSONArray().put(p.longitude()).put(p.latitude()))
                }
            }
            obj.put("coordinates", coords)
            routesJson.put(obj)
        }
        return routesJson.toString()
    }

    /**
     * Sends reroute_along — with the new route's line — when the SDK has
     * rerouted mid-navigation. Other route updates (a new request, a traffic
     * refresh, alternatives) are ignored: they aren't a new road.
     */
    fun onRoutesUpdated(result: RoutesUpdatedResult) {
        if (result.reason != RoutesExtra.ROUTES_UPDATE_REASON_REROUTE) return
        val primary = result.navigationRoutes.firstOrNull() ?: return
        if (primary.id == lastReroutedRouteId) return
        lastReroutedRouteId = primary.id
        PluginUtilities.sendEvent(MapBoxEvents.REROUTE_ALONG, routesJson(listOf(primary)))
    }
}
