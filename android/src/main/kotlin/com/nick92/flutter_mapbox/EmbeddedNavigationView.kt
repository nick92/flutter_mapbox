package com.nick92.flutter_mapbox

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Expected
import com.mapbox.common.Cancelable
import com.mapbox.common.location.Location
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.TimeFormat
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.replay.MapboxReplayer
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import com.mapbox.navigation.tripdata.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.tripdata.progress.api.MapboxTripProgressApi
import com.mapbox.navigation.tripdata.progress.model.DistanceRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.EstimatedTimeToArrivalFormatter
import com.mapbox.navigation.tripdata.progress.model.PercentDistanceTraveledFormatter
import com.mapbox.navigation.tripdata.progress.model.TimeRemainingFormatter
import com.mapbox.navigation.tripdata.progress.model.TripProgressUpdateFormatter
import com.mapbox.navigation.ui.base.util.MapboxNavigationConsumer
import com.mapbox.navigation.ui.components.maneuver.view.MapboxManeuverView
import com.mapbox.navigation.ui.components.tripprogress.view.MapboxTripProgressView
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.camera.transition.NavigationCameraTransitionOptions
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineViewOptions
import com.mapbox.navigation.voice.api.MapboxSpeechApi
import com.mapbox.navigation.voice.api.MapboxVoiceInstructionsPlayer
import com.mapbox.navigation.voice.model.SpeechAnnouncement
import com.mapbox.navigation.voice.model.SpeechError
import com.mapbox.navigation.voice.model.SpeechValue
import com.mapbox.navigation.voice.model.SpeechVolume
import com.nick92.flutter_mapbox.FlutterMapboxPlugin.Companion.PERMISSION_REQUEST_CODE
import com.nick92.flutter_mapbox.databinding.MapActivityBinding
import com.nick92.flutter_mapbox.models.MapBoxEvents
import com.nick92.flutter_mapbox.models.MapBoxPointAnnotaions
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

        // initialize Mapbox Navigation — access token is read from mapbox_access_token string resource
        MapboxNavigationApp.setup(NavigationOptions.Builder(context).build())
        MapboxNavigationApp.attach(this.activity as LifecycleOwner)

        mapboxNavigation = MapboxNavigationApp.current()!!

        // initialize Navigation Camera
        viewportDataSource = MapboxNavigationViewportDataSource(mapboxMap)
        navigationCamera = NavigationCamera(
            mapboxMap,
            mapView.camera,
            viewportDataSource
        )
        mapView.camera.addCameraAnimationsLifecycleListener(
            NavigationBasicGesturesHandler(navigationCamera)
        )
        navigationCamera.registerNavigationCameraStateChangeObserver { navigationCameraState ->
            when (navigationCameraState) {
                NavigationCameraState.TRANSITION_TO_FOLLOWING,
                NavigationCameraState.FOLLOWING -> binding.recenter.visibility = View.INVISIBLE
                NavigationCameraState.TRANSITION_TO_OVERVIEW,
                NavigationCameraState.OVERVIEW,
                NavigationCameraState.IDLE -> binding.recenter.visibility = View.INVISIBLE
            }
        }

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

        val distanceFormatterOptions = mapboxNavigation.navigationOptions.distanceFormatterOptions

        maneuverApi = MapboxManeuverApi(
            MapboxDistanceFormatter(distanceFormatterOptions)
        )

        tripProgressApi = MapboxTripProgressApi(
            TripProgressUpdateFormatter.Builder(activity)
                .distanceRemainingFormatter(DistanceRemainingFormatter(distanceFormatterOptions))
                .timeRemainingFormatter(TimeRemainingFormatter(activity))
                .percentRouteTraveledFormatter(PercentDistanceTraveledFormatter())
                .estimatedTimeToArrivalFormatter(
                    EstimatedTimeToArrivalFormatter(activity, TimeFormat.NONE_SPECIFIED)
                )
                .build()
        )

        speechApi = MapboxSpeechApi(activity, navigationLanguage)
        voiceInstructionsPlayer = MapboxVoiceInstructionsPlayer(activity, navigationLanguage)

        // initialize route line — Maps SDK v11 uses routeLineBelowLayerId (no "with" prefix)
        val mapboxRouteLineApiOptions = MapboxRouteLineApiOptions.Builder().build()
        val mapboxRouteLineViewOptions = MapboxRouteLineViewOptions.Builder(activity)
            .routeLineBelowLayerId("road-label")
            .build()
        routeLineApi = MapboxRouteLineApi(mapboxRouteLineApiOptions)
        routeLineView = MapboxRouteLineView(mapboxRouteLineViewOptions)

        val routeArrowOptions = RouteArrowOptions.Builder(activity).build()
        routeArrowView = MapboxRouteArrowView(routeArrowOptions)

        var styleUrl = FlutterMapboxPlugin.mapStyleUrlDay
        if (styleUrl == null) styleUrl = Style.MAPBOX_STREETS

        mapboxMap.loadStyle(styleUrl) {
            if (longPressDestinationEnabled) {
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

        binding.stop.setOnClickListener {
            clearRouteAndStopNavigation()
        }

        binding.recenter.setOnClickListener {
            navigationCamera.requestNavigationCameraToFollowing()
            binding.routeOverview.showTextAndExtend(BUTTON_ANIMATION_DURATION)
        }
        binding.routeOverview.setOnClickListener {
            navigationCamera.requestNavigationCameraToOverview()
            binding.recenter.showTextAndExtend(BUTTON_ANIMATION_DURATION)
        }
        binding.soundButton.setOnClickListener {
            isVoiceInstructionsMuted = !isVoiceInstructionsMuted
        }

        val annotationApi = mapView.annotations
        pointAnnotationManager = annotationApi.createPointAnnotationManager()
        pointAnnotationManager.addClickListener(onPointAnnotationClickListener)

        isVoiceInstructionsMuted = true

        mapView.gestures.addOnMapClickListener(mapClickListener)

        // Maps SDK v11 event subscriptions (replaces addOnMapIdleListener / addOnCameraChangeListener)
        mapIdleCancelable = mapboxMap.subscribeMapIdle {
            if (mapMoved) {
                val coords = mapboxMap.cameraState.center
                centerCoords = mutableListOf(coords.longitude(), coords.latitude())
                zoomLevel = mapboxMap.cameraState.zoom
                PluginUtilities.sendEvent(MapBoxEvents.MAP_POSITION_CHANGED)
                mapMoved = false
            }
        }

        cameraChangeCancelable = mapboxMap.subscribeCameraChanged {
            if (::pointAnnotationManager.isInitialized) {
                if (mapboxMap.cameraState.zoom < 8 && pointAnnotationManager.annotations.isNotEmpty()) {
                    pointAnnotationManager.deleteAll()
                } else if (mapboxMap.cameraState.zoom > 8 && pointAnnotationManager.annotations.isEmpty()) {
                    val points = mutableListOf<PointAnnotationOptions>()
                    for (point in listOfPoints) {
                        points.add(point.pointAnnotationOptions!!)
                    }
                    pointAnnotationManager.create(points)
                }
            }
            mapMoved = true
        }

        registerObservers()
        checkPermissionAndStartTrip(false)
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
            "getZoomLevel" -> {
                result.success(zoomLevel)
            }
            "reCenter" -> {
                navigationCamera.requestNavigationCameraToOverview(
                    stateTransitionOptions = NavigationCameraTransitionOptions.Builder()
                        .maxDuration(0)
                        .build()
                )
            }
            "setPOIs" -> {
                addPOIs(methodCall, result)
            }
            "removePOIs" -> {
                removePOIs(methodCall, result)
            }
            else -> result.notImplemented()
        }
    }

    private fun buildRoute(methodCall: MethodCall, result: MethodChannel.Result) {
        isNavigationCanceled = false
        isNavigationInProgress = false

        val arguments = methodCall.arguments as? Map<*, *>
        if (arguments != null)
            setOptions(arguments)
        val mapReady = true
        if (mapReady) {
            wayPoints.clear()
            val points = arguments?.get("wayPoints") as HashMap<*, *>

            for (item in points) {
                val point = item.value as HashMap<*, *>
                val latitude = point["Latitude"] as Double
                val longitude = point["Longitude"] as Double
                wayPoints.add(Point.fromLngLat(longitude, latitude))
            }

            val height = arguments["maxHeight"] as? String
            val weight = arguments["maxWeight"] as? String
            val width = arguments["maxWidth"] as? String

            if (height != null) maxHeight = height.toDouble()
            if (weight != null) maxWeight = weight.toDouble()
            if (width != null) maxWidth = width.toDouble()

            val avoids = arguments["avoid"] as? List<String>
            if (avoids != null && avoids.isNotEmpty())
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
                .build(),
            object : NavigationRouterCallback {
                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: String
                ) {
                    if (routes.isEmpty()) {
                        PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILD_NO_ROUTES_FOUND)
                        return
                    }

                    FlutterMapboxPlugin.currentRoute = routes[0]
                    durationRemaining = FlutterMapboxPlugin.currentRoute!!.directionsRoute.duration()
                    distanceRemaining = FlutterMapboxPlugin.currentRoute!!.directionsRoute.distance()

                    PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILT)
                    mapboxNavigation.setNavigationRoutes(routes)
                    navigationCamera.requestNavigationCameraToOverview()
                    isBuildingRoute = false
                    if (isNavigationInProgress) {
                        startNavigation()
                    }
                }

                override fun onFailure(
                    reasons: List<RouterFailure>,
                    routeOptions: RouteOptions
                ) {
                    var message = "an error occurred while building the route. Errors: "
                    for (reason in reasons) {
                        message += reason.message
                    }
                    PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILD_FAILED, message)
                    isBuildingRoute = false
                }

                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                    PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILD_CANCELLED)
                }
            })
    }

    private fun beginFullScreenNavigation() {
        activity.let { FullscreenNavigationLauncher.startNavigation(it, wayPoints) }
    }

    private fun addPOIs(methodCall: MethodCall, result: MethodChannel.Result) {
        val arguments = methodCall.arguments as? Map<*, *>

        if (arguments != null) {
            val groupName = arguments["group"] as? String
            val base64Image = arguments["icon"] as? String
            var iconSize = arguments["iconSize"] as? Double
            val poiPoints = arguments["poi"] as? HashMap<*, *>
            var poiImage: ByteArray? = null

            base64Image?.let {
                poiImage = android.util.Base64.decode(it, 0)
            }

            if (iconSize == null) {
                iconSize = 0.2
            }

            val image = BitmapFactory.decodeByteArray(poiImage, 0, poiImage!!.size)
            addPOIAnnotations(groupName!!, image, iconSize, poiPoints!!)
        }
        result.success(true)
    }

    private fun removePOIs(methodCall: MethodCall, result: MethodChannel.Result) {
        val arguments = methodCall.arguments as? Map<*, *>

        if (arguments != null) {
            val groupName = arguments["group"] as? String
            removePOIsByGroupName(groupName!!)
        }
        result.success(true)
    }

    private fun addPOIAnnotations(groupName: String, poiImage: Bitmap, iconSize: Double, pois: HashMap<*, *>) {
        for (item in pois) {
            val poi = item.value as HashMap<*, *>
            val id = poi["Id"] as String
            val name = poi["Name"] as String
            val latitude = poi["Latitude"] as Double
            val longitude = poi["Longitude"] as Double

            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(longitude, latitude))
                .withIconImage(poiImage)

            pointAnnotationOptions.iconSize = iconSize
            pointAnnotationOptions.textOffset = listOf(0.0, 3.0)
            pointAnnotationOptions.textField = name
            pointAnnotationOptions.textSize = 12.0

            if (FlutterMapboxPlugin.mapStyleUrlDay?.contains("night") == true) {
                pointAnnotationOptions.textColor = "white"
                pointAnnotationOptions.textHaloColor = "black"
            } else {
                pointAnnotationOptions.textColor = "black"
                pointAnnotationOptions.textHaloColor = "white"
            }
            pointAnnotationOptions.textHaloWidth = 1.0

            val pointAnnotaion = MapBoxPointAnnotaions()
            pointAnnotaion.id = id
            pointAnnotaion.groupName = groupName
            pointAnnotaion.pointAnnotationOptions = pointAnnotationOptions

            if (!containsName(id)) {
                listOfPoints.add(pointAnnotaion)
            }
        }

        val points: MutableList<PointAnnotationOptions> = mutableListOf()
        for (point in listOfPoints) {
            points.add(point.pointAnnotationOptions!!)
        }
        pointAnnotationManager.create(points)
    }

    private fun removePOIsByGroupName(groupName: String) {
        val pointAnnotaions = pointAnnotationManager.annotations
        val listOfPois = listOfPoints.filter { it.groupName == groupName }

        val points: MutableList<PointAnnotation> = mutableListOf()
        for (point in listOfPois) {
            val annotation = pointAnnotaions.filter {
                it.textField == point.pointAnnotationOptions!!.textField
            }
            points.addAll(annotation)
        }

        pointAnnotationManager.delete(points)
        listOfPoints.removeAll { it.groupName == groupName }
    }

    private fun containsName(nameToCheck: String): Boolean {
        for (point in listOfPoints) {
            if (point.id == nameToCheck) return true
        }
        return false
    }

    private fun moveCameraToOriginOfRoute() {
        FlutterMapboxPlugin.currentRoute?.let {
            val originCoordinate = it.directionsRoute.routeOptions()?.coordinatesList()?.getOrNull(0)
            // camera update can be implemented here if needed
        }
    }

    private fun clearRoute(methodCall: MethodCall, result: MethodChannel.Result) {
        mapboxNavigation.setNavigationRoutes(listOf())
        mapboxReplayer.stop()
    }

    private fun clearRouteAndStopNavigation() {
        mapboxNavigation.setNavigationRoutes(listOf())
        mapboxReplayer.stop()
        PluginUtilities.sendEvent(MapBoxEvents.NAVIGATION_CANCELLED)
    }

    private fun startNavigation(methodCall: MethodCall, result: MethodChannel.Result) {
        val arguments = methodCall.arguments as? Map<*, *>
        if (arguments != null)
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

    private fun checkPermissionAndStartTrip(withForegroundService: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasPermission =
                activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            if (hasPermission != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
        mapboxNavigation.startTripSession(withForegroundService)
    }

    private fun startNavigation() {
        isNavigationCanceled = false

        if (FlutterMapboxPlugin.currentRoute != null) {
            if (simulateRoute) {
                mapboxReplayer.play()
            }

            checkPermissionAndStartTrip(true)
            binding.soundButton.visibility = View.VISIBLE
            binding.routeOverview.visibility = View.VISIBLE
            binding.tripProgressView.visibility = View.VISIBLE

            navigationCamera.requestNavigationCameraToFollowing()
            isNavigationInProgress = true
            PluginUtilities.sendEvent(MapBoxEvents.NAVIGATION_RUNNING)
        }
    }

    private fun finishNavigation(isOffRouted: Boolean = false) {
        mapboxNavigation.setNavigationRoutes(listOf())

        zoom = zoom
        bearing = 0.0
        tilt = 0.0
        isNavigationCanceled = true

        if (!isOffRouted) {
            isNavigationInProgress = false
            moveCameraToOriginOfRoute()
        }

        if (simulateRoute)
            mapboxReplayer.stop()

        mapboxNavigation.stopTripSession()
    }

    private fun updateCamera(methodCall: MethodCall, result: MethodChannel.Result) {
        val arguments = methodCall.arguments as? Map<*, *>
        if (arguments != null) {
            val latitude = arguments["latitude"] as Double
            val longitude = arguments["longitude"] as Double
            // camera update can be implemented here
        }
    }

    private fun setOptions(arguments: Map<*, *>) {
        val navMode = arguments["mode"] as? String
        if (navMode != null) {
            when (navMode) {
                "walking" -> navigationMode = DirectionsCriteria.PROFILE_WALKING
                "cycling" -> navigationMode = DirectionsCriteria.PROFILE_CYCLING
                "driving" -> navigationMode = DirectionsCriteria.PROFILE_DRIVING
            }
        }

        val simulated = arguments["simulateRoute"] as? Boolean
        if (simulated != null) simulateRoute = simulated

        val language = arguments["language"] as? String
        if (language != null) navigationLanguage = language

        val units = arguments["units"] as? String
        if (units != null) {
            when (units) {
                "imperial" -> navigationVoiceUnits = DirectionsCriteria.IMPERIAL
                "metric" -> navigationVoiceUnits = DirectionsCriteria.METRIC
            }
        }

        val styleDay = arguments["mapStyleUrlDay"] as? String
        val styleNight = arguments["mapStyleUrlNight"] as? String

        if (styleDay != null) FlutterMapboxPlugin.mapStyleUrlDay = styleDay
        if (styleNight != null) FlutterMapboxPlugin.mapStyleUrlNight = styleNight

        initialLatitude = arguments["initialLatitude"] as? Double
        initialLongitude = arguments["initialLongitude"] as? Double

        val height = arguments["maxHeight"] as? String
        val weight = arguments["maxWeight"] as? String
        val width = arguments["maxWidth"] as? String

        if (height != null) maxHeight = height.toDouble()
        if (weight != null) maxWeight = weight.toDouble()
        if (width != null) maxWidth = width.toDouble()

        val zm = arguments["zoom"] as? Double
        if (zm != null) zoom = zm

        val br = arguments["bearing"] as? Double
        if (br != null) bearing = br

        val tt = arguments["tilt"] as? Double
        if (tt != null) tilt = tt

        val optim = arguments["isOptimized"] as? Boolean
        if (optim != null) isOptimized = optim

        val anim = arguments["animateBuildRoute"] as? Boolean
        if (anim != null) animateBuildRoute = anim

        val altRoute = arguments["alternatives"] as? Boolean
        if (altRoute != null) alternatives = altRoute

        val voiceEnabled = arguments["voiceInstructionsEnabled"] as? Boolean
        if (voiceEnabled != null) voiceInstructionsEnabled = voiceEnabled

        val bannerEnabled = arguments["bannerInstructionsEnabled"] as? Boolean
        if (bannerEnabled != null) bannerInstructionsEnabled = bannerEnabled

        val longPress = arguments["longPressDestinationEnabled"] as? Boolean
        if (longPress != null) longPressDestinationEnabled = longPress

        val avoids = arguments["avoid"] as? List<String>
        if (avoids != null && avoids.isNotEmpty()) excludeList = avoids
    }

    open fun registerObservers() {
        mapboxNavigation.registerRoutesObserver(routesObserver)
        mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.registerLocationObserver(locationObserver)
        mapboxNavigation.registerVoiceInstructionsObserver(voiceInstructionsObserver)
        mapboxNavigation.registerRouteProgressObserver(replayProgressObserver)
        // continuous alternatives replaces NavigationRouteAlternativesObserver (removed in v3)
        mapboxNavigation.setContinuousAlternativesEnabled(true)
    }

    open fun unregisterObservers() {
        mapboxNavigation.unregisterRoutesObserver(routesObserver)
        mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.unregisterLocationObserver(locationObserver)
        mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
        mapboxNavigation.unregisterRouteProgressObserver(replayProgressObserver)
    }

    fun onDestroy() {
        mapIdleCancelable?.cancel()
        cameraChangeCancelable?.cancel()
        MapboxNavigationProvider.destroy()
        mapboxReplayer.finish()
        maneuverApi.cancel()
        routeLineApi.cancel()
        routeLineView.cancel()
        speechApi.cancel()
        voiceInstructionsPlayer.shutdown()
    }

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

    var initialLatitude: Double? = null
    var initialLongitude: Double? = null

    val wayPoints: MutableList<Point> = mutableListOf()
    val pois: MutableList<PointAnnotation> = mutableListOf()
    var navigationMode = DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
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

    private var isDisposed = false
    private var isRefreshing = false
    private var isBuildingRoute = false
    private var isNavigationInProgress = false
    private var isNavigationCanceled = false
    private var zoomLevel = 0.0

    val BUTTON_ANIMATION_DURATION = 1500L

    val routeClickPadding = 30 * Resources.getSystem().displayMetrics.density

    private val mapboxReplayer = MapboxReplayer()
    private val replayProgressObserver = ReplayProgressObserver(mapboxReplayer)

    open val binding: MapActivityBinding = bind

    private lateinit var mapView: MapView
    private lateinit var mapboxMap: MapboxMap

    // Maps SDK v11 event subscription cancelables
    private var mapIdleCancelable: Cancelable? = null
    private var cameraChangeCancelable: Cancelable? = null

    private lateinit var mapboxNavigation: MapboxNavigation
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource

    private val pixelDensity = Resources.getSystem().displayMetrics.density
    private val overviewPadding: EdgeInsets by lazy {
        EdgeInsets(
            160.0 * pixelDensity,
            40.0 * pixelDensity,
            160.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }
    private val landscapeOverviewPadding: EdgeInsets by lazy {
        EdgeInsets(
            140.0 * pixelDensity,
            380.0 * pixelDensity,
            130.0 * pixelDensity,
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

    private var listOfPoints: MutableList<MapBoxPointAnnotaions> = mutableListOf()
    private lateinit var selectedAnnotation: String
    private lateinit var pointAnnotationManager: PointAnnotationManager

    private lateinit var maneuverApi: MapboxManeuverApi
    private lateinit var tripProgressApi: MapboxTripProgressApi
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private val routeArrowApi: MapboxRouteArrowApi = MapboxRouteArrowApi()
    private lateinit var routeArrowView: MapboxRouteArrowView

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

    private lateinit var speechApi: MapboxSpeechApi
    private lateinit var voiceInstructionsPlayer: MapboxVoiceInstructionsPlayer

    private val voiceInstructionsObserver = VoiceInstructionsObserver { voiceInstructions ->
        speechApi.generate(voiceInstructions, speechCallback)
    }

    private val speechCallback =
        MapboxNavigationConsumer<Expected<SpeechError, SpeechValue>> { expected ->
            expected.fold(
                { error ->
                    voiceInstructionsPlayer.play(error.fallback, voiceInstructionsPlayerCallback)
                },
                { value ->
                    voiceInstructionsPlayer.play(value.announcement, voiceInstructionsPlayerCallback)
                }
            )
        }

    private val voiceInstructionsPlayerCallback =
        MapboxNavigationConsumer<SpeechAnnouncement> { value ->
            speechApi.clean(value)
        }

    private val navigationLocationProvider = NavigationLocationProvider()

    private val locationObserver = object : LocationObserver {
        var firstLocationUpdateReceived = false

        override fun onNewRawLocation(rawLocation: Location) {
            // not handled
        }

        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val enhancedLocation = locationMatcherResult.enhancedLocation
            navigationLocationProvider.changePosition(
                location = enhancedLocation,
                keyPoints = locationMatcherResult.keyPoints,
            )

            viewportDataSource.onLocationChanged(enhancedLocation)
            viewportDataSource.evaluate()

            if (!firstLocationUpdateReceived) {
                firstLocationUpdateReceived = true
            }
        }
    }

    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        viewportDataSource.onRouteProgressChanged(routeProgress)
        viewportDataSource.evaluate()

        val style = mapboxMap.style
        if (style != null) {
            val maneuverArrowResult = routeArrowApi.addUpcomingManeuverArrow(routeProgress)
            routeArrowView.renderManeuverUpdate(style, maneuverArrowResult)
        }

        val maneuvers = maneuverApi.getManeuvers(routeProgress)
        maneuvers.fold(
            { error ->
                Toast.makeText(context, error.errorMessage, Toast.LENGTH_SHORT).show()
            },
            {
                binding.maneuverView.visibility = View.VISIBLE
                binding.maneuverView.renderManeuvers(maneuvers)
            }
        )

        binding.tripProgressView.render(tripProgressApi.getTripProgress(routeProgress))

        if (!isNavigationCanceled) {
            try {
                distanceRemaining = routeProgress.distanceRemaining.toDouble()
                durationRemaining = routeProgress.durationRemaining
                val progressEvent = MapBoxRouteProgressEvent(routeProgress)
                PluginUtilities.sendEvent(progressEvent)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            routeLineApi.setNavigationRoutes(routeUpdateResult.navigationRoutes) { value ->
                mapboxMap.style?.apply {
                    routeLineView.renderRouteDrawData(this, value)
                }
            }
            viewportDataSource.onRouteChanged(routeUpdateResult.navigationRoutes.first())
            viewportDataSource.evaluate()
            PluginUtilities.sendEvent(MapBoxEvents.REROUTE_ALONG, routeUpdateResult.reason)
        } else {
            val style = mapboxMap.style
            if (style != null) {
                routeLineApi.clearRouteLine { value ->
                    routeLineView.renderClearRouteLineValue(style, value)
                }
                routeArrowView.render(style, routeArrowApi.clearArrows())
            }
            viewportDataSource.clearRouteData()
            viewportDataSource.evaluate()
        }
    }

    private val mapClickListener = OnMapClickListener { point ->
        routeLineApi.findClosestRoute(point, mapboxMap, routeClickPadding) {
            val routeFound = it.value?.navigationRoute
            if (routeFound != null && routeFound != routeLineApi.getPrimaryNavigationRoute()) {
                val reOrderedRoutes = routeLineApi.getNavigationRoutes()
                    .filter { navigationRoute -> navigationRoute != routeFound }
                    .toMutableList()
                    .also { list -> list.add(0, routeFound) }
                FlutterMapboxPlugin.currentRoute = routeFound
                durationRemaining = FlutterMapboxPlugin.currentRoute!!.directionsRoute.duration()
                distanceRemaining = FlutterMapboxPlugin.currentRoute!!.directionsRoute.distance()
                mapboxNavigation.setNavigationRoutes(reOrderedRoutes)
                PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILT)
            }
        }
        false
    }

    private val onPointAnnotationClickListener = OnPointAnnotationClickListener { annotation ->
        val point = listOfPoints.filter {
            it.pointAnnotationOptions!!.getPoint()!!.latitude() == annotation.point.latitude() &&
                    it.pointAnnotationOptions!!.getPoint()!!.longitude() == annotation.point.longitude()
        }

        selectedAnnotation = point.first().id
        PluginUtilities.sendEvent(MapBoxEvents.ANNOTATION_TAPPED)
        false
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
