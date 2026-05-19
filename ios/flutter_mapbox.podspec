#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint flutter_mapbox.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'flutter_mapbox'
  s.version          = '0.9.6'
  s.summary          = 'Add turn-by-turn navigation to your Flutter app using the Mapbox Navigation SDK.'
  s.description      = <<-DESC
Add Turn By Turn Navigation to Your Flutter Application Using MapBox. Never leave your app when you need to navigate your users to a location.
                       DESC
  s.homepage         = 'https://github.com/nick92/flutter_mapbox'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Nick Wilkins' => 'nickawilkins@hotmail.com' }
  s.source           = { :path => '.' }
  s.source_files = 'Classes/**/*'
  s.dependency 'Flutter'
  s.platform = :ios, '14.0'
  s.dependency 'MapboxCoreNavigation', '~> 2.12'
  s.dependency 'MapboxNavigation', '~> 2.12'

  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'
end
