## 1.0.1

Android navigation fixes, and an iOS dependency pin.

* **Route line under the labels.** The route line was anchored to a
  `road-label` layer that Mapbox's navigation styles don't have (theirs is
  `road-label-navigation`), so it was drawn over every label and the location
  puck. It's now placed below whichever road-label layer the loaded style has,
  in both the embedded view and full-screen navigation.
* **Route clears after navigation.** The embedded view and full-screen
  navigation each called `MapboxNavigationApp.setup()`, which destroys and
  re-creates the navigation instance when already set up — leaving the other
  holding a dead one, so `clearRoute()` did nothing. Setup now happens once and
  the instance is shared. `clearRoute()` also clears the drawn line and arrows
  directly and forgets the last route.
* **Arrival and cancellation from full-screen navigation.** Full-screen
  navigation now sends `on_arrival` at the final destination, and closing it
  reports `navigation_finished` only after arrival — closing early is
  `navigation_cancelled` (it used to always report finished).
  `finishNavigation()` now also closes full-screen navigation.
* **Events keep flowing with several map views.** Native events went to a
  single sink owned by the last view to listen, and a view closing cleared it,
  silencing every other view. Sinks are now a stack; a disposed view removes
  only its own. Disposed views also unregister their navigation observers.
* Full-screen navigation no longer stops the shared trip session on close,
  which froze the embedded map's location puck.
* **iOS:** `mapbox-navigation-ios` pinned to 3.24.x — from 3.31 its test-only
  dependencies fail to resolve together in an app's Swift package workspace.

## 1.0.0

**Breaking — moves both platforms to Mapbox Navigation SDK v3.**

* Android: Mapbox `navigationcore` 3.23.0 (from Navigation SDK 2.19); `compileSdk 36`,
  Android Gradle Plugin 8.x, Kotlin 2.1, Java 17. Adds `FOREGROUND_SERVICE` /
  `FOREGROUND_SERVICE_LOCATION` to the plugin manifest.
* iOS: `mapbox-navigation-ios` 3.24+ via Swift Package Manager; minimum iOS 14.
  CocoaPods-only builds are no longer supported.
* Requires Dart 3 / Flutter 3.
* New `MapBoxNavigationViewController.selectRoute(index)` to promote an
  alternative route.
* `route_built` now carries each route's `coordinates` (`[[lng, lat], ...]`,
  primary first) alongside `distance` and `duration`, on both platforms —
  including after `selectRoute` or tapping an alternative on the map.
* Android full-screen navigation: shows the 2D location puck (with bearing), and
  keeps the maneuver banner, sound button and trip-progress card clear of the
  status / navigation bars on edge-to-edge (Android 15+) devices; the camera is
  padded to match.
* Example app: Mapbox tokens replaced with placeholders — supply your own.

## 0.9.6

* fix for ios crashes


## 0.9.5

* android poi amend text color based on appearance 


## 0.9.4

* ios annotiation id work
* ios poi amend text color based on appearance 

## 0.9.3

* android annotiation id work

## 0.9.2

* roll-back mapbox version

## 0.9.1

* add id to waypoints object 

## 0.9.0

* remove android deprecated methods


## 0.8.9

* amend overview padding values
* change weight measurment type to metricTons


## 0.8.8

* add back zoomLevel var


## 0.8.7

* add back zoomLevel var


## 0.8.6

* add back zoomLevel var


## 0.8.5

* add back zoomLevel var


## 0.8.4

* add back zoomLevel var


## 0.8.3

* add back zoomLevel var


## 0.8.2

* try to resolve android issue


## 0.8.1

* try to resolve android issue


## 0.8.0

* fix iOS double conversion error


## 0.7.9

* return zoom level to flutter work


## 0.7.8

* pois - amend poi MAP_POSITION_CHANGED to under 12 zoom


## 0.7.7

* pois - amend poi hide level ios


## 0.7.6

* pois - amend poi hid level


## 0.7.5

* pois - minor change to append annotations ios


## 0.7.4

* pois - hide on soom 


## 0.7.3

* pois - allow amend of icon size


## 0.7.2

* android - fix for plugin error

## 0.7.1

* ios - remove pois work


## 0.7.0

* ios - fix for get centercoords


## 0.6.9

* ios - further poi annotation work

       
## 0.6.8

* ios - poi annotation work to match android

## 0.6.7

* android - amend poi removal and waypoints asset to isnotempty

## 0.6.6

* android - initialise poiManager on start-up

## 0.6.5

* android - group POIs together and removal work

## 0.6.4

* android - on add POI if doesn't already exist


## 0.6.3

* android - add POI with base64 image method
* android - remove add poi from MapBoxOptions


## 0.6.2

* android - work to pass back current view ceter coordinates


## 0.6.1

* android - fix for null check bug #8 


## 0.6.0

* ios - only add excludes empty check as was erroring


## 0.5.9

* android - add null check for styles before updating vars

## 0.5.8

* add in speed limit indicator for android


## 0.5.7

* add in map style vars as was failing without
* add update route event back into route observer func

## 0.5.6

* update missing refs

## 0.5.5

* update style to write to FlutterMapboxPlugin global var
* change FullScreenNav to use Navigation routes over depricated routes
* fix for line draw not updating on FullScreenNav


## 0.5.4

* accept avoids into buildRoute func
* fix iOS excludes as was ommitting point from URLQuery parameter


## 0.5.3

* update to Mapbox Navigation in Android v 2.11.0 
* update to Mapbox Navigation in iOS v 2.12

## 0.5.1

* update to android v33 complie 
* hide compass and scale elements


## 0.5.0

* add in mute option
* fix for routing vars


## 0.4.9

* fix mapbox navigation version


## 0.4.8

* navigation view changes for android

## 0.4.7

* fix for navigation end loop cycle bug

## 0.4.6

* android hide annotations on zoon out work
* android keep screen alive on navigation 
* android add events into full screen nav

## 0.4.5

* remove MapboxNavigationProvider on detory 

## 0.4.4

* exclude work change of type for iOS

## 0.4.3

* exclude work on trip planning for iOS and Android

## 0.4.2

* fix anroid route builder to duration 

## 0.4.1

* android select annotation to return string

## 0.4.0

* remove android POI zoom as was lagging map

## 0.3.9

* android POI click work
* android POI hide on zoom out

## 0.3.8

* Remove red pin POI

## 0.3.7
* POI work for android
* update android navigation view bar appearance
* fix to allow route selection to change distance and duration vars in android

## 0.3.6

* fix to allow route selection to change distance and duration vars in iOS

## 0.3.5

* return string instead of annotation class

## 0.3.4

* iOS POI annotation click work

## 0.3.3

* remove POI work for now

## 0.3.2

* update of mapbox android maps 
* beginning of annotations work for android

## 0.3.1

* fix for PluginUtilities error

## 0.3.0

* amend mapbox versions

## 0.2.9

* amend mapbox versions

## 0.2.8

* fix for iOS strongSelf error

## 0.2.7

* add location grant work

## 0.2.6

* pass language argument into full screen navigation building

## 0.2.5

* add point annotation tap events to ios
* fix voice language argument for android

## 0.2.4

* fix pois null reference exception

## 0.2.3

* add pois to mapbox options

## 0.2.2

* add annotation work for poi's on iOS

## 0.2.1

* navigation carmera work on android to set to zoom 14
* allow for arugments to be passed into mapview init


## 0.2.0

* finish navigation intent on route clear
* allow of alternative route selection on map click



## 0.1.8

* add android reCenter navigation camera method call 


## 0.1.7

* allow vehicle dimentions to be passed in via build route aruguments 

## 0.1.6

* android - add embedded navigation view work 


## 0.1.5

* android - start navigation session on init


## 0.1.4

* add duration and disatance android route builder response
* add vehicle height / weight / width arguments to android route builder

## 0.1.3

* fix for android embeded mapbox view

## 0.1.0

* finalise android dev work

## 0.0.9

* change context create override to nullable to fix error

## 0.0.8

* attempt to fix oncreate error

## 0.0.7

* fix for Android missing deps

## 0.0.6

* re-work for Android dev

## 0.0.5

* initial Android dev work

## 0.0.4

* Big fixes for width switch over


## 0.0.3

* Swap length with width


## 0.0.2

* Add max height / length / weight arguments to query 


## 0.0.1

* initial release.