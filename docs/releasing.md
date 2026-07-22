# Publishing a release

Releases use a candidate-first, protected workflow. The candidate job produces
the checksum that is committed to `Package.swift`; the release job verifies a
green `main` commit before it creates either a tag or a draft GitHub Release.
JitPack and Swift Package Manager are tested again after the draft is published.

Release versions are exactly `MAJOR.MINOR.PATCH`, for example `0.1.0`. Do not
use a `v` prefix, a prerelease suffix, or a moved tag.

## One-time repository setup

Before the first public release, the owner must:

1. Create the public `yannallain/rrule-kmp` GitHub repository and make it the
   canonical `origin`.
2. Enable GitHub Actions and allow the `GITHUB_TOKEN` permissions declared by
   the workflows. Candidate and build jobs remain read-only; only the protected
   publish job receives contents, identity-token, attestation, and artifact
   metadata permissions.
3. Create a protected GitHub environment named `release`. Require an owner
   review before its publish job can create a tag or draft release.
4. Protect `main` and require the Linux/Kotlin, Android API 21 and API 36, and
   Apple CI jobs before changes can merge.
5. Add a tag ruleset covering release tags that blocks tag updates and
   deletion. The token-only workflow creates the tag from its protected
   publish job, so do not enable **Restrict creations** for `GITHUB_TOKEN`:
   `github-actions[bot]` cannot be selected as a ruleset bypass actor. If tag
   creation must also be restricted, use a dedicated GitHub App or fine-grained
   token whose actor can be added to the bypass list. The workflow itself
   validates the exact version, commit, green CI run, and protected environment
   before creating the tag.
6. Add the repository to Codecov and enable Codecov's GitHub OIDC integration.
   CI uploads coverage without a repository token.
7. Enable
   [private vulnerability reporting](https://github.com/yannallain/rrule-kmp/settings/security_analysis)
   and verify that the reporting link in [`SECURITY.md`](../SECURITY.md) opens a
   private advisory form.
8. Enable GitHub immutable releases if the repository has access to them.
   Regardless of that setting, never replace a published asset or move a tag;
   corrections receive a new patch version.
9. Open the public repository on JitPack after the first release is published,
   request the version build, and inspect its log. JitPack needs no publication
   credential.
10. If the release will claim runtime-certified iOS 13 support, provide a real
    iOS 13 runtime or device gate. Otherwise preserve the documented caveat:
    hosted Apple jobs validate deployment metadata and newer simulator/device
    builds, but do not prove execution on iOS 13 itself.

The workflows use GitHub provenance attestations and require no code-signing
secret. If the distribution policy also requires Maven or Apple code signing,
configure and verify that separately before the first release.

## 1. Build the hosted release candidate

Choose the next version. In GitHub, open the
[Release candidate workflow](../.github/workflows/release-candidate.yml), choose
**Run workflow**, select `main`, and enter the exact numeric version.

The candidate workflow runs on the hosted release toolchain with Xcode 26.6. It
runs the Gradle, ABI, Kotlin/Native, Swift, simulator, and generic-device gates;
builds the XCFramework archive twice without caches; and verifies byte-for-byte
reproducibility. It also builds the Maven release archive twice. Download the
candidate artifact and retain its toolchain record, archive, and checksum as
release evidence.

The authoritative native outputs are:

```text
RRuleKmpCore-<version>.xcframework.zip
swift-checksum.txt
```

Do not derive the committed checksum from an archive produced by a different
local Xcode version. Local checks remain useful before dispatching the hosted
candidate:

```shell
xcrun simctl list devices available
IOS_SIMULATOR_UDID='<simulator-udid>' \
  ./apple/verify-native-ios-release-candidate.sh 0.1.0
```

## 2. Commit the candidate checksum

Update the root `Package.swift` binary target with:

- the exact candidate checksum;
- the versioned release URL below.

```text
https://github.com/yannallain/rrule-kmp/releases/download/<version>/RRuleKmpCore-<version>.xcframework.zip
```

Update versioned installation examples in the public documentation. Validate
the manifest and local checks:

```shell
swift package dump-package
./gradlew check checkKotlinAbi koverVerifyJvm
```

Commit the manifest and documentation, push `main`, and wait for every required
CI job to pass. Do not create or push the tag manually. Run the owner-provided
real iOS 13 runtime/device check now, or keep the documented caveat if that
environment is still unavailable.

## 3. Create the protected draft release

On the exact green commit, open **Actions → Release → Run workflow**, select
`main`, and enter the same version.

The release workflow first runs with read-only repository permissions. For a
new tag, it confirms that it is building the current `main` commit. A recovery
run may instead resume only an existing tag that already points to the exact
verified SHA. In both cases, CI must have succeeded for that SHA and
`Package.swift` must contain the generated URL and checksum. The build then
reproduces the XCFramework, Maven repository, legal archive, and checksums.

Only after that build succeeds can the `release` environment approve the small
publish job. That job downloads and verifies the staged artifacts, records
GitHub provenance attestations, creates the numeric tag at the verified commit,
and opens a **draft** GitHub Release.

The draft contains:

| Asset | Purpose |
| --- | --- |
| `RRuleKmpCore-<version>.xcframework.zip` | Swift Package Manager binary target |
| `rrule-kmp-<version>-maven-repository.zip` | All `rrule-kmp` Maven publications; transitives remain external |
| `rrule-kmp-android-<version>.aar` | Standalone Android artifact |
| `rrule-kmp-jvm-<version>.jar` | Standalone JVM artifact |
| `rrule-kmp-<version>-sources.jar` | Kotlin sources |
| `rrule-kmp-<version>-licenses.zip` | Project and dependency licences |
| `swift-checksum.txt` | SwiftPM checksum committed to the public manifest |
| `SHA256SUMS` | Checksums for every other uploaded release asset |
| `LICENSE`, `THIRD_PARTY_NOTICES` | Top-level legal material |
| `swift-package.json` | Parsed package manifest captured during the build |
| `toolchain-versions.txt` | Hosted build-tool versions used for the release |

Download the draft assets and verify the checksums and attestations:

```shell
shasum -a 256 -c SHA256SUMS
gh attestation verify RRuleKmpCore-0.1.0.xcframework.zip \
  --repo yannallain/rrule-kmp
```

Review the generated notes, legal files, toolchain record, and real iOS 13
evidence. Publish the draft only when all release evidence is acceptable.

## 4. Verify public distribution

Publishing the draft starts the
[Distribution smoke workflow](../.github/workflows/distribution-smoke.yml).
This check deliberately runs after publication because a draft Release asset
is not publicly downloadable and the workflow is triggered by
`release.published`.

The workflow waits for JitPack to expose:

```kotlin
implementation("com.github.yannallain:rrule-kmp:<version>")
```

It then uses clean dependency caches to run the standalone
[KMP consumer](../integration-tests/published-kmp-consumer/README.md) against the
public repository, execute its JVM test, compile all three Apple KLIB variants,
and build the full-R8 Android consumer. This proves public coordinates and
Gradle module metadata rather than a locally substituted project.

The Apple job independently downloads the published XCFramework, checks it
against the tagged `Package.swift`, and uses the standalone
[Swift package consumer](../integration-tests/swift-package-consumer/README.md)
to resolve the exact Git URL and version from a clean cache before building
generic simulator and device consumers. Normal native applications use:

```text
https://github.com/yannallain/rrule-kmp.git
```

The hosted checks still do not execute on a real iOS 13 runtime. Preserve the
manual iOS 13 result or the compatibility caveat with the release evidence.

If either public smoke job fails, do not move the tag or replace a release
asset. Diagnose the publication failure and issue a new patch release.
