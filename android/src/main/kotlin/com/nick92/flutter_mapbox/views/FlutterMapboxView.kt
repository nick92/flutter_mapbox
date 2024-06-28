package com.nick92.flutter_mapbox.views

import android.app.Activity
import android.view.View
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewTreeLifecycleOwner
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.nick92.flutter_mapbox.EmbeddedNavigationView
import com.nick92.flutter_mapbox.databinding.MapActivityBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView


class FlutterMapboxView(context: Context, activity: Activity, private val lifecycle: Lifecycle, binding: MapActivityBinding, binaryMessenger: BinaryMessenger, vId: Int, args: Any?, accessToken: String)
    : PlatformView, DefaultLifecycleObserver, EmbeddedNavigationView(context, activity, binding, accessToken) {
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
        * Setting lifecycle owner to the map view
        * To fix the issue
        * ---> IllegalStateException: Please ensure that the hosting activity/fragment
        *      is a valid LifecycleOwner #259 - Reported By:- @amias-samir
        */
        mapView.let {
            lifecycle.let { l ->
                ViewTreeLifecycleOwner.set(it) {
                    l
                }
            }
        }

        super.initFlutterChannelHandlers()
    }

    override fun getView(): View {
        val view: View = mapView;
        return view;
    }

    override fun dispose() {
        unregisterObservers();
        onDestroy();
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