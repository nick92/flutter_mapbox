package com.nick92.flutter_mapbox.views

import android.app.Activity
import android.view.View
import android.content.Context
import android.util.Log
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapboxMapOptions
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.loader.MapboxMapsInitializer
import com.nick92.flutter_mapbox.EmbeddedNavigationView
import com.nick92.flutter_mapbox.databinding.MapActivityBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import timber.log.Timber


class EmbeddedView(context: Context, activity: Activity, binding: MapActivityBinding, binaryMessenger: BinaryMessenger, vId: Int, args: Any?, accessToken: String)
    : PlatformView, EmbeddedNavigationView(context, activity, binding, accessToken) {
    private val viewId: Int = vId
    private val messenger: BinaryMessenger = binaryMessenger
    private val options: MapInitOptions = MapInitOptions(context, textureView = true)
    private var mapView = MapView(context, options)

    init {
        initFlutterChannelHandlers()
        initNavigation(mapView)
    }

    override fun initFlutterChannelHandlers() {
        methodChannel = MethodChannel(messenger, "flutter_mapbox/${viewId}")
        eventChannel = EventChannel(messenger, "flutter_mapbox/${viewId}/events")
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

}