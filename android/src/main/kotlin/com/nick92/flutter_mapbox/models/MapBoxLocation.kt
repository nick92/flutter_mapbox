package com.nick92.flutter_mapbox.models

class MapBoxLocation(val name: String = "", val latitude: Double?, val longitude: Double?) {

    override fun toString(): String {
        return "{" +
                "  \"latitude\": $latitude," +
                "  \"longitude\": $longitude" +
                "}"
    }

}