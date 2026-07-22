# Binary dependency licences

The native `RRuleKmpCore` framework statically links the Kotlin/Native runtime,
the Kotlin standard library, and `kotlinx-datetime`.

- Kotlin and `kotlinx-datetime` are licensed under
  [Apache License 2.0](Apache-2.0.txt).
- The files under [`Kotlin-Native`](Kotlin-Native) are the notices that the
  Kotlin documentation requires distributors of Kotlin/Native binaries to
  make available. They are copied from the Kotlin 2.3.20 distribution used to
  build this release.

These files supplement the project's MIT License and `THIRD_PARTY_NOTICES`,
which are distributed with the release bundles and embedded in the JVM and
Android artifacts.
