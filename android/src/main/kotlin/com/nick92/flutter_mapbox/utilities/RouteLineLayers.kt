package com.nick92.flutter_mapbox.utilities

import android.content.Context
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.locationcomponent.LocationComponentConstants
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions

/**
 * Where the route line goes in the layer stack: just below the style's road
 * labels, so street names, POIs and place labels stay readable on top of it.
 *
 * The anchor layer's name depends on the style — Mapbox's navigation styles
 * (navigation-day-v1 / navigation-night-v1) call it "road-label-navigation",
 * Streets calls it "road-label". Anchoring to a layer that doesn't exist makes
 * the SDK add the line at the very top, over every label and the puck — which
 * is what happened with the old hard-coded "road-label". So pick from the
 * loaded style, falling back to just under the location puck.
 */
object RouteLineLayers {
    private val LABEL_ANCHORS = listOf(
        "road-label-navigation",
        "road-label",
        "road-label-simple",
    )

    fun belowLayerId(style: Style): String =
        LABEL_ANCHORS.firstOrNull { style.styleLayerExists(it) }
            ?: LocationComponentConstants.LOCATION_INDICATOR_LAYER

    /** Options for a MapboxRouteLineView built once [style] has loaded. */
    fun viewOptions(context: Context, style: Style): MapboxRouteLineViewOptions =
        MapboxRouteLineViewOptions.Builder(context)
            .routeLineBelowLayerId(belowLayerId(style))
            .build()
}
