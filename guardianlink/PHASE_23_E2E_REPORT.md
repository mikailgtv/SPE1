# گزارش E2E Phase 23

**وضعیت:** `PARTIALLY_VALIDATED`; اجرای واقعی end-to-end API/backend/DB از Vite proxy انجام شد. این گزارش Android device/UI browser/provider E2E را ادعا نمی‌کند.

## Test execution
- **Test ID:** `P23-E2E-API-01`
- **Environment:** Debian 13; PostgreSQL 17.11 test DB `guardianlink_phase23`; Ktor backend :8080; Vite 7.3.6 :5173؛ base=`http://127.0.0.1:5173`; synthetic accounts/devices, no real child/device/provider.
- **Command:** `python3 /tmp/gl_phase23_api_flow.py`
- **Start:** `2026-10-06T07:31:17.795213Z`
- **End:** `2026-10-06T07:31:21.677169Z`
- **Expected:** end-to-end setup, real DB writes/reads, notification and SOS lifecycle, family/device isolation, auth/session controls; request-size guard separately assessed.
- **Actual:** 54 HTTP checks produced response status in the script’s expected set; flow-level assertions for usage totals, stored current fix, server-derived geofence entry, notification row and SOS state also completed. 53 HTTP/status checks are counted PASS. One 1 MiB body-size check is **NOT VERIFIED**: actual response 401, not 413; it proves no 1 MiB rejection, not a passing guard.
- **Artifact:** execution transcript emitted by command in tool output; helper script `/tmp/gl_phase23_api_flow.py` (outside shared workspace); report is sanitized. No token, password, or coordinate printed here.

## Results by flow
| Test ID | Request/assertion | Expected | Actual | Status |
|---|---|---|---|---|
| P23-E2E-01 | Register parent A/B | 201 | 201 / 201 | PASS |
| P23-E2E-02 | Create family A/B | 201 | 201 / 201 | PASS |
| P23-E2E-03 | Pair each child: pairing session, exchange, confirm, device auth | 201/200/201/200 | each child matched | PASS |
| P23-E2E-04 | Child A device status sync | 200 | 200; prior 500 fixed by correct device session mapper | PASS |
| P23-E2E-05 | Parent A lists own paired device | 200 + matching device ID | 200; assertion matched | PASS |
| P23-E2E-06 | Child A usage upload + parent reads today | 200 + total >=17 synthetic minutes | 200; payload assertion matched | PASS |
| P23-E2E-07 | Parent enables synthetic location sharing, creates zone | 200; 200 | both matched | PASS |
| P23-E2E-08 | Child location sync + parent reads current fix | 200; non-null fix | 200; assertion matched after `upsertStatus` fix | PASS |
| P23-E2E-09 | Geofence event feed | 200 + `GEOFENCE_ENTER` present | 200; server-derived transition assertion matched | PASS |
| P23-E2E-10 | Notification feed + acknowledgement | 200; nonempty; ack 200 | matched | PASS |
| P23-E2E-11 | Child A SOS activate; Parent A reads/ack/resolves | 200/201; active/activating; 200; 200 | matched | PASS |
| P23-E2E-12 | Child B SOS and foreign-session read isolation | create 200/201; unauthorized reads 404 | matched, including cross-family device/usage/location/geofence/notification/SOS and cross-device SOS | PASS |
| P23-E2E-13 | Login/refresh rotation/logout | 200; old refresh 401; new access 200; logout 204; revoked access 401 | matched | PASS |
| P23-E2E-14 | Invalid bearer | 401 | 401 | PASS |
| P23-E2E-15 | Login limiter 9 attempts | 8 error responses then 429 | 401 for 1–8; 429 for 9 | PASS (client status) |
| P23-E2E-16 | Family export/delete capability | 404; 404/405 | 404; 405 | OBSERVED; feature absent, not capability PASS |
| P23-E2E-17 | 1 MiB padded login payload | prove server returns 413/size ceiling | HTTP 401 | NOT VERIFIED; 1 MiB was accepted through parsing/auth handling |

## Known caveats
1. Backend logs for some expected client-side 401/429 responses recorded completion/error status 500. Security API response assertions used the HTTP client result; status logging inconsistency remains open.
2. Test used permitted synthetic package/time/location/SOS inputs in the isolated test DB only. No dispatch to emergency service or push provider occurred.
3. E2E is backend/API contract and persistence, not parent UI interaction, Android app/runtime, real UsageStats, Managed Device enforcement, or a real phone.
4. Export/delete routes were absent; no destructive test attempted.

## Exact Next Step
Add regression tests for `deviceSessionRow` and `LocationRepo.upsertStatus`; rerun the same API flow with Docker/Testcontainers CI and a true request-size test after defining an explicit limit. Then execute Playwright UI and Android device tests; production gates stay closed until those and the open matrix items pass.
