# Differential testing

`DifferentialHarness` in `commonTest` compares this library's selected occurrence list with a list
exported by any independent RFC 5545 implementation.

External implementations are intentionally not embedded or invoked by the Gradle build. This keeps
the shared test suite deterministic, avoids extra runtime dependencies, and makes every imported
oracle list reviewable. Add a `DifferentialCase`, supply the independent implementation's ordered
values to the diagnostic test, and inspect the structured per-index differences.

A difference is diagnostic, not automatically a failure. Check RFC 5545 first because another
implementation may deliberately accept permissive or non-conforming rule-part combinations.
