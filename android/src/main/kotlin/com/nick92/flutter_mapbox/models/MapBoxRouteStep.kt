package com.nick92.flutter_mapbox.models

import com.mapbox.api.directions.v5.models.LegStep
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions

class MapBoxRouteStep(val step: LegStep) {

    val instructions: String = "" //step.bannerInstructions()[0]?.primary().toString()
    val distance: Double = step.distance()
    val expectedTravelTime: Double = step.duration()

}

class MapBoxPointAnnotaions() {
    var groupName: String = ""
    var pointAnnotationOptions: PointAnnotationOptions? = null
}