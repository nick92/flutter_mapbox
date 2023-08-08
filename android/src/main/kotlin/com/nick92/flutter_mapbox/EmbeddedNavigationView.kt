package com.nick92.flutter_mapbox

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Expected
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.maps.*
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.delegates.listeners.OnCameraChangeListener
import com.mapbox.maps.plugin.delegates.listeners.OnMapIdleListener
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.navigation.base.TimeFormat
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.*
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.replay.MapboxReplayer
import com.mapbox.navigation.core.replay.ReplayLocationEngine
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.routealternatives.NavigationRouteAlternativesObserver
import com.mapbox.navigation.core.routealternatives.RouteAlternativesError
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.ui.base.util.MapboxNavigationConsumer
import com.mapbox.navigation.ui.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.ui.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.camera.transition.NavigationCameraTransitionOptions
import com.mapbox.navigation.ui.maps.camera.view.MapboxRecenterButton
import com.mapbox.navigation.ui.maps.camera.view.MapboxRouteOverviewButton
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.ui.tripprogress.api.MapboxTripProgressApi
import com.mapbox.navigation.ui.tripprogress.model.*
import com.mapbox.navigation.ui.tripprogress.view.MapboxTripProgressView
import com.mapbox.navigation.ui.voice.api.MapboxSpeechApi
import com.mapbox.navigation.ui.voice.api.MapboxVoiceInstructionsPlayer
import com.mapbox.navigation.ui.voice.model.SpeechAnnouncement
import com.mapbox.navigation.ui.voice.model.SpeechError
import com.mapbox.navigation.ui.voice.model.SpeechValue
import com.mapbox.navigation.ui.voice.model.SpeechVolume
import com.mapbox.navigation.ui.voice.view.MapboxSoundButton
import com.nick92.flutter_mapbox.databinding.MapActivityBinding
import com.nick92.flutter_mapbox.models.MapBoxEvents
import com.nick92.flutter_mapbox.models.MapBoxRouteProgressEvent
import com.nick92.flutter_mapbox.utilities.PluginUtilities
import com.nick92.flutter_mapbox.views.FullscreenNavigationLauncher
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.*


open class EmbeddedNavigationView(ctx: Context, act: Activity, bind: MapActivityBinding, accessToken: String):  MethodChannel.MethodCallHandler, EventChannel.StreamHandler,
    Application.ActivityLifecycleCallbacks {

    open fun initFlutterChannelHandlers() {
        methodChannel?.setMethodCallHandler(this)
        eventChannel?.setStreamHandler(this)
    }

    open fun initNavigation(mv: MapView, arguments: Map<*, *>) {
        mapView = mv
        mapboxMap = mapView.getMapboxMap()

        mapView.compass.visibility = false
        mapView.scalebar.enabled = false

        setOptions(arguments)

        // initialize the location puck
        mapView.location.apply {
            this.locationPuck = LocationPuck2D(
                bearingImage = ContextCompat.getDrawable(
                    context,
                    R.drawable.mapbox_user_icon
                ),
            )
            setLocationProvider(navigationLocationProvider)
            enabled = true
        }

        // initialize Mapbox Navigation
        mapboxNavigation = if (MapboxNavigationProvider.isCreated()) {
            MapboxNavigationProvider.retrieve()
        } else if (simulateRoute) {
            MapboxNavigationProvider.create(
                NavigationOptions.Builder(context)
                    .accessToken(token)
                    // .locationEngine(replayLocationEngine)
                    .build()
            )
        } else {
            MapboxNavigationProvider.create(
                NavigationOptions.Builder(context)
                    .accessToken(token)
                    .build()
            )
        }

        // initialize Navigation Camera
        viewportDataSource = MapboxNavigationViewportDataSource(mapboxMap)
        navigationCamera = NavigationCamera(
            mapboxMap,
            mapView.camera,
            viewportDataSource
        )
        // set the animations lifecycle listener to ensure the NavigationCamera stops
        // automatically following the user location when the map is interacted with
        mapView.camera.addCameraAnimationsLifecycleListener(
            NavigationBasicGesturesHandler(navigationCamera)
        )
        navigationCamera.registerNavigationCameraStateChangeObserver { navigationCameraState ->
            // shows/hide the recenter button depending on the camera state
            when (navigationCameraState) {
                NavigationCameraState.TRANSITION_TO_FOLLOWING,
                NavigationCameraState.FOLLOWING -> binding.recenter.visibility = View.INVISIBLE
                NavigationCameraState.TRANSITION_TO_OVERVIEW,
                NavigationCameraState.OVERVIEW,
                NavigationCameraState.IDLE -> binding.recenter.visibility = View.INVISIBLE
            }
        }
        // set the padding values depending on screen orientation and visible view layout
        if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            viewportDataSource.overviewPadding = landscapeOverviewPadding
        } else {
            viewportDataSource.overviewPadding = overviewPadding
        }
        if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            viewportDataSource.followingPadding = landscapeFollowingPadding
        } else {
            viewportDataSource.followingPadding = followingPadding
        }

        // make sure to use the same DistanceFormatterOptions across different features
        val distanceFormatterOptions = mapboxNavigation.navigationOptions.distanceFormatterOptions

        // initialize maneuver api that feeds the data to the top banner maneuver view
        maneuverApi = MapboxManeuverApi(
            MapboxDistanceFormatter(distanceFormatterOptions)
        )

        // initialize bottom progress view
        tripProgressApi = MapboxTripProgressApi(
            TripProgressUpdateFormatter.Builder(activity)
                .distanceRemainingFormatter(
                    DistanceRemainingFormatter(distanceFormatterOptions)
                )
                .timeRemainingFormatter(
                    TimeRemainingFormatter(activity)
                )
                .percentRouteTraveledFormatter(
                    PercentDistanceTraveledFormatter()
                )
                .estimatedTimeToArrivalFormatter(
                    EstimatedTimeToArrivalFormatter(activity, TimeFormat.NONE_SPECIFIED)
                )
                .build()
        )

        // initialize voice instructions api and the voice instruction player
        speechApi = MapboxSpeechApi(
            activity,
            token,
            navigationLanguage
        )
        voiceInstructionsPlayer = MapboxVoiceInstructionsPlayer(
            activity,
            token,
            navigationLanguage
        )

        // initialize route line, the withRouteLineBelowLayerId is specified to place
        // the route line below road labels layer on the map
        // the value of this option will depend on the style that you are using
        // and under which layer the route line should be placed on the map layers stack
        val mapboxRouteLineOptions = MapboxRouteLineOptions.Builder(activity)
            .withRouteLineBelowLayerId("road-label")
            .build()
        routeLineApi = MapboxRouteLineApi(mapboxRouteLineOptions)
        routeLineView = MapboxRouteLineView(mapboxRouteLineOptions)

        // initialize maneuver arrow view to draw arrows on the map
        val routeArrowOptions = RouteArrowOptions.Builder(activity).build()
        routeArrowView = MapboxRouteArrowView(routeArrowOptions)

        var styleUrl = FlutterMapboxPlugin.mapStyleUrlDay
        if (styleUrl == null) styleUrl = Style.MAPBOX_STREETS
        // load map style if set if not default
        mapboxMap.loadStyleUri(
            styleUrl
        ) {
            if(longPressDestinationEnabled)
            {
                // add long click listener that search for a route to the clicked destination
                binding.mapView.gestures.addOnMapLongClickListener { point ->
                    wayPoints.clear()

                    val originLocation = navigationLocationProvider.lastLocation
                    val originPoint = originLocation?.let {
                        Point.fromLngLat(it.longitude, it.latitude)
                    }
                    wayPoints.add(originPoint!!)
                    wayPoints.add(point)

                    getRoute(context)
                    true
                }
            }
        }

        // initialize view interactions
        binding.stop.setOnClickListener {
            clearRouteAndStopNavigation()
        }

        recenterButton = MapboxRecenterButton(context)
        overviewButton = MapboxRouteOverviewButton(context)
        soundButton = MapboxSoundButton(context)

        recenterButton.setOnClickListener {
            navigationCamera.requestNavigationCameraToFollowing()
            binding.routeOverview.showTextAndExtend(BUTTON_ANIMATION_DURATION)
        }
        overviewButton.setOnClickListener {
            navigationCamera.requestNavigationCameraToOverview()
            binding.recenter.showTextAndExtend(BUTTON_ANIMATION_DURATION)
        }
        soundButton.setOnClickListener {
            // mute/unmute voice instructions
            isVoiceInstructionsMuted = !isVoiceInstructionsMuted
        }

        // set initial sounds button state
        soundButton.mute()
        isVoiceInstructionsMuted = true

        mapView.gestures.addOnMapClickListener(mapClickListener)

        mapboxMap.addOnMapIdleListener(addOnMapIdleListener)
        mapboxMap.addOnCameraChangeListener(onCameraChangeListener)
        // initialize navigation trip observers
        registerObservers()
        mapboxNavigation.startTripSession(withForegroundService = false)
    }

    override fun onMethodCall(methodCall: MethodCall, result: MethodChannel.Result) {
        when (methodCall.method) {
            "getPlatformVersion" -> {
                result.success("Android ${Build.VERSION.RELEASE}")
            }
            "enableOfflineRouting" -> {
                //downloadRegionForOfflineRouting(call, result)
            }
            "buildRoute" -> {
                buildRoute(methodCall, result)
            }
            "updateCamera" -> {
                updateCamera(methodCall, result)
            }
            "clearRoute" -> {
                clearRoute(methodCall, result)
            }
            "startNavigation" -> {
                startNavigation(methodCall, result)
            }
            "startFullScreenNavigation" -> {
                beginFullScreenNavigation()
            }
            "finishNavigation" -> {
                finishNavigation(methodCall, result)
            }
            "getDistanceRemaining" -> {
                result.success(distanceRemaining)
            }
            "getCenterCoordinates" -> {
                result.success(centerCoords)
            }
            "getDurationRemaining" -> {
                result.success(durationRemaining)
            }
            "getSelectedAnnotation" -> {
                result.success(selectedAnnotation)
            }
            "reCenter" -> {
                navigationCamera.requestNavigationCameraToOverview(
                    stateTransitionOptions = NavigationCameraTransitionOptions.Builder()
                        .maxDuration(0) // instant transition
                        .build()
                )
            }
            "setPOIs" -> {
                addPOIs(methodCall, result)
            }
            else -> result.notImplemented()
        }
    }

    private fun buildRoute(methodCall: MethodCall, result: MethodChannel.Result) {
        isNavigationCanceled = false
        isNavigationInProgress = false

        val arguments = methodCall.arguments as? Map<*, *>
        if(arguments != null)
            setOptions(arguments)
        val mapReady = true
        if (mapReady) {
            wayPoints.clear()
            val points = arguments?.get("wayPoints") as HashMap<*, *>

            for (item in points)
            {
                val point = item.value as HashMap<*, *>
                val latitude = point["Latitude"] as Double
                val longitude = point["Longitude"] as Double
                wayPoints.add(Point.fromLngLat(longitude, latitude))
            }

            val height = arguments["maxHeight"] as? String
            val weight = arguments["maxWeight"] as? String
            val width = arguments["maxWidth"] as? String

            if(height != null)
                maxHeight = height.toDouble()
            if(weight != null)
                maxWeight = weight.toDouble()
            if(width != null)
                maxWidth = width.toDouble()

            var avoids = arguments["avoid"] as? List<String>

            if(avoids != null && avoids.isNotEmpty())
                excludeList = avoids

            getRoute(context)
            result.success(true)
        } else {
            result.success(false)
        }
    }

    private fun getRoute(context: Context) {
        if (!PluginUtilities.isNetworkAvailable(context)) {
            PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILD_FAILED, "No Internet Connection")
            return
        }

        PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILDING)

        mapboxNavigation.requestRoutes(
            RouteOptions.builder()
                .applyDefaultNavigationOptions()
                .applyLanguageAndVoiceUnitOptions(context)
                .coordinatesList(wayPoints)
                .waypointIndicesList(listOf(0, wayPoints.lastIndex))
                .excludeList(excludeList)
                .language(navigationLanguage)
                .alternatives(alternatives)
                .profile(navigationMode)
                .maxHeight(maxHeight)
                .maxWidth(maxWidth)
                .maxWeight(maxWeight)
                .continueStraight(!allowsUTurnAtWayPoints)
                .voiceUnits(navigationVoiceUnits)
                .annotations(DirectionsCriteria.ANNOTATION_DISTANCE)
                .enableRefresh(true)
                .build()
            , object : NavigationRouterCallback {
                override fun onRoutesReady(routes: List<NavigationRoute>,
                                           routerOrigin: RouterOrigin) {
                    if (routes.isEmpty()){
                        PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILD_NO_ROUTES_FOUND)
                        return
                    }

                    FlutterMapboxPlugin.currentRoute = routes[0]
                    durationRemaining = FlutterMapboxPlugin.currentRoute!!.directionsRoute.duration()
                    distanceRemaining = FlutterMapboxPlugin.currentRoute!!.directionsRoute.distance()

                    PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILT)
                    // Draw the route on the map
                    mapboxNavigation.setNavigationRoutes(routes)
                    // move the camera to overview when new route is available
                    navigationCamera.requestNavigationCameraToOverview()
                    isBuildingRoute = false
                    //Start Navigation again from new Point, if it was already in Progress
                    if (isNavigationInProgress) {
                        startNavigation()
                    }
                }
                override fun onFailure(reasons: List<RouterFailure>,
                                       routeOptions: RouteOptions
                ) {
                    var message = "an error occurred while building the route. Errors: "
                    for (reason in reasons){
                        message += reason.message
                    }
                    PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILD_FAILED, message)
                    isBuildingRoute = false
                }
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
                    PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILD_CANCELLED)
                }
            })
    }

    private fun beginFullScreenNavigation() {
        activity?.let { FullscreenNavigationLauncher.startNavigation(it, wayPoints) }
    }

    private fun addPOIs(methodCall: MethodCall, result: MethodChannel.Result) {
        val arguments = methodCall.arguments as? Map<*, *>

        if(arguments != null) {
            val groupName = arguments["group"] as? String
            val base64Image = arguments["icon"] as? String
            val poiPoints = arguments["poi"] as? HashMap<*, *>
            var poiImage: ByteArray? = null

            base64Image?.let {
                // Decode and use the image as needed
                poiImage = android.util.Base64.decode(it, 0)
            }

            var image = BitmapFactory.decodeByteArray(poiImage, 0, poiImage!!.size)
            addPOIAnnotations(image, poiPoints!!)
        }
        result.success(true)
    }

    private fun addPOIAnnotations(poiImage: Bitmap, pois: HashMap<*, *>) {
        val annotationApi = mapView.annotations
        pointAnnotationManager = annotationApi!!.createPointAnnotationManager()

        for (item in pois) {
            val poi = item.value as HashMap<*, *>
            val name = poi["Name"] as String
            val latitude = poi["Latitude"] as Double
            val longitude = poi["Longitude"] as Double

            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(longitude, latitude))
                .withIconImage(poiImage)

            pointAnnotationOptions.iconSize = 0.2
            pointAnnotationOptions.textOffset = listOf(0.0, 2.5)
            pointAnnotationOptions.textField = name
            pointAnnotationOptions.textSize = 12.0

            if(!containsName(name)) {
                listOfPoints.add(pointAnnotationOptions)
            }
        }

        pointAnnotationManager.addClickListener(onPointAnnotationClickListener)
        // Add the resulting pointAnnotation to the map.
        pointAnnotationManager.create(listOfPoints)
    }

    private fun containsName(nameToCheck: String): Boolean {
        for (point in listOfPoints) {
            if (point.textField == nameToCheck) {
                return true
            }
        }
        return false
    }

    private fun moveCameraToOriginOfRoute() {
        FlutterMapboxPlugin.currentRoute?.let {
            val originCoordinate = it.routeOptions?.coordinatesList()?.get(0)
            originCoordinate?.let {
                val location = LatLng(originCoordinate.latitude(), originCoordinate.longitude())
                updateCamera(location)
            }
        }
    }

    private fun clearRoute(methodCall: MethodCall, result: MethodChannel.Result) {
        mapboxNavigation.setNavigationRoutes(listOf())
        // stop simulation
        mapboxReplayer.stop()
    }

    private fun clearRouteAndStopNavigation() {
        mapboxNavigation.setNavigationRoutes(listOf())
        // stop simulation
        mapboxReplayer.stop()

        PluginUtilities.sendEvent(MapBoxEvents.NAVIGATION_CANCELLED)

    }

    private fun startNavigation(methodCall: MethodCall, result: MethodChannel.Result) {
        val arguments = methodCall.arguments as? Map<*, *>
        if(arguments != null)
            setOptions(arguments)

        startNavigation()

        if (FlutterMapboxPlugin.currentRoute != null) {
            result.success(true)
        } else {
            result.success(false)
        }
    }

    private fun finishNavigation(methodCall: MethodCall, result: MethodChannel.Result) {
        finishNavigation()

        if (FlutterMapboxPlugin.currentRoute != null) {
            result.success(true)
        } else {
            result.success(false)
        }
    }

    private fun startNavigation() {
        isNavigationCanceled = false

        if (FlutterMapboxPlugin.currentRoute != null) {
            if (simulateRoute) {
                mapboxReplayer.play()
            }
            mapboxNavigation.startTripSession()
            // show UI elements
            binding.soundButton.visibility = View.VISIBLE
            binding.routeOverview.visibility = View.VISIBLE
            binding.tripProgressView.visibility = View.VISIBLE

            // move the camera to overview when new route is available
            navigationCamera.requestNavigationCameraToFollowing()
            isNavigationInProgress = true
            PluginUtilities.sendEvent(MapBoxEvents.NAVIGATION_RUNNING)
        }
    }

    private fun finishNavigation(isOffRouted: Boolean = false) {
        // clear
        mapboxNavigation.setNavigationRoutes(listOf())

        zoom = zoom
        bearing = 0.0
        tilt = 0.0
        isNavigationCanceled = true

        if (!isOffRouted) {
            isNavigationInProgress = false
            moveCameraToOriginOfRoute()
        }

        if(simulateRoute)
            mapboxReplayer.stop()

        mapboxNavigation.stopTripSession()
    }

    private fun updateCamera(methodCall: MethodCall, result: MethodChannel.Result) {
        var arguments = methodCall.arguments as? Map<*, *>

        if(arguments != null) {
            var latitude = arguments["latitude"] as Double;
            var longitude = arguments["longitude"] as Double;

            updateCamera(LatLng(latitude, longitude));
        }
    }

    private fun updateCamera(location: LatLng) {
        val mapAnimationOptions = MapAnimationOptions.Builder().duration(1500L).build()
        mapView.camera.easeTo(
            CameraOptions.Builder()
                // Centers the camera to the lng/lat specified.
                .center(Point.fromLngLat(location.longitude, location.latitude))
                // specifies the zoom value. Increase or decrease to zoom in or zoom out
                .zoom(zoom)
                // specify frame of reference from the center.
                .build(),
            mapAnimationOptions
        )
    }

    private fun setOptions(arguments: Map<*, *>)
    {
        val navMode = arguments["mode"] as? String
        if(navMode != null)
        {
            if(navMode == "walking")
                navigationMode = DirectionsCriteria.PROFILE_WALKING;
            else if(navMode == "cycling")
                navigationMode = DirectionsCriteria.PROFILE_CYCLING;
            else if(navMode == "driving")
                navigationMode = DirectionsCriteria.PROFILE_DRIVING;
        }

        val simulated = arguments["simulateRoute"] as? Boolean
        if (simulated != null) {
            simulateRoute = simulated
        }

        val language = arguments["language"] as? String
        if(language != null)
            navigationLanguage = language

        val units = arguments["units"] as? String

        if(units != null)
        {
            if(units == "imperial")
                navigationVoiceUnits = DirectionsCriteria.IMPERIAL
            else if(units == "metric")
                navigationVoiceUnits = DirectionsCriteria.METRIC
        }

        val styleDay = arguments?.get("mapStyleUrlDay") as? String
        val styleNight = arguments?.get("mapStyleUrlNight") as? String

        if(styleDay != null)
            FlutterMapboxPlugin.mapStyleUrlDay = arguments?.get("mapStyleUrlDay") as? String

        if(styleNight != null)
            FlutterMapboxPlugin.mapStyleUrlNight = arguments?.get("mapStyleUrlNight") as? String

        initialLatitude = arguments["initialLatitude"] as? Double
        initialLongitude = arguments["initialLongitude"] as? Double

        val height = arguments["maxHeight"] as? String
        val weight = arguments["maxWeight"] as? String
        val width = arguments["maxWidth"] as? String

        if(height != null)
            maxHeight = height.toDouble()
        if(weight != null)
            maxWeight = weight.toDouble()
        if(width != null)
            maxWidth = width.toDouble()

        val zm = arguments["zoom"] as? Double
        if(zm != null)
            zoom = zm

        val br = arguments["bearing"] as? Double
        if(br != null)
            bearing = br

        val tt = arguments["tilt"] as? Double
        if(tt != null)
            tilt = tt

        val optim = arguments["isOptimized"] as? Boolean
        if(optim != null)
            isOptimized = optim

        val anim = arguments["animateBuildRoute"] as? Boolean
        if(anim != null)
            animateBuildRoute = anim

        val altRoute = arguments["alternatives"] as? Boolean
        if(altRoute != null)
            alternatives = altRoute

        val voiceEnabled = arguments["voiceInstructionsEnabled"] as? Boolean
        if(voiceEnabled != null)
            voiceInstructionsEnabled = voiceEnabled

        val bannerEnabled = arguments["bannerInstructionsEnabled"] as? Boolean
        if(bannerEnabled != null)
            bannerInstructionsEnabled = bannerEnabled

        val longPress = arguments["longPressDestinationEnabled"] as? Boolean
        if(longPress != null)
            longPressDestinationEnabled = longPress

//        val poiPoints = arguments["poi"] as? HashMap<*, *>
//
//        if(poiPoints != null)
//            addPOIAnnotations(poiPoints)

        var avoids = arguments["avoid"] as? List<String>

        if(avoids != null && avoids.isNotEmpty())
            excludeList = avoids
    }

    open fun registerObservers() {
        // register event listeners
        mapboxNavigation.registerRoutesObserver(routesObserver)
        mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.registerLocationObserver(locationObserver)
        mapboxNavigation.registerVoiceInstructionsObserver(voiceInstructionsObserver)
        mapboxNavigation.registerRouteProgressObserver(replayProgressObserver)
        mapboxNavigation.registerRouteAlternativesObserver(alternativesObserver)
    }

    open fun unregisterObservers() {
        // unregister event listeners to prevent leaks or unnecessary resource consumption
        mapboxNavigation.unregisterRoutesObserver(routesObserver)
        mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.unregisterLocationObserver(locationObserver)
        mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
        mapboxNavigation.unregisterRouteProgressObserver(replayProgressObserver)
        mapboxNavigation.unregisterRouteAlternativesObserver(alternativesObserver)
    }

    fun onDestroy() {
        MapboxNavigationProvider.destroy()
        mapboxReplayer.finish()
        maneuverApi.cancel()
        routeLineApi.cancel()
        routeLineView.cancel()
        speechApi.cancel()
        voiceInstructionsPlayer.shutdown()
    }

    //Flutter stream listener delegate methods
    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        FlutterMapboxPlugin.eventSink = events
    }

    override fun onCancel(arguments: Any?) {
        FlutterMapboxPlugin.eventSink = null
    }

    val context: Context = ctx
    val activity: Activity = act
    val token: String = accessToken
    open var methodChannel: MethodChannel? = null
    open var eventChannel: EventChannel? = null

    //Config
    var initialLatitude: Double? = null
    var initialLongitude: Double? = null

    val wayPoints: MutableList<Point> = mutableListOf()
    val pois: MutableList<PointAnnotation> = mutableListOf()
    var navigationMode =  DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
    var simulateRoute = false
    var mapStyleUrlDay: String? = null
    var mapStyleUrlNight: String? = null
    var navigationLanguage = "en"
    var navigationVoiceUnits = DirectionsCriteria.METRIC
    var zoom = 14.0
    var bearing = 0.0
    var tilt = 0.0
    var distanceRemaining: Double? = null
    var durationRemaining: Double? = null
    var centerCoords: MutableList<Double> = mutableListOf()
    var alternatives = true

    var mapMoved = false

    var allowsUTurnAtWayPoints = false
    var enableRefresh = false
    var voiceInstructionsEnabled = true
    var bannerInstructionsEnabled = true
    var longPressDestinationEnabled = true
    var animateBuildRoute = true
    var isOptimized = false
    var maxHeight: Double? = null
    var maxWeight: Double? = null
    var maxWidth: Double? = null
    var excludeList: List<String> = listOf()
    var originPoint: Point? = null
    var destinationPoint: Point? = null

//    private var currentRoute: NavigationRoute? = null
    private var isDisposed = false
    private var isRefreshing = false
    private var isBuildingRoute = false
    private var isNavigationInProgress = false
    private var isNavigationCanceled = false

    val BUTTON_ANIMATION_DURATION = 1500L

    val routeClickPadding = 30 * Resources.getSystem().displayMetrics.density

    /**
     * Debug tool used to play, pause and seek route progress events that can be used to produce mocked location updates along the route.
     */
    private val mapboxReplayer = MapboxReplayer()

    /**
     * Debug tool that mocks location updates with an input from the [mapboxReplayer].
     */
    private val replayLocationEngine = ReplayLocationEngine(mapboxReplayer)

    /**
     * Debug observer that makes sure the replayer has always an up-to-date information to generate mock updates.
     */
    private val replayProgressObserver = ReplayProgressObserver(mapboxReplayer)

    /**
     * Bindings to the example layout.
     */
    open val binding: MapActivityBinding = bind


    /**
     * MapView entry point obtained from the embedded view.
     * You need to get a new reference to this object whenever the [MapView] is recreated.
     */
    private lateinit var mapView: MapView

    /**
     * Mapbox Maps entry point obtained from the [MapView].
     * You need to get a new reference to this object whenever the [MapView] is recreated.
     */
    private lateinit var mapboxMap: MapboxMap

    private lateinit var soundButton: MapboxSoundButton

    private lateinit var overviewButton: MapboxRouteOverviewButton

    private lateinit var recenterButton: MapboxRecenterButton

    /**
     * Mapbox Navigation entry point. There should only be one instance of this object for the app.
     * You can use [MapboxNavigationProvider] to help create and obtain that instance.
     */
    private lateinit var mapboxNavigation: MapboxNavigation

    /**
     * Used to execute camera transitions based on the data generated by the [viewportDataSource].
     * This includes transitions from route overview to route following and continuously updating the camera as the location changes.
     */
    private lateinit var navigationCamera: NavigationCamera

    /**
     * Produces the camera frames based on the location and routing data for the [navigationCamera] to execute.
     */
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource

    /*
     * Below are generated camera padding values to ensure that the route fits well on screen while
     * other elements are overlaid on top of the map (including instruction view, buttons, etc.)
     */
    private val pixelDensity = Resources.getSystem().displayMetrics.density
    private val overviewPadding: EdgeInsets by lazy {
        EdgeInsets(
            140.0 * pixelDensity,
            40.0 * pixelDensity,
            120.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }
    private val landscapeOverviewPadding: EdgeInsets by lazy {
        EdgeInsets(
            130.0 * pixelDensity,
            380.0 * pixelDensity,
            110.0 * pixelDensity,
            20.0 * pixelDensity
        )
    }
    private val followingPadding: EdgeInsets by lazy {
        EdgeInsets(
            180.0 * pixelDensity,
            40.0 * pixelDensity,
            150.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }
    private val landscapeFollowingPadding: EdgeInsets by lazy {
        EdgeInsets(
            30.0 * pixelDensity,
            380.0 * pixelDensity,
            110.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }

    private var listOfPoints: MutableList<PointAnnotationOptions> = mutableListOf()

    private lateinit var selectedAnnotation: String

    private lateinit var pointAnnotationManager: PointAnnotationManager

    /**
     * Generates updates for the [MapboxManeuverView] to display the upcoming maneuver instructions
     * and remaining distance to the maneuver point.
     */
    private lateinit var maneuverApi: MapboxManeuverApi

    /**
     * Generates updates for the [MapboxTripProgressView] that include remaining time and distance to the destination.
     */
    private lateinit var tripProgressApi: MapboxTripProgressApi

    /**
     * Generates updates for the [routeLineView] with the geometries and properties of the routes that should be drawn on the map.
     */
    private lateinit var routeLineApi: MapboxRouteLineApi

    /**
     * Draws route lines on the map based on the data from the [routeLineApi]
     */
    private lateinit var routeLineView: MapboxRouteLineView

    /**
     * Generates updates for the [routeArrowView] with the geometries and properties of maneuver arrows that should be drawn on the map.
     */
    private val routeArrowApi: MapboxRouteArrowApi = MapboxRouteArrowApi()

    /**
     * Draws maneuver arrows on the map based on the data [routeArrowApi].
     */
    private lateinit var routeArrowView: MapboxRouteArrowView

    /**
     * Stores and updates the state of whether the voice instructions should be played as they come or muted.
     */
    private var isVoiceInstructionsMuted = false
        set(value) {
            field = value
            if (value) {
                binding.soundButton.muteAndExtend(BUTTON_ANIMATION_DURATION)
                voiceInstructionsPlayer.volume(SpeechVolume(0f))
            } else {
                binding.soundButton.unmuteAndExtend(BUTTON_ANIMATION_DURATION)
                voiceInstructionsPlayer.volume(SpeechVolume(1f))
            }
        }

    /**
     * Extracts message that should be communicated to the driver about the upcoming maneuver.
     * When possible, downloads a synthesized audio file that can be played back to the driver.
     */
    private lateinit var speechApi: MapboxSpeechApi

    /**
     * Plays the synthesized audio files with upcoming maneuver instructions
     * or uses an on-device Text-To-Speech engine to communicate the message to the driver.
     */
    private lateinit var voiceInstructionsPlayer: MapboxVoiceInstructionsPlayer

    /**
     * Observes when a new voice instruction should be played.
     */
    private val voiceInstructionsObserver = VoiceInstructionsObserver { voiceInstructions ->
        speechApi.generate(voiceInstructions, speechCallback)
    }

    /**
     * Based on whether the synthesized audio file is available, the callback plays the file
     * or uses the fall back which is played back using the on-device Text-To-Speech engine.
     */
    private val speechCallback =
        MapboxNavigationConsumer<Expected<SpeechError, SpeechValue>> { expected ->
            expected.fold(
                { error ->
                    // play the instruction via fallback text-to-speech engine
                    voiceInstructionsPlayer.play(
                        error.fallback,
                        voiceInstructionsPlayerCallback
                    )
                },
                { value ->
                    // play the sound file from the external generator
                    voiceInstructionsPlayer.play(
                        value.announcement,
                        voiceInstructionsPlayerCallback
                    )
                }
            )
        }

    /**
     * When a synthesized audio file was downloaded, this callback cleans up the disk after it was played.
     */
    private val voiceInstructionsPlayerCallback =
        MapboxNavigationConsumer<SpeechAnnouncement> { value ->
            // remove already consumed file to free-up space
            speechApi.clean(value)
        }

    /**
     * [NavigationLocationProvider] is a utility class that helps to provide location updates generated by the Navigation SDK
     * to the Maps SDK in order to update the user location indicator on the map.
     */
    private val navigationLocationProvider = NavigationLocationProvider()

    /**
     * Gets notified with location updates.
     *
     * Exposes raw updates coming directly from the location services
     * and the updates enhanced by the Navigation SDK (cleaned up and matched to the road).
     */
    private val locationObserver = object : LocationObserver {
        var firstLocationUpdateReceived = false

        override fun onNewRawLocation(rawLocation: Location) {
            // not handled
        }

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation
            // update location puck's position on the map
            navigationLocationProvider.changePosition(
                location = enhancedLocation,
                keyPoints = locationMatcherResult.keyPoints,
            )

            // update camera position to account for new location
            viewportDataSource.onLocationChanged(enhancedLocation)
            viewportDataSource.evaluate()

            // if this is the first location update the activity has received,
            // it's best to immediately move the camera to the current user location
            if (!firstLocationUpdateReceived) {
                firstLocationUpdateReceived = true
                val location = LatLng(enhancedLocation.latitude, enhancedLocation.longitude)
                updateCamera(location)
            }
        }
    }

    /**
     * Gets notified with progress along the currently active route.
     */
    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        // update the camera position to account for the progressed fragment of the route
        viewportDataSource.onRouteProgressChanged(routeProgress)
        viewportDataSource.evaluate()

       // draw the upcoming maneuver arrow on the map
        val style = mapboxMap.getStyle()
        if (style != null) {
            val maneuverArrowResult = routeArrowApi.addUpcomingManeuverArrow(routeProgress)
            routeArrowView.renderManeuverUpdate(style, maneuverArrowResult)
        }

        // update top banner with maneuver instructions
        val maneuvers = maneuverApi.getManeuvers(routeProgress)
        maneuvers.fold(
            { error ->
                Toast.makeText(
                    context,
                    error.errorMessage,
                    Toast.LENGTH_SHORT
                ).show()
            },
            {
                binding.maneuverView.visibility = View.VISIBLE
                binding.maneuverView.renderManeuvers(maneuvers)
            }
        )

        // update bottom trip progress summary
        binding.tripProgressView.render(
            tripProgressApi.getTripProgress(routeProgress)
        )

        //update flutter events
        if (!isNavigationCanceled) {
            try {

                distanceRemaining = routeProgress.distanceRemaining.toDouble()
                durationRemaining = routeProgress.durationRemaining

                val progressEvent = MapBoxRouteProgressEvent(routeProgress)
                PluginUtilities.sendEvent(progressEvent)

            } catch (e: Exception) {

            }
        }

    }

    /**
     * Gets notified whenever the tracked routes change.
     *
     * A change can mean:
     * - routes get changed with [MapboxNavigation.setRoutes]
     * - routes annotations get refreshed (for example, congestion annotation that indicate the live traffic along the route)
     * - driver got off route and a reroute was executed
     */
    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            routeLineApi.setNavigationRoutes(
                routeUpdateResult.navigationRoutes
            ) { value ->
                mapboxMap.getStyle()?.apply {
                    routeLineView.renderRouteDrawData(this, value)
                }
            }
            // update the camera position to account for the new route
            viewportDataSource.onRouteChanged(routeUpdateResult.navigationRoutes.first())
            viewportDataSource.evaluate()

            //send a reroute event
            PluginUtilities.sendEvent(MapBoxEvents.REROUTE_ALONG, routeUpdateResult.reason)
        } else {
            // remove the route line and route arrow from the map
            val style = mapboxMap.getStyle()
            if (style != null) {
                routeLineApi.clearRouteLine { value ->
                    routeLineView.renderClearRouteLineValue(
                        style,
                        value
                    )
                }
                routeArrowView.render(style, routeArrowApi.clearArrows())
            }

            // remove the route reference from camera position evaluations
            viewportDataSource.clearRouteData()
            viewportDataSource.evaluate()
        }
    }

    /**
     * The SDK triggers [NavigationRouteAlternativesObserver] when available alternatives change.
     */
    private val alternativesObserver = object : NavigationRouteAlternativesObserver {
        override fun onRouteAlternatives(
            routeProgress: RouteProgress,
            alternatives: List<NavigationRoute>,
            routerOrigin: RouterOrigin
        ) {
            // Set the suggested alternatives
            val updatedRoutes = mutableListOf<NavigationRoute>()
            updatedRoutes.add(routeProgress.navigationRoute) // only primary route should persist
            updatedRoutes.addAll(alternatives) // all old alternatives should be replaced by the new ones
            mapboxNavigation.setNavigationRoutes(updatedRoutes)
        }

        override fun onRouteAlternativesError(error: RouteAlternativesError) {
            // no impl
        }
    }

    /**
     * Click on any point of the alternative route on the map to make it primary.
     */
    private val mapClickListener = OnMapClickListener { point ->
        routeLineApi.findClosestRoute(
            point,
            mapboxMap,
            routeClickPadding
        ) {
            val routeFound = it.value?.navigationRoute
            // if we clicked on some route that is not primary,
            // we make this route primary and all the others - alternative
            if (routeFound != null && routeFound != routeLineApi.getPrimaryNavigationRoute()) {
                val reOrderedRoutes = routeLineApi.getNavigationRoutes()
                    .filter { navigationRoute -> navigationRoute != routeFound }
                    .toMutableList()
                    .also { list ->
                        list.add(0, routeFound)
                    }
                FlutterMapboxPlugin.currentRoute = routeFound
                durationRemaining = FlutterMapboxPlugin.currentRoute!!.directionsRoute.duration()
                distanceRemaining = FlutterMapboxPlugin.currentRoute!!.directionsRoute.distance()

                mapboxNavigation.setNavigationRoutes(reOrderedRoutes)
                PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILT)
            }
        }
        false
    }

    private val addOnMapIdleListener = OnMapIdleListener {
        if (mapMoved) {
            val coords = mapboxMap.cameraState.center
            centerCoords = mutableListOf(coords.longitude(), coords.latitude())
            PluginUtilities.sendEvent(MapBoxEvents.MAP_POSITION_CHANGED)
            mapMoved = false
        }
    }

    private val onCameraChangeListener = OnCameraChangeListener {
        if (::pointAnnotationManager.isInitialized) {
            if (mapboxMap.cameraState.zoom < 7 && pointAnnotationManager.annotations.isNotEmpty()) {
                pointAnnotationManager.deleteAll()
            } else if (mapboxMap.cameraState.zoom > 7 && pointAnnotationManager.annotations.isEmpty()) {
                pointAnnotationManager.create(listOfPoints)
            }
        }
        mapMoved = true
        false
    }

    private val onPointAnnotationClickListener = OnPointAnnotationClickListener { annotation ->
        selectedAnnotation = annotation.textField!!
        PluginUtilities.sendEvent(MapBoxEvents.ANNOTATION_TAPPED)
        false
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        TODO("Not yet implemented")
    }

    override fun onActivityStarted(activity: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivityResumed(activity: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivityPaused(activity: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivityStopped(activity: Activity) {
        TODO("Not yet implemented")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        TODO("Not yet implemented")
    }

    override fun onActivityDestroyed(activity: Activity) {
        TODO("Not yet implemented")
    }
}

