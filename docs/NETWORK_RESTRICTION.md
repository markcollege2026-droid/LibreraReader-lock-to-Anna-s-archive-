# Restricted-network fork

## Policy and entry points

`NetworkPolicy.requireAllowed` parses an absolute HTTP(S) URL with `java.net.URI`,
checks its **host**, case-insensitively, for `annas-archive`, and throws `IOException`
when denied. User-info, missing/invalid hosts, unsupported schemes and invalid
ports are rejected. A keyword in a path, query or fragment does not grant access.

This deliberately implements the requested **substring** rule. For example,
`annas-archive.example.com` passes; the policy does not prove domain ownership,
protect against DNS rebinding, or pin website certificates/IP addresses.

| Entry point | Enforcement |
| --- | --- |
| OPDS/downloads/font downloads/LAN imports | Shared `OPDS.client`, constructed by `RestrictedHttpClient` |
| Glide remote images | Existing `OkHttpUrlLoader` uses the same client |
| Redirects | Automatic redirects disabled; the application interceptor validates every redirect before a new connection/DNS lookup |
| Proxies | Disabled; arbitrary proxies would themselves connect to disallowed hosts |
| Dialog/license/HTML WebViews and XML WebView | `RestrictedWebView` + `RestrictedWebViewClient` |
| WebView resources and redirects | Native Chromium network loads disabled; permitted GET/HEAD resources fetched through guarded OkHttp; errors return 403 |
| External browser helper | `Urls.open` validates before launching the intent |
| Google Drive/auth/ads/consent/billing/review | Real SDK dependencies removed from every app variant; existing offline stubs used instead |
| Foliate EPUB renderer | Reserved `https://annas-archive.librera.invalid` origin, served entirely by `FoliateServer`; native networking remains blocked |

Local absolute `file:///` documents and `about:blank` are not network access and
remain available. `file://remote-host` and content-provider navigation are not
permitted. Normal WebViews disable JavaScript when loading documents to avoid
uninterceptable channels such as WebSocket/WebRTC. Only the offline EPUB shell
retains JavaScript, its existing CSP and its local-only resource handler. WebView
POST is intentionally disabled because Android does not expose its request body
for safe forwarding. Network-blocking callbacks catch the validator's IOException
and cancel navigation/return 403 rather than crash Android's callback thread.

Consequently, unrelated OPDS catalogs, LAN transfer downloads, Google Drive sync,
ads, release-note websites and third-party remote resources will not work. Links
opened by other apps, OS-managed TTS engines or an external browser after handoff
are outside this app's process; this is not a device-wide firewall.

`Builder/src/main/java/translations/GoogleTranslation.java` is a desktop translation
utility, not shipped in the APK. It is deliberately not subject to the app policy.
The Gradle/SDK/native build downloads are likewise build-time operations.

## Build and CI

This fork's version catalog requires **Java 21**, compile SDK **36**, and the
checked-in Gradle **9.7.1** wrapper (not Java 17). On a machine with the Android
SDK and NDK 27.2.12479018 installed:

```sh
export ANDROID_HOME=/path/to/android-sdk
bash Builder/ci-native.sh
./gradlew assembleDebug
./gradlew :app:testFdroidJSDebugUnitTest
./gradlew :app:assembleFdroidJSRelease -PciReleaseSigning
# With an emulator/device running:
./gradlew :app:connectedFdroidJSDebugAndroidTest
```

`Builder/ci-native.sh` builds the fork's patched native reader libraries and
recreates the checkout's machine-specific symlinks. These large libraries remain
ignored, not checked into Git. No Firebase file or advertising keys are required. The unused `libDepFree` and
`libDepPro` SDK modules are also excluded from the Gradle project so the root
`assembleDebug` task does not build those unrestricted SDK bundles.

`.github/workflows/ci.yml` runs on every push and pull request (also manually),
caches Gradle/native outputs, assembles debug APKs, runs JVM tests, assembles a
minified release APK, boots a headless API 35 x86_64 emulator, and runs the full
FDroid-JS instrumentation suite. Shared policy tests run on both JVM and device.
An entry-point audit catches common newly introduced unguarded clients; it is a
regression tripwire, not a substitute for code review.

After a **successful** run, download the `librera-restricted-apks` Actions artifact:
it contains the universal **FDroid-JS debug and release APKs**, with PDFium/Foliate
instead of MuPDF, plus native DjVu/archive/converter support. The release build
is **signed with the CI debug/test key**, non-debuggable and R8-minified; it is
installable for evaluation but **not a production-signed distribution**. The
throwaway CI signing identity can change between runs, requiring uninstalling a
previous build. Do not use it to update a production installation.

For a production-signed release, omit `-PciReleaseSigning` and configure
`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
`RELEASE_KEY_PASSWORD` in your private Gradle properties. Without these or the
explicit CI flag, release APKs are unsigned. Never commit signing keys.

## Verification in the Arena sandbox

All eight `NetworkPolicyTest` / `RestrictedHttpClientTest` tests were compiled
and executed successfully using Java 21, JUnit 4.13.2 and OkHttp/MockWebServer
3.12.6. The production HTTP policy also passed a local HTTP-server smoke test: initial denial before DNS,
allowed HTTP, blocked cross-host redirect, allowed relative redirect, and policy
retention on cloned clients. The entry-point audit, Java syntax parsing, workflow
YAML parsing, shell syntax check and `git diff --check` also pass.

Full local Gradle compilation is blocked here: Gradle's distribution host, Google
Maven and Android SDK download hosts are outside the sandbox's network allowlist,
and no Android SDK/NDK is installed. A passing CI run and downloadable artifacts
are required before claiming APK delivery or emulator verification.

The first CI attempts exposed an upstream build prerequisite issue: Google SDK
Manager does not provide the fork's original `platforms;android-37` package.
This was confirmed against preview and obsolete SDK channels as well. The version
catalog now uses compile SDK 36, matching the existing target SDK, and PDFium
2.0.0 (the last API-36-compatible release). Its page-size lookup and configuration
calls have been adapted to that version; 2.0.2/2.0.3 demand API 37 in AAR metadata.
This sacrifices the newer library's page-retention optimization but preserves
PDF rendering without pretending an unavailable SDK is installed.
