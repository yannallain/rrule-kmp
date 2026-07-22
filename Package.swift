// swift-tools-version: 5.8

import PackageDescription

let package = Package(
    name: "RRuleKit",
    platforms: [
        .iOS(.v13),
    ],
    products: [
        .library(
            name: "RRuleKit",
            targets: ["RRuleKit"]
        ),
    ],
    targets: [
        .binaryTarget(
            name: "RRuleKmpCore",
            url: "https://github.com/yannallain/rrule-kmp/releases/download/0.1.0/RRuleKmpCore-0.1.0.xcframework.zip",
            checksum: "4c3e033ce33fb37ceed917d70d4b10cc7517320a7b874ec4c42058bc374af8f8"
        ),
        .target(
            name: "RRuleKit",
            dependencies: ["RRuleKmpCore"],
            path: "apple/swift-package/Sources/RRuleKit"
        ),
        .testTarget(
            name: "RRuleKitTests",
            dependencies: ["RRuleKit"],
            path: "apple/swift-package/Tests/RRuleKitTests"
        ),
    ]
)
