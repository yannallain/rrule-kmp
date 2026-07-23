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
            checksum: "6b528139d34a843a1cf1a7947e45c973316c0336e89c49dd371d494abc5e9814"
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
