package com.nick92.flutter_mapbox.views

import android.app.Activity
import android.view.View
import android.content.Context

import com.nick92.flutter_mapbox.TurnByTurn
import com.nick92.flutter_mapbox.databinding.MapActivityBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView


class EmbeddedNavigationMapView(context: Context, activity: Activity, binding: MapActivityBinding, binaryMessenger: BinaryMessenger, vId: Int, args: Any?, accessToken: String)
    : PlatformView, TurnByTurn(context, activity, binding, accessToken) {
    private val viewId: Int = vId
    private val messenger: BinaryMessenger = binaryMessenger

    override fun initFlutterChannelHandlers() {
        methodChannel = MethodChannel(messenger, "flutter_mapbox/${viewId}")
        eventChannel = EventChannel(messenger, "flutter_mapbox/${viewId}/events")
        super.initFlutterChannelHandlers()
    }

    override fun onFlutterViewAttached(flutterView: View) {
        super.onFlutterViewAttached(flutterView)
        initFlutterChannelHandlers()
        initNavigation()
    }

    override fun getView(): View {
        val view: View = binding.root;
        return view;
    }

    override fun dispose() {
        unregisterObservers();
        onDestroy();
    }

}