// swift-tools-version: 5.10
import PackageDescription

let package = Package(
    name: "BydWirelessMirror",
    platforms: [
        .iOS(.v16),
        .macOS(.v13)
    ],
    products: [
        .library(
            name: "BydMirrorCore",
            targets: ["BydMirrorCore"]
        )
    ],
    targets: [
        .target(
            name: "BydMirrorCore",
            path: "Sources/Core"
        ),
        .testTarget(
            name: "BydMirrorCoreTests",
            dependencies: ["BydMirrorCore"],
            path: "Tests/BydMirrorCoreTests"
        )
    ]
)
