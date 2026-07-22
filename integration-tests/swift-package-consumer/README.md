# Published Swift package consumer

This standalone Swift package depends on the public Git repository at an exact
release version. The post-release distribution workflow uses it to prove the
Git tag, package identity, `RRuleKit` product, released XCFramework, and generic
iOS simulator/device link paths from clean Swift Package Manager caches.

The manifest defaults to `0.1.0`. A release workflow supplies the exact public
repository and version through `RRULE_KMP_REPOSITORY_URL` and
`RRULE_KMP_VERSION`; these inputs affect this integration fixture only.
