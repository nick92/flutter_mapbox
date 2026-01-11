package com.nick92.flutter_mapbox.views

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
// IMPORTANT: Use this extension import
import androidx.lifecycle.setViewTreeLifecycleOwner 
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.nick92.flutter_mapbox.EmbeddedNavigationView
import com.nick92.flutter_mapbox.databinding.MapActivityBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView

class FlutterMapboxView(
    context: Context, 
    activity: Activity, 
    private val lifecycle: Lifecycle, 
    binding: MapActivityBinding, 
    binaryMessenger: BinaryMessenger, 
    vId: Int, 
    args: Any?, 
    accessToken: String
) : PlatformView, DefaultLifecycleObserver, EmbeddedNavigationView(context, activity, binding, accessToken) {
    
    private val viewId: Int = vId
    private val messenger: BinaryMessenger = binaryMessenger
    private val options: MapInitOptions = MapInitOptions(context)
    private val mapView: MapView = MapView(context, options)

    init {
        val arguments = args as Map<*, *>
        lifecycle.addObserver(this)
        initFlutterChannelHandlers()
        initNavigation(mapView, arguments)
    }

    override fun initFlutterChannelHandlers() {
        methodChannel = MethodChannel(messenger, "flutter_mapbox/${viewId}")
        eventChannel = EventChannel(messenger, "flutter_mapbox/${viewId}/events")
        
        /*
        * Setting lifecycle owner to the map view using the modern extension method.
        * This fixes IllegalStateException: Please ensure that the hosting activity/fragment is a valid LifecycleOwner
        */
        mapView.setViewTreeLifecycleOwner(activity as LifecycleOwner)

        super.initFlutterChannelHandlers()
    }

    override fun getView(): View {
        return mapView
    }

    override fun dispose() {
        lifecycle.removeObserver(this)
        unregisterObservers()
        onDestroy()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        mapView.onStart()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        mapView.onStop()
    }
}