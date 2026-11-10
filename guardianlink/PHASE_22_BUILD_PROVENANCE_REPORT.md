# Phase 22 — گزارش منشأ و Hash Artifactها

زمان local build وب: پنجرهٔ 2026-10-05 21:12–21:13 UTC؛ Node v20.20.2، npm 10.8.2؛ `npm ci --ignore-scripts`, `npm test`, `npm run build` و `npm audit --audit-level=high` موفق شدند. هیچ CI runner یا Git commit موجود نیست.

| Artifact Name | Version | Commit | Build Time | Build Environment | SHA-256 | Build Result |
|---|---|---|---|---|---|---|
| Parent Android APK/AAB | `0.1.0` default / versionCode `1` | unavailable (`.git` absent) | — | Android SDK/Gradle/JDK17/signing env absent | N/A | `NOT BUILT`; `BLOCKED BY ENVIRONMENT` |
| Child Android APK/AAB | `0.1.0` default / versionCode `1` | unavailable | — | Android SDK/Gradle/JDK17/signing env absent | N/A | `NOT BUILT`; `BLOCKED BY ENVIRONMENT` |
| Parent Web build bundle, local archive `phase22-artifacts/guardianlink-parent-web-0.1.0-dist.tar.gz` | package `0.1.0` (not released) | unavailable | 2026-10-05 21:12–21:13 UTC | local sandbox/Linux, Node20.20.2/npm10.8.2 | `3ff5749595cfb5512e815062c8be61c79e9326073340fe0acc0c7f626a8ea991` | `PASS` for local build/archive only; **NOT deployed/released** |
| Web main JS | Vite hashed chunk | unavailable | same | same | `aaa15bde152d3430d4d49417ed9167d4a218d30a00dbd607b9a39c16a77ea83c` | built |
| Web CSS | Vite hashed chunk | unavailable | same | same | `225037313f775a7fdfc69381da380abcf891fee175943fb2e96c293d2a5aa5a2` | built |
| Backend JAR/container | `0.1.0` source version | unavailable | — | Gradle/wrapper/JDK17/Docker unavailable | N/A | `NOT BUILT`; `BLOCKED BY ENVIRONMENT` |
| Database migration bundle | source target through V9 | unavailable | — | no packaging/migration run | N/A | `NOT BUILT`; SQL files remain source only |

Web archive contains seven files from `web/dist` and is stored in workspace as a local evidence artifact only. It is not a signed release tarball, does not have SLSA provenance, is not published, not deployed and not approved for user distribution. Archive SHA is over the actual tar.gz bytes; inner JS/CSS digests are included above. P22-ART-005, local Linux sandbox, 2026-10-05 ~21:17 UTC: `tar -tzf phase22-artifacts/guardianlink-parent-web-0.1.0-dist.tar.gz` exited 0; seven members listed; `sha256sum` returned the digest above (`PASS` for archive integrity/readability only, not authenticity). Source tree fingerprint (not artifact digest) is in `PHASE_22_VERSION_FREEZE_REPORT.md`.

No Android signing key, API credential, secret, certificate or Git commit was manufactured. No other artifact or container image exists in evidence.
