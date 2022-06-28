package com.nick92.flutter_mapbox.views

import android.app.Activity
import android.content.Intent
import com.mapbox.geojson.Point
import java.io.Serializable

open class FullscreenNavigationLauncher {
    companion object {
        val KEY_STOP_NAVIGATION = "com.my.mapbox.broadcast.STOP_NAVIGATION"

        @JvmStatic
        fun startNavigation(activity: Activity, wayPoints: List<Point?>?) {
            val navigationIntent = Intent(activity, FullscreenNavActivity::class.java)
            navigationIntent.putExtra("waypoints", wayPoints as Serializable?)
            activity.startActivity(navigationIntent)
        }

        @JvmStatic
        fun stopNavigation(activity: Activity) {
            val stopIntent = Intent()
            stopIntent.action = KEY_STOP_NAVIGATION
            activity.sendBroadcast(stopIntent)
        }
    }
}