// swift-tools-version: 5.9
// Flutter looks for Package.swift at ios/<plugin_name>/Package.swift
import PackageDescription

let package = Package(
    name: "flutter_mapbox",
    platforms: [.iOS("14.0")],
    products: [
        .library(name: "flutter-mapbox", targets: ["flutter_mapbox"])
    ],
    dependencies: [
        // Flutter injects this local package during build
        .package(name: "FlutterFramework", path: "../FlutterFramework"),
        // Pinned to 3.24.x: from 3.31 its test-only dependencies
        // (swift-snapshot-testing / swift-custom-dump) fail to resolve
        // together in an app's workspace. Widen deliberately, after checking.
        .package(
            url: "https://github.com/mapbox/mapbox-navigation-ios.git",
            .upToNextMinor(from: "3.24.0")
        )
    ],
    targets: [
        .target(
            name: "flutter_mapbox",
            dependencies: [
                .product(name: "FlutterFramework", package: "FlutterFramework"),
                .product(name: "MapboxNavigationCore", package: "mapbox-navigation-ios"),
                .product(name: "MapboxNavigationUIKit", package: "mapbox-navigation-ios")
            ],
            // Sources/flutter_mapbox is a symlink to ios/Classes
            // Exclude ObjC files — SPM targets must be single-language
            exclude: [
                "FlutterMapboxPlugin.m",
                "FlutterMapboxPlugin.h"
            ]
        )
    ]
)
