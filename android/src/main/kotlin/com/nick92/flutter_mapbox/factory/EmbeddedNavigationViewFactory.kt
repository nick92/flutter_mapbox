package com.nick92.flutter_mapbox.factory

import android.app.Activity
import android.content.Context
import com.nick92.flutter_mapbox.R
import com.nick92.flutter_mapbox.databinding.MapActivityBinding
import com.nick92.flutter_mapbox.utilities.PluginUtilities
import com.nick92.flutter_mapbox.views.EmbeddedNavigationMapView

import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class EmbeddedNavigationViewFactory(private val messenger: BinaryMessenger, private val activity: Activity) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val binding = MapActivityBinding.inflate(this.activity.layoutInflater)
        val accessToken = PluginUtilities.getResourceFromContext( context,"mapbox_access_token")
        val view = EmbeddedNavigationMapView(context, activity, binding, messenger, viewId, args, accessToken)
        activity.setTheme(R.style.Theme_AppCompat_NoActionBar)
        return view
    }
}