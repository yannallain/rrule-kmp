// swift-tools-version: 5.8

import Foundation
import PackageDescription

let environment = ProcessInfo.processInfo.environment
let repositoryURL = environment["RRULE_KMP_REPOSITORY_URL"]
    ?? "https://github.com/yannallain/rrule-kmp.git"
let releaseVersionString = environment["RRULE_KMP_VERSION"] ?? "0.1.0"
let releaseVersionComponents = releaseVersionString
    .split(separator: ".")
    .compactMap { Int($0) }

precondition(
    releaseVersionComponents.count == 3,
    "RRULE_KMP_VERSION must use MAJOR.MINOR.PATCH",
)

let releaseVersion = Version(
    releaseVersionComponents[0],
    releaseVersionComponents[1],
    releaseVersionComponents[2]
)

let package = Package(
    name: "RRuleKitRemoteConsumer",
    platforms: [
        .iOS(.v13),
    ],
    products: [
        .library(
            name: "RRuleKitRemoteConsumer",
            targets: ["RRuleKitRemoteConsumer"]
        ),
    ],
    dependencies: [
        .package(url: repositoryURL, exact: releaseVersion),
    ],
    targets: [
        .target(
            name: "RRuleKitRemoteConsumer",
            dependencies: [
                .product(name: "RRuleKit", package: "rrule-kmp"),
            ]
        ),
    ]
)
