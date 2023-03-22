package com.nick92.flutter_mapbox.views

import android.app.Activity
import android.content.Context
import com.mapbox.mapboxsdk.Mapbox
import com.nick92.flutter_mapbox.utilities.PluginUtilities
import com.nick92.flutter_mapbox.databinding.MapActivityBinding

import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class EmbeddedViewFactory(private val messenger: BinaryMessenger, private val activity: Activity) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val binding = MapActivityBinding.inflate(this.activity.layoutInflater)
        val accessToken = PluginUtilities.getResourceFromContext(context,"mapbox_access_token")
        Mapbox.getInstance(context, accessToken)
        return EmbeddedView(context, activity, binding, messenger, viewId, args, accessToken)
    }
}