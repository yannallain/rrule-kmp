// swift-tools-version: 5.8

import PackageDescription

// This local-package manifest is copied to build/swift-package by
// :apple:rrule-kit:prepareLocalSwiftPackage together with the generated XCFramework.
let package = Package(
    name: "RRuleKit",
    platforms: [
        .iOS(.v13),
    ],
    products: [
        .library(name: "RRuleKit", targets: ["RRuleKit"]),
    ],
    targets: [
        .binaryTarget(
            name: "RRuleKmpCore",
            path: "Artifacts/RRuleKmpCore.xcframework"
        ),
        .target(
            name: "RRuleKit",
            dependencies: ["RRuleKmpCore"]
        ),
        .testTarget(
            name: "RRuleKitTests",
            dependencies: ["RRuleKit"]
        ),
    ]
)
