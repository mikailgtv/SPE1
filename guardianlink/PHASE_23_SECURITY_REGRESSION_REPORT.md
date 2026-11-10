# گزارش Security Regression Phase 23

**نتیجه:** بخشی از API regression واقعی PASS؛ Android policy و end-to-end body-size guard باز هستند. هیچ مجوز/احراز هویت دور زده نشد و هیچ token/secret در گزارش درج نشده است.

## اجرای امنیتی واقعی
| ID | محیط/فرمان | زمان UTC | انتظار | نتیجهٔ مشاهده‌شده | وضعیت | Artifact |
|---|---|---|---|---|---|---|
| P23-SEC-01 | API flow از Vite proxy؛ دو حساب/family مستقل، pairing و device auth | 2026-10-06 07:31:17.795–07:31:21.677Z | هر دستگاه به parent خودش محدود | cross-family device/usage/location/geofence/notification/SOS readها 404؛ child A نتوانست SOS child B را بخواند (404) | PASS | `PHASE_23_E2E_REPORT.md` |
| P23-SEC-02 | refresh/logout lifecycle در همان flow | همان بازه | refresh rotation و revocation | refresh قبلی 401؛ rotated token معتبر؛ logout 204؛ access بعد logout 401 | PASS | E2E report |
| P23-SEC-03 | invalid bearer | همان بازه | deny 401 | client HTTP 401 | PASS | E2E report |
| P23-SEC-04 | login limiter: 9 login fail | همان بازه | 8 attempts allow error response, next throttle | 1–8: 401؛ 9: 429 | PASS (status client) | E2E report |
| P23-SEC-05 | 1 MiB padded login body | همان بازه | 413/explicit size limit | HTTP 401، نه 413؛ body parse/validation تا auth رسید | NOT PASS; request cap not established | E2E report; `P23-B11` |
| P23-SEC-06 | error logging compare | همان flow | HTTP outcome و log status consistent | برخی client 401/429 در log به‌صورت 500 ثبت شده‌اند | FOLLOW-UP | Backend report |
| P23-SEC-07 | Android permission/enforcement | SDK/device غایب؛ no command executable | policy enforcement ثابت شود | اجرا نشد؛ Normal Mode را به‌جای Managed Device نمی‌شماریم | BLOCKED | Android runtime report |

## موارد خارج از آزمون
- Penetration testing، fuzzing، JWT key rotation, CORS/CSRF prod edge, TLS/headers, dependency SCA، cloud IAM و production security review: `NOT TESTED`.
- هیچ secret، bearer token، password، coordinate شخصی یا user content خام در گزارش ثبت نشده است. مختصات مصنوعی Warsaw فقط در test request درون test DB و بدون چاپ اجرا شد.
- Probe حجمی در suite صرفاً 1 MiB بوده؛ از آن نمی‌توان حداکثر اندازهٔ عمومی یا DoS resilience نتیجه گرفت.

## اقدام‌های Release Gate
1. حد مجاز body را در server/proxy صریح تعریف؛ برای login/device endpoints اندازه‌های مرزی را با credential مجاز و test-safe payload بسنج.
2. اصلاح logger برای ثبت status نهایی بعد از StatusPages؛ regression بررسی کند 401/429 با همین مقدار ثبت شوند.
3. اجرای suite با Docker/Testcontainers و API tests پس از bugfixها؛ Android managed policy فقط روی دستگاه مجاز.
