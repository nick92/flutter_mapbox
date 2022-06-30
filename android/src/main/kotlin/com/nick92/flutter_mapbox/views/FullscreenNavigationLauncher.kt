package com.nick92.flutter_mapbox.views

import android.app.Activity
import android.content.Intent
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.geojson.Point
import com.mapbox.navigation.base.route.NavigationRoute
import java.io.Serializable

open class FullscreenNavigationLauncher {
    companion object {
        val KEY_STOP_NAVIGATION = "com.my.mapbox.broadcast.STOP_NAVIGATION"

        fun startNavigation(activity: Activity, wayPoints: List<Point?>?) {
            val navigationIntent = Intent(activity, FullscreenNavActivity::class.java)
            navigationIntent.putExtra("waypoints", wayPoints as Serializable?)
            activity.startActivity(navigationIntent)
        }

        fun stopNavigation(activity: Activity) {
            val stopIntent = Intent()
            stopIntent.action = KEY_STOP_NAVIGATION
            activity.sendBroadcast(stopIntent)
        }
    }
}