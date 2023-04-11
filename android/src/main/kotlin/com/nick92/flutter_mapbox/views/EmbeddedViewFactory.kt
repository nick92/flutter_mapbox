package com.nick92.flutter_mapbox.views

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import androidx.lifecycle.Lifecycle
import com.nick92.flutter_mapbox.utilities.PluginUtilities
import com.nick92.flutter_mapbox.databinding.MapActivityBinding

import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class EmbeddedViewFactory(private val messenger: BinaryMessenger,
                          private val activity: Activity,
                          private val lifecycle: Lifecycle
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val inflater = LayoutInflater.from(context)
        val binding = MapActivityBinding.inflate(inflater)
        val accessToken = PluginUtilities.getResourceFromContext(context,"mapbox_access_token")
        return EmbeddedView(context, activity, lifecycle, binding, messenger, viewId, args, accessToken)
    }
}