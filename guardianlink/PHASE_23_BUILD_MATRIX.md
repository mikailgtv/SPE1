# ماتریس Build Phase 23

**تاریخ:** 2026-10-06 UTC · build matrix نشان می‌دهد build محلی Web/backend موفق است، Android blocked؛ production build/deploy اجرا نشده.

| ID | Component | Command/Toolchain | Expected | Actual | Status | Evidence |
|---|---|---|---|---|---|---|
| P23-BLD-01 | Backend distribution | JDK17.0.20.1+1; Gradle 9.7.1; offline; `installDist`; 256 MiB heap/metaspace | compile/install | `BUILD SUCCESSFUL` پس از database/repository fixes | PASS | Backend runtime report |
| P23-BLD-02 | Backend unit/integration suite | Gradle 9.7.1; `test` | all tests green | 141 complete, 122 fail due Docker provider/Testcontainers; 19 pass | BLOCKED_BY_ENVIRONMENT | backend test HTML report (generated) |
| P23-BLD-03 | Web typecheck + production bundle | Node 20.20.2/npm10.8.2; `npm run build` | `tsc -b && vite build` | 90 modules; JS 385.93kB, CSS 35.46kB; successful | PASS | Web integration report |
| P23-BLD-04 | Android parent/child debug | AGP9.4.1; Gradle8.14.3 command; Gradle9.7.1 attempted | both APK tasks successful | 8.14.3 below minimum 9.6; 9.7.1 build daemon disappeared/time out; SDK 36 absent | BLOCKED | Android build report |
| P23-BLD-05 | Android release/signing | — | signed release artifacts | not attempted | NOT TESTED | none |
| P23-BLD-06 | Production deploy artifact | — | controlled deployment + provenance | explicitly not performed | NOT TESTED; gate closed | final report |

## Build acceptance status
Release Candidate **not established**: missing Android binaries/runtime, Docker-backed test suite, explicit request-body guard verification, and complete security/deployment validations. Successful Web/backend development builds alone are insufficient.
