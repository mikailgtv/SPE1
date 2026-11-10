# Phase 21 — گزارش راستی‌آزمایی وب

**نتیجه:** `PARTIAL PASS`: unit tests، npm audit و production compile واقعی محلی موفق؛ browser rendering، deployed API/CSP، auth/session و E2E تأیید نشده است.

## Test evidence

| Test ID | Environment | Command | Timestamp (UTC) | Expected | Actual | Result | Artifact |
|---|---|---|---|---|---|---|---|
| WEB-DEP-001 | `web/`, Node v20.20.2, npm10.8.2 | `npm ci --ignore-scripts` | 2026-10-05 ~13:06 | install from lock | 161 packages added; npm 0 vulnerabilities | `PASS` | console |
| WEB-UNIT-002 | same | `npm test` | 2026-10-05 13:06:13 start | Vitest suite success | 2 files, 4 tests passed | `PASS` | console |
| WEB-BUILD-003 | same | `npm run build` | 2026-10-05 ~13:06 | TS check + Vite production build | success, 90 modules; HTML 0.71 kB; CSS 35.46 kB/7.61 gzip; JS 385.93 kB/118.12 gzip | `PASS` | `web/dist/` (snapshot excluded) |
| WEB-AUDIT-004 | same | `npm audit --audit-level=high` | 2026-10-05 ~13:06 | no high+ findings | 0 vulnerabilities | `PASS` for web npm graph | console |
| WEB-BROWSER-005 | local browser | manual/automation, `NOT RUN` | 2026-10-05 | render, route, responsive, accessibility | no browser acceptance run recorded | `NOT TESTED` | none |
| WEB-LIVEAPI-006 | staging API/browser | `NOT RUN` | 2026-10-05 | real authentication + `/v1` operations | no API endpoint/PG/staging; `BLOCKED` | none |
| WEB-SEC-007 | deployed browser/edge | CSP/CORS/cookie/network test, `NOT RUN` | 2026-10-05 | deployment policy matches expected origin | config source only | `NOT TESTED` | none |

## Scope and limitations

Vitest tests are only `src/api.test.ts` and `src/i18n.test.ts` (3 API helper tests + 1 i18n test). They are not dashboard journey, browser, backend contract, auth, privacy export/delete, cross-family authorization, or E2E. No end-to-end scenario was mocked and then claimed as success.

`npm audit` excludes Android/Maven, GitHub Actions, Docker base images and Git history. `npm ci --ignore-scripts` avoids package install lifecycle scripts; build/test still run repository package scripts afterward. npm printed a deprecated `whatwg-encoding@3.1.1` notice; no vulnerability was reported. Bundle size is recorded, not a performance guarantee.

No Web source was changed in this phase. Accessibility, French/Persian locale behavior, browser storage privacy, screen-reader checks, and deployed CSP remain release gates.
