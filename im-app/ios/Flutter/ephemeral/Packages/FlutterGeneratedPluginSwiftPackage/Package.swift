// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.
//
// Generated file. Do not edit.
//

import PackageDescription

let package = Package(
    name: "FlutterGeneratedPluginSwiftPackage",
    platforms: [
        .iOS("13.0")
    ],
    products: [
        .library(name: "FlutterGeneratedPluginSwiftPackage", type: .static, targets: ["FlutterGeneratedPluginSwiftPackage"])
    ],
    dependencies: [
        .package(name: "audioplayers_darwin", path: "../.packages/audioplayers_darwin-6.5.0"),
        .package(name: "connectivity_plus", path: "../.packages/connectivity_plus-6.1.5"),
        .package(name: "file_picker", path: "../.packages/file_picker-8.3.7"),
        .package(name: "image_picker_ios", path: "../.packages/image_picker_ios-0.8.13+6"),
        .package(name: "package_info_plus", path: "../.packages/package_info_plus-8.3.1"),
        .package(name: "record_ios", path: "../.packages/record_ios-2.1.1"),
        .package(name: "sqflite_darwin", path: "../.packages/sqflite_darwin-2.4.3+1"),
        .package(name: "sqlite3_flutter_libs", path: "../.packages/sqlite3_flutter_libs-0.5.42"),
        .package(name: "url_launcher_ios", path: "../.packages/url_launcher_ios-6.4.1"),
        .package(name: "video_player_avfoundation", path: "../.packages/video_player_avfoundation-2.10.0"),
        .package(name: "FlutterFramework", path: "../.packages/FlutterFramework")
    ],
    targets: [
        .target(
            name: "FlutterGeneratedPluginSwiftPackage",
            dependencies: [
                .product(name: "audioplayers-darwin", package: "audioplayers_darwin"),
                .product(name: "connectivity-plus", package: "connectivity_plus"),
                .product(name: "file-picker", package: "file_picker"),
                .product(name: "image-picker-ios", package: "image_picker_ios"),
                .product(name: "package-info-plus", package: "package_info_plus"),
                .product(name: "record-ios", package: "record_ios"),
                .product(name: "sqflite-darwin", package: "sqflite_darwin"),
                .product(name: "sqlite3-flutter-libs", package: "sqlite3_flutter_libs"),
                .product(name: "url-launcher-ios", package: "url_launcher_ios"),
                .product(name: "video-player-avfoundation", package: "video_player_avfoundation"),
                .product(name: "FlutterFramework", package: "FlutterFramework")
            ]
        )
    ]
)
