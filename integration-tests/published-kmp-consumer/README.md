# Published KMP consumer

This standalone fixture resolves `rrule-kmp` as an external dependency. It has
no project substitution or source dependency, so it protects the Gradle module
metadata, JVM runtime artifact, and all three Apple KLIB variants that public
Kotlin Multiplatform consumers receive.

Publish a JitPack-shaped development version and verify every variant:

```shell
./gradlew \
  -PPUBLICATION_GROUP=com.github.yannallain \
  -PVERSION_NAME=0.0.0 \
  publishAllPublicationsToLocalBuildRepository

./gradlew -p integration-tests/published-kmp-consumer \
  -PrruleVersion=0.0.0 \
  --refresh-dependencies \
  jvmTest \
  compileKotlinIosX64 \
  compileKotlinIosArm64 \
  compileKotlinIosSimulatorArm64
```

To verify a public JitPack tag, set `rruleRepositoryUrl` and the release
version:

```shell
./gradlew -p integration-tests/published-kmp-consumer \
  -PrruleRepositoryUrl=https://jitpack.io \
  -PrruleVersion=0.1.0 \
  --refresh-dependencies \
  jvmTest \
  compileKotlinIosX64 \
  compileKotlinIosArm64 \
  compileKotlinIosSimulatorArm64
```
