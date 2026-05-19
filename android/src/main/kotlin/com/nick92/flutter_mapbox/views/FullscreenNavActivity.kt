package com.nick92.flutter_mapbox.views

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.api.directions.v5.models.Bearing
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Expected
import com.mapbox.common.Cancelable
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.TimeFormat
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.replay.MapboxReplayer
import com.mapbox.navigation.core.replay.route.ReplayProgressObserver
import com.mapbox.navigation.core.replay.route.ReplayRouteMapper
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
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
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
import com.nick92.flutter_mapbox.FlutterMapboxPlugin
import com.nick92.flutter_mapbox.databinding.NavigationActivityBinding
import com.nick92.flutter_mapbox.models.MapBoxEvents
import com.nick92.flutter_mapbox.models.MapBoxRouteProgressEvent
import com.nick92.flutter_mapbox.utilities.PluginUtilities
import com.nick92.flutter_mapbox.utilities.PluginUtilities.Companion.sendEvent
import java.util.*

class FullscreenNavActivity : AppCompatActivity() {

    private var receiver: BroadcastReceiver? = null
    private var points: MutableList<Point> = mutableListOf()
    private lateinit var binding: NavigationActivityBinding

    private lateinit var mapboxNavigation: MapboxNavigation
    private lateinit var navigationCamera: NavigationCamera
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView
    private val routeArrowApi = MapboxRouteArrowApi()
    private lateinit var routeArrowView: MapboxRouteArrowView
    private lateinit var maneuverApi: MapboxManeuverApi
    private lateinit var tripProgressApi: MapboxTripProgressApi
    private lateinit var speechApi: MapboxSpeechApi
    private lateinit var voiceInstructionsPlayer: MapboxVoiceInstructionsPlayer
    private val navigationLocationProvider = NavigationLocationProvider()

    private val mapboxReplayer = MapboxReplayer()
    private val replayProgressObserver = ReplayProgressObserver(mapboxReplayer)

    private var mapIdleCancelable: Cancelable? = null
    private var isVoiceInstructionsMuted = true

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
        binding = NavigationActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val p = intent.getSerializableExtra("waypoints") as? MutableList<Point>
        if (p != null) points = p

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { finish() }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, IntentFilter(FullscreenNavigationLauncher.KEY_STOP_NAVIGATION), RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, IntentFilter(FullscreenNavigationLauncher.KEY_STOP_NAVIGATION))
        }

        setupNavigationComponents()
        setupUI()
    }

    private fun setupNavigationComponents() {
        MapboxNavigationApp.setup(NavigationOptions.Builder(this).build())
        mapboxNavigation = MapboxNavigationApp.current()!!

        val mapboxMap = binding.mapView.mapboxMap

        viewportDataSource = MapboxNavigationViewportDataSource(mapboxMap)
        navigationCamera = NavigationCamera(mapboxMap, binding.mapView.camera, viewportDataSource)
        binding.mapView.camera.addCameraAnimationsLifecycleListener(
            NavigationBasicGesturesHandler(navigationCamera)
        )
        navigationCamera.registerNavigationCameraStateChangeObserver { state ->
            // keep recenter visible when not following
        }

        routeLineApi = MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build())
        routeLineView = MapboxRouteLineView(
            MapboxRouteLineViewOptions.Builder(this)
                .routeLineBelowLayerId("road-label")
                .build()
        )
        routeArrowView = MapboxRouteArrowView(RouteArrowOptions.Builder(this).build())

        val distanceFormatterOptions = mapboxNavigation.navigationOptions.distanceFormatterOptions
        maneuverApi = MapboxManeuverApi(MapboxDistanceFormatter(distanceFormatterOptions))
        tripProgressApi = MapboxTripProgressApi(
            TripProgressUpdateFormatter.Builder(this)
                .distanceRemainingFormatter(DistanceRemainingFormatter(distanceFormatterOptions))
                .timeRemainingFormatter(TimeRemainingFormatter(this))
                .percentRouteTraveledFormatter(PercentDistanceTraveledFormatter())
                .estimatedTimeToArrivalFormatter(
                    EstimatedTimeToArrivalFormatter(this, TimeFormat.NONE_SPECIFIED)
                )
                .build()
        )

        val language = FlutterMapboxPlugin.navigationLanguage
        speechApi = MapboxSpeechApi(this, language)
        voiceInstructionsPlayer = MapboxVoiceInstructionsPlayer(this, language)

        val styleUrl = FlutterMapboxPlugin.mapStyleUrlDay ?: Style.MAPBOX_STREETS
        mapboxMap.loadStyle(styleUrl)
    }

    private fun setupUI() {
        binding.stop.setOnClickListener { finish() }

        binding.soundButton.setOnClickListener {
            isVoiceInstructionsMuted = !isVoiceInstructionsMuted
            if (isVoiceInstructionsMuted) {
                binding.soundButton.mute()
                voiceInstructionsPlayer.volume(SpeechVolume(0f))
            } else {
                binding.soundButton.unmute()
                voiceInstructionsPlayer.volume(SpeechVolume(1f))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mapboxNavigation.registerRoutesObserver(routesObserver)
        mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.registerLocationObserver(locationObserver)
        mapboxNavigation.registerVoiceInstructionsObserver(voiceInstructionsObserver)
        mapboxNavigation.registerRouteProgressObserver(replayProgressObserver)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            }
        }
        mapboxNavigation.startTripSession()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (FlutterMapboxPlugin.currentRoute != null) {
            setRouteAndStartNavigation(FlutterMapboxPlugin.currentRoute!!)
        } else if (points.size >= 2) {
            findRoute(points[0], points[1])
        }
    }

    override fun onStop() {
        super.onStop()
        mapboxNavigation.unregisterRoutesObserver(routesObserver)
        mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.unregisterLocationObserver(locationObserver)
        mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
        mapboxNavigation.unregisterRouteProgressObserver(replayProgressObserver)
        mapboxNavigation.stopTripSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapIdleCancelable?.cancel()
        mapboxReplayer.finish()
        maneuverApi.cancel()
        routeLineApi.cancel()
        routeLineView.cancel()
        speechApi.cancel()
        voiceInstructionsPlayer.shutdown()
        try { receiver?.let { unregisterReceiver(it) } } catch (e: Exception) {}
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sendEvent(MapBoxEvents.NAVIGATION_FINISHED)
    }

    private fun findRoute(origin: Point, destination: Point) {
        mapboxNavigation.requestRoutes(
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
                .layersList(listOf(mapboxNavigation.getZLevel() ?: 0, null))
                .build(),
            object : NavigationRouterCallback {
                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: String) {
                    setRoutesAndStartNavigation(routes)
                }
                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    sendEvent(MapBoxEvents.ROUTE_BUILD_FAILED)
                }
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: String) {
                    sendEvent(MapBoxEvents.ROUTE_BUILD_CANCELLED)
                }
            }
        )
    }

    private fun setRoutesAndStartNavigation(routes: List<NavigationRoute>) {
        mapboxNavigation.setNavigationRoutes(routes)
        navigationCamera.requestNavigationCameraToFollowing()
        sendEvent(MapBoxEvents.NAVIGATION_RUNNING)
    }

    private fun setRouteAndStartNavigation(route: NavigationRoute) {
        mapboxNavigation.setNavigationRoutes(listOf(route))
        navigationCamera.requestNavigationCameraToFollowing()
        sendEvent(MapBoxEvents.NAVIGATION_RUNNING)
    }

    private val locationObserver = object : LocationObserver {
        override fun onNewRawLocation(rawLocation: com.mapbox.common.location.Location) {}
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            navigationLocationProvider.changePosition(
                location = locationMatcherResult.enhancedLocation,
                keyPoints = locationMatcherResult.keyPoints,
            )
            viewportDataSource.onLocationChanged(locationMatcherResult.enhancedLocation)
            viewportDataSource.evaluate()
        }
    }

    private val voiceInstructionsObserver = VoiceInstructionsObserver { voiceInstructions ->
        speechApi.generate(voiceInstructions, speechCallback)
    }

    private val speechCallback =
        MapboxNavigationConsumer<Expected<SpeechError, SpeechValue>> { expected ->
            expected.fold(
                { error -> voiceInstructionsPlayer.play(error.fallback, voiceInstructionsPlayerCallback) },
                { value -> voiceInstructionsPlayer.play(value.announcement, voiceInstructionsPlayerCallback) }
            )
        }

    private val voiceInstructionsPlayerCallback =
        MapboxNavigationConsumer<SpeechAnnouncement> { value ->
            speechApi.clean(value)
        }

    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        try {
            viewportDataSource.onRouteProgressChanged(routeProgress)
            viewportDataSource.evaluate()

            val style = binding.mapView.mapboxMap.style
            if (style != null) {
                val maneuverArrowResult = routeArrowApi.addUpcomingManeuverArrow(routeProgress)
                routeArrowView.renderManeuverUpdate(style, maneuverArrowResult)
            }

            val maneuvers = maneuverApi.getManeuvers(routeProgress)
            maneuvers.fold(
                { /* ignore error */ },
                {
                    binding.maneuverView.visibility = View.VISIBLE
                    binding.maneuverView.renderManeuvers(maneuvers)
                }
            )

            binding.tripProgressView.render(tripProgressApi.getTripProgress(routeProgress))

            FlutterMapboxPlugin.distanceRemaining = routeProgress.distanceRemaining
            FlutterMapboxPlugin.durationRemaining = routeProgress.durationRemaining
            val progressEvent = MapBoxRouteProgressEvent(routeProgress)
            PluginUtilities.sendEvent(progressEvent)
        } catch (_: Exception) {}
    }

    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            routeLineApi.setNavigationRoutes(routeUpdateResult.navigationRoutes) { value ->
                binding.mapView.mapboxMap.style?.apply {
                    routeLineView.renderRouteDrawData(this, value)
                }
            }
            viewportDataSource.onRouteChanged(routeUpdateResult.navigationRoutes.first())
            viewportDataSource.evaluate()
            sendEvent(MapBoxEvents.REROUTE_ALONG)
        } else {
            val style = binding.mapView.mapboxMap.style
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
}
