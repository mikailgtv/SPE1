# Android Release Report — Phase 13

**Status: `BLOCKED` — هیچ Release APK ساخته، امضا یا توزیع نشده است.**

## Source configuration (not build evidence)

| Item | Parent | Child | Evidence/status |
|---|---|---|---|
| applicationId | `ir.guardianlink.parent` | `ir.guardianlink.child` | Gradle source |
| minSdk | 26 | 26 | Gradle source |
| targetSdk | 36 | 36 | Gradle source |
| compileSdk | 36 | 36 | Gradle source |
| source version defaults | `0.1.0`, code 1 | `0.1.0`, code 1 | Default only; no release identity |
| Release debuggable | false | false | Configuration source only |
| API endpoint | Gradle release property `guardianReleaseBaseUrl` | same | Gate requires HTTPS; no real staging URL |
| Signing | env-injected keystore/password/alias | same mechanism | no keystore/password/fingerprint supplied |
| R8/minify | disabled | disabled | Not tested; release blocker |
| Release artifacts | none | none | No Gradle/Android SDK build |
| Instrumentation tests | 0 Android `androidTest` files | 0 Android `androidTest` files | Static file count; none executed |

Older `artifacts/phase5/*debug.apk` files are debug artifacts, not evidence for this release gate; they were not used as release output.

## Attempted/available checks

- Gradle and Gradle wrapper unavailable; Android SDK build could not be started.
- `adb` unavailable; no emulator/physical device or battery/permission test.
- No staging endpoint, signing keystore, signing secrets, trusted certificate fingerprint, APK host or channel owner.
- No `apksigner` signature verification, SHA-256 release checksum, manifest merge audit, release config inspection, R8 test or CI Run ID.
- CI workflow files describe signed APK build/fingerprint verification; source definitions are not artifacts or evidence.

## Android test backlog

1. Install genuine signed release Parent/Child APKs; verify certificate fingerprint and monotonically increasing code/name.
2. Enable R8 only with observed minimal keep rules; test auth, DTO serialization, networking, navigation, notification feed, location/geofence and SOS.
3. Add instrumented tests for launch/login/pairing/consent/permission-denied/dashboard/device state/location sync/rules/web status/SOS/offline recovery/logout.
4. At least one emulator and physical Android device: supported versions, normal/battery-saver/offline/weak network/reboot/app kill/permission revoked/location disabled/notification disabled.
5. Verify direct APK user-controlled install/update, package identity/signature, checksum, update and rollback/hotfix behavior; no silent installation or platform bypass.

## Distribution note

Direct APK was selected as the intended channel but no download site, support/privacy contact, APK update service or signed release package exists. App needs transparent permissions/data handling; no public distribution is authorized by this report. Current official Android developer-verification rollout was documented in `RELEASE_GUIDE.md`; verify current country/channel rules again at release time.
