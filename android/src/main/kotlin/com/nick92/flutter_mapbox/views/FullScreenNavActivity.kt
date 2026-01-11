package com.nick92.flutter_mapbox.views

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.api.directions.v5.models.*
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.*
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.replay.MapboxReplayer
import com.mapbox.navigation.core.replay.ReplayLocationEngine
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.dropin.map.MapViewObserver
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.tripprogress.model.*
import com.nick92.flutter_mapbox.FlutterMapboxPlugin
import com.nick92.flutter_mapbox.R
import com.nick92.flutter_mapbox.databinding.NavigationActivityBinding
import com.nick92.flutter_mapbox.models.MapBoxEvents
import com.nick92.flutter_mapbox.models.MapBoxRouteProgressEvent
import com.nick92.flutter_mapbox.utilities.PluginUtilities
import com.nick92.flutter_mapbox.utilities.PluginUtilities.Companion.sendEvent
import java.util.*

class FullscreenNavActivity : AppCompatActivity() {

    private var receiver: BroadcastReceiver? = null
    private var points: MutableList<Point> = mutableListOf()
    private var accessToken: String? = null
    
    // RENAMED property to avoid conflict with any Mapbox internal "mapboxNavigation" val
    private var mapboxNavInstance: MapboxNavigation? = null
    private lateinit var binding: NavigationActivityBinding
    
    private val mapboxReplayer = MapboxReplayer()
    private val replayProgressObserver = ReplayProgressObserver(mapboxReplayer)

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setTheme(androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
        
        binding = NavigationActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        accessToken = PluginUtilities.getResourceFromContext(this.applicationContext, "mapbox_access_token")

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                finish()
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, IntentFilter(FullscreenNavigationLauncher.KEY_STOP_NAVIGATION), RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, IntentFilter(FullscreenNavigationLauncher.KEY_STOP_NAVIGATION))
        }

        val p = intent.getSerializableExtra("waypoints") as? MutableList<Point>
        if(p != null) points = p

        // 1. Setup the SDK
        MapboxNavigationApp.setup(
            NavigationOptions.Builder(this.applicationContext)
                .accessToken(accessToken)
                .build()
        )

        // 2. USE A LOCAL VARIABLE FIRST, THEN ASSIGN TO PROPERTY
        // This bypasses the "val" check on class-level properties
        val localNavReference = MapboxNavigationApp.current()
        this.mapboxNavInstance = localNavReference

        val styleUrl = FlutterMapboxPlugin.mapStyleUrlDay ?: Style.MAPBOX_STREETS

        binding.navigationView.customizeViewStyles {
            style {
              var  styleUri = styleUrl
            }
        }

        val observer = object : MapViewObserver() {}
        val internalMapView: MapView = MapView(this.applicationContext)
        binding.navigationView.registerMapObserver(observer)
        observer.onAttached(internalMapView)
    }

    override fun onStart() {
        super.onStart()
        mapboxNavInstance?.registerRoutesObserver(routesObserver)
        mapboxNavInstance?.registerRouteProgressObserver(routeProgressObserver)
        mapboxNavInstance?.registerLocationObserver(locationObserver)
        mapboxNavInstance?.registerRouteProgressObserver(replayProgressObserver)

        if (mapboxNavInstance?.getNavigationRoutes()?.isEmpty() == true) {
            mapboxReplayer.pushEvents(
                listOf(
                    ReplayRouteMapper.mapToUpdateLocation(
                        eventTimestamp = 0.0,
                        point = Point.fromLngLat(-122.39726512303575, 37.785128345296805)
                    )
                )
            )
            mapboxReplayer.playFirstLocation()
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if(FlutterMapboxPlugin.currentRoute != null){
            setRouteAndStartNavigation(FlutterMapboxPlugin.currentRoute!!)
        } else if (points.size >= 2) {
            findRoute(origin = points[0], destination = points[1])
        }
    }

    override fun onStop() {
        super.onStop()
        mapboxNavInstance?.unregisterRoutesObserver(routesObserver)
        mapboxNavInstance?.unregisterRouteProgressObserver(routeProgressObserver)
        mapboxNavInstance?.unregisterLocationObserver(locationObserver)
        mapboxNavInstance?.unregisterRouteProgressObserver(replayProgressObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapboxReplayer.finish()
        try {
            receiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {}
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sendEvent(MapBoxEvents.NAVIGATION_FINISHED)
    }

    private fun findRoute(origin: Point, destination: Point) {
        mapboxNavInstance?.requestRoutes(
            RouteOptions.builder()
                .applyDefaultNavigationOptions()
                .applyLanguageAndVoiceUnitOptions(this)
                .coordinatesList(listOf(origin, destination))
                .language(FlutterMapboxPlugin.navigationLanguage)
                .alternatives(FlutterMapboxPlugin.showAlternateRoutes)
                .voiceUnits(FlutterMapboxPlugin.navigationVoiceUnits)
                .bannerInstructions(FlutterMapboxPlugin.bannerInstructionsEnabled)
                .voiceInstructions(FlutterMapboxPlugin.voiceInstructionsEnabled)
                .steps(true)
                .bearingsList(listOf(Bearing.builder().angle(1.0).degrees(45.0).build(), null))
                .layersList(listOf(mapboxNavInstance?.getZLevel() ?: 0, null))
                .build(),
            object : NavigationRouterCallback {
                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: RouterOrigin) {
                    setRoutesAndStartNavigation(routes)
                }
                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    sendEvent(MapBoxEvents.ROUTE_BUILD_FAILED)
                }
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
                    sendEvent(MapBoxEvents.ROUTE_BUILD_CANCELLED)
                }
            }
        )
    }

    private fun setRoutesAndStartNavigation(routes: List<NavigationRoute>) {
        mapboxNavInstance?.setNavigationRoutes(routes)
        binding.navigationView.api.startActiveGuidance(routes)
        sendEvent(MapBoxEvents.NAVIGATION_RUNNING)
    }

    private fun setRouteAndStartNavigation(route: NavigationRoute) {
        binding.navigationView.api.startActiveGuidance(listOf(route))
        sendEvent(MapBoxEvents.NAVIGATION_RUNNING)
    }

    private val navigationLocationProvider = NavigationLocationProvider()
    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: Location) {}
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            navigationLocationProvider.changePosition(
                location = locationMatcherResult.enhancedLocation,
                keyPoints = locationMatcherResult.keyPoints,
            )
        }
    }

    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        try {
            FlutterMapboxPlugin.distanceRemaining = routeProgress.distanceRemaining
            FlutterMapboxPlugin.durationRemaining = routeProgress.durationRemaining
            val progressEvent = MapBoxRouteProgressEvent(routeProgress)
            PluginUtilities.sendEvent(progressEvent)
        } catch (_: Exception) {}
    }

    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            PluginUtilities.sendEvent(MapBoxEvents.REROUTE_ALONG)
        }
    }
}