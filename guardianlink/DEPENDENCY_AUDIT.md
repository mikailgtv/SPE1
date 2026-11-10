# Dependency Inventory & Audit Status

**وضعیت:** inventory ایستا از Gradle files؛ vulnerability database scan، transitive resolution یا update PR اجرا نشده. `gradle`, OSV, Trivy, Syft, Grype, Semgrep در محیط موجود نیستند. `Known vulnerabilities` و `Update available` برای موارد زیر **UNVERIFIED** است، نه «صفر».

## Server direct dependencies

| Dependency | Version in source | Purpose | Vulnerability status | Update available | Breaking risk |
|---|---:|---|---|---|---|
| Ktor Netty/server plugins | 3.6.0 | HTTP server, serialization, status pages, logging | UNVERIFIED | UNVERIFIED | High: routing/config/plugin API |
| Jackson Kotlin module | 2.18.2 | DTO JSON serialization | UNVERIFIED | UNVERIFIED | Medium: serializer behavior/schema |
| HikariCP | 5.1.0 | PostgreSQL connection pool | UNVERIFIED | UNVERIFIED | Medium: pool defaults/API |
| PostgreSQL JDBC | 42.7.4 | Database driver | UNVERIFIED | UNVERIFIED | High: JDBC/TLS/driver compatibility |
| Flyway core / PostgreSQL | 10.20.1 | Versioned SQL migrations | UNVERIFIED | UNVERIFIED | High: migration engine behavior |
| Nimbus JOSE JWT | 9.37.3 | EdDSA/JWT validation | UNVERIFIED | UNVERIFIED | High: token/JWK/algorithm behavior |
| argon2-jvm | 2.11 | Password hashing | UNVERIFIED | UNVERIFIED | Medium: native binaries/params |
| Logback classic | 1.5.6 | SLF4J logging backend | UNVERIFIED | UNVERIFIED | Medium: configuration/serialization |
| JUnit Jupiter | 5.10.2 | Server tests | UNVERIFIED | UNVERIFIED | Low/medium |
| Testcontainers PostgreSQL/JUnit | 1.20.4 | Integration tests with disposable Postgres | UNVERIFIED | UNVERIFIED | Medium: Docker/runtime support |

## Android direct/catalog dependencies

| Dependency/version | Purpose | Vulnerability/update/breaking status |
|---|---|---|
| Android Gradle Plugin 9.4.1 | Android build | UNVERIFIED; build compatibility not tested |
| Kotlin / Compose plugin 2.4.20 | Kotlin compiler and Compose integration | UNVERIFIED; compiler compatibility not tested |
| AndroidX Core KTX 1.16.0 | Android core helpers | UNVERIFIED |
| Activity Compose 1.10.1 | Activity/Compose hosting | UNVERIFIED |
| Compose BOM 2025.09.00; UI/Material3 | UI toolkit alignment | UNVERIFIED |
| Lifecycle 2.9.1 | Lifecycle/Compose | UNVERIFIED |
| Navigation Compose 2.9.0 | Navigation | UNVERIFIED |
| WorkManager 2.10.0 | Scheduled/background work | UNVERIFIED; OS behavior needs device tests |
| DataStore 1.1.1 | Local preferences | UNVERIFIED |
| OkHttp 4.12.0 | HTTP client | UNVERIFIED |
| Gson 2.11.0 | JSON conversion | UNVERIFIED; R8 keep rules not checked |
| ZXing 3.5.3 | Pairing QR processing | UNVERIFIED |
| Coroutines 1.10.1 | Async/background operations | UNVERIFIED |
| JUnit 4.13.2 / MockWebServer 4.12.0 | Android tests/network tests | UNVERIFIED |
| Play Services Location 21.3.0 | Official fused-location API | UNVERIFIED; permission/device checks outstanding |

## CI/GitHub Actions

Workflow files currently use tags `actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4`, `android-actions/setup-android@v3`, `actions/upload-artifact@v4`, `actions/dependency-review-action@v4`, `github/codeql-action@v3`, `aquasecurity/trivy-action@0.36.0`, `docker/login-action@v3`, `docker/setup-buildx-action@v3`, `docker/build-push-action@v6`. Tags are not immutable SHA pins; runtime release validation is pending.

## Required audit actions

1. Add Gradle dependency locking/verification only after successful resolution; commit lockfiles and verify checksums.
2. Generate SBOM for server image and both Android APKs; attach artifact and retention/access policy.
3. Run OSV/OWASP/Trivy/CodeQL/secret scans on CI; fail on Critical/High with documented exception owner and expiry.
4. Review each dependency purpose, transitive tree, license, CVE, supported version, upstream security policy and upgrade compatibility.
5. Re-run all unit/integration/E2E and signature checks after upgrades.
