package com.nick92.flutter_mapbox

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import androidx.lifecycle.Lifecycle
import com.nick92.flutter_mapbox.views.EmbeddedViewFactory

import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.navigation.base.route.NavigationRoute
import com.nick92.flutter_mapbox.views.FullscreenNavigationLauncher

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.embedding.engine.plugins.lifecycle.FlutterLifecycleAdapter
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.platform.PlatformViewRegistry
import java.util.*

/** FlutterMapboxPlugin */
class FlutterMapboxPlugin: FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler, ActivityAware {

  private lateinit var channel : MethodChannel
  private lateinit var progressEventChannel: EventChannel
  private var currentActivity: Activity? = null
  private lateinit var currentContext: Context
  private var lifecycle: Lifecycle? = null

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    val messenger = flutterPluginBinding.binaryMessenger
    channel = MethodChannel(messenger, "flutter_mapbox")
    channel.setMethodCallHandler(this)

    progressEventChannel = EventChannel(messenger, "flutter_mapbox/events")
    progressEventChannel.setStreamHandler(this)

    platformViewRegistry = flutterPluginBinding.platformViewRegistry
    binaryMessenger = messenger;
  }

  companion object {

    var eventSink:EventChannel.EventSink? = null

    var PERMISSION_REQUEST_CODE: Int = 367

    lateinit var routes : List<DirectionsRoute>
    var currentRoute: NavigationRoute? = null
    val wayPoints: MutableList<Point> = mutableListOf()

    var showAlternateRoutes: Boolean = true
    val allowsClickToSetDestination: Boolean = false
    var allowsUTurnsAtWayPoints: Boolean = false
    var navigationMode =  DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
    var simulateRoute = false
    var mapStyleUrlDay: String? = null
    var mapStyleUrlNight: String? = null
    var navigationLanguage = "en"
    var navigationVoiceUnits = DirectionsCriteria.IMPERIAL
    var zoom = 14.0
    var bearing = 0.0
    var tilt = 0.0
    var distanceRemaining: Float? = null
    var durationRemaining: Double? = null
    var platformViewRegistry: PlatformViewRegistry? = null
    var binaryMessenger: BinaryMessenger? = null

    var view_name = "FlutterMapboxView"
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when(call.method)
    {
      "getPlatformVersion" -> {
        result.success("Android ${Build.VERSION.RELEASE}")
      }
      "getDistanceRemaining" -> {
        result.success(distanceRemaining);
      }
      "getDurationRemaining" -> {
        result.success(durationRemaining);
      }
      "startNavigation" -> {
        checkPermissionAndBeginNavigation(call, result)
      }
      "finishNavigation" -> {
        currentActivity?.let { FullscreenNavigationLauncher.stopNavigation(it) }
      }
      "enableOfflineRouting" -> {
        downloadRegionForOfflineRouting(call, result)
      }
      else -> result.notImplemented()
    }
  }
  
  private fun downloadRegionForOfflineRouting(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result){
    result.error("TODO", "Not Implemented in Android","will implement soon")
  }
  
  private fun checkPermissionAndBeginNavigation(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result)
  {
    val arguments = call.arguments as? Map<String, Any>

    val navMode = arguments?.get("mode") as? String
    if(navMode != null)
    {
      if(navMode == "walking")
        navigationMode = DirectionsCriteria.PROFILE_WALKING;
      else if(navMode == "cycling")
        navigationMode = DirectionsCriteria.PROFILE_CYCLING;
      else if(navMode == "driving")
        navigationMode = DirectionsCriteria.PROFILE_DRIVING;
    }

    val alternateRoutes = arguments?.get("alternatives") as? Boolean
    if(alternateRoutes != null){
      showAlternateRoutes = alternateRoutes
    }

    val simulated = arguments?.get("simulateRoute") as? Boolean
    if (simulated != null) {
      simulateRoute = simulated
    }

    val allowsUTurns = arguments?.get("allowsUTurnsAtWayPoints") as? Boolean
    if(allowsUTurns != null){
      allowsUTurnsAtWayPoints = allowsUTurns
    }

    val language = arguments?.get("language") as? String
    if(language != null)
      navigationLanguage = language

    val units = arguments?.get("units") as? String

    if(units != null)
    {
      if(units == "imperial")
        navigationVoiceUnits = DirectionsCriteria.IMPERIAL
      else if(units == "metric")
        navigationVoiceUnits = DirectionsCriteria.METRIC
    }

    mapStyleUrlDay = arguments?.get("mapStyleUrlDay") as? String
    mapStyleUrlNight = arguments?.get("mapStyleUrlNight") as? String

    wayPoints.clear()

    val points = arguments?.get("wayPoints") as HashMap<Int, Any>
    for (item in points)
    {
      val point = item.value as HashMap<*, *>
      val latitude = point["Latitude"] as Double
      val longitude = point["Longitude"] as Double
      wayPoints.add(Point.fromLngLat(longitude, latitude))
    }

    checkPermissionAndBeginNavigation(wayPoints)

  }

  private fun checkPermissionAndBeginNavigation(wayPoints: List<Point>)
  {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val haspermission = currentActivity?.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
      if(haspermission != PackageManager.PERMISSION_GRANTED) {
        //_activity.onRequestPermissionsResult((a,b,c) => onRequestPermissionsResult)
        currentActivity?.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
        beginNavigation(wayPoints)
      }
      else
        beginNavigation(wayPoints)
    }
    else
      beginNavigation(wayPoints)
  }

  private fun beginNavigation(wayPoints: List<Point>) {
    currentActivity?.let { FullscreenNavigationLauncher.startNavigation(it, wayPoints) }
  }


  override fun onListen(args: Any?, events: EventChannel.EventSink?) {
    eventSink = events;
  }

  override fun onCancel(args: Any?) {
    eventSink = null;
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {

    currentActivity = null
    channel.setMethodCallHandler(null)
    progressEventChannel.setStreamHandler(null)

  }

  override fun onDetachedFromActivity() {
    currentActivity!!.finish()
    currentActivity = null
    lifecycle = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    currentActivity = binding.activity
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    lifecycle = FlutterLifecycleAdapter.getActivityLifecycle(binding)
    currentActivity = binding.activity
    currentContext = binding.activity.applicationContext

    if (platformViewRegistry != null && binaryMessenger != null && currentActivity != null && lifecycle != null) {
      platformViewRegistry?.registerViewFactory(
        view_name,
        EmbeddedViewFactory(binaryMessenger!!, currentActivity!!, lifecycle!!)
      )
    }
  }

  override fun onDetachedFromActivityForConfigChanges() {
    //To change body of created functions use File | Settings | File Templates.
  }

  interface LifecycleProvider {
    fun getLifecycle(): Lifecycle?
  }

}