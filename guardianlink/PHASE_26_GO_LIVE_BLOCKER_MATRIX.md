# ماتریس موانع Go-Live فاز ۲۶

**تاریخ بررسی:** ۷ اکتبر ۲۰۲۶  
**محدوده:** بازبینی مخزن و ابزارهای همین workspace؛ هیچ دسترسی به حساب cloud، production، staging، DNS، credential یا سرویس بیرونی وجود نداشت. پیکربندی/سند به‌تنهایی اثبات آمادگی نیست.
| ID | مانع | شدت | فاز منبع | Owner | لازم پیش از Go-Live | وضعیت |
|---|---|---|---|---|---|---|
| GL-01 | UAT کاربر واقعی و journey اصلی پاس نشده؛ فاز ۲۵ محیطی مسدود | P1 | 25 | تخصیص‌نیافته؛ QA Lead پیشنهادی | UAT واقعی/ثبت evidence | BLOCKED |
| GL-02 | محیط production واقعی، account/VPC/network/TLS/deployment وجود/دسترسی اثبات‌شده ندارد | P1 | 20–26 | تخصیص‌نیافته؛ SRE/Cloud Owner | provisioning و verification | BLOCKED |
| GL-03 | کلید JWT در فایل `DATA_DIR/ed25519.jwk` ساخته/خوانده می‌شود؛ secret manager، persistence/rotation در deploy واقعی اثبات نشده | P1 | 22–26 | تخصیص‌نیافته؛ Security/Backend | مدیریت کلید پایدار، rotation و restore drill | BLOCKED |
| GL-04 | migrationها در startup اپ اجرا می‌شوند؛ runtime/migrator role separation و rehearsal نامعلوم | P1 | 23–26 | تخصیص‌نیافته؛ DBA | roles، rehearsal، integrity و failure path | BLOCKED |
| GL-05 | DB production، TLS/at-rest, private routing, least privilege, limits اثبات نشده | P1 | 22–26 | تخصیص‌نیافته؛ DBA/Cloud | provisioning/audit واقعی | BLOCKED |
| GL-06 | Backup encrypted/S3 script فقط source است؛ scheduler، storage، retention, checksum/restore, alert واقعی نداریم | P1 | 23–26 | تخصیص‌نیافته؛ Backup Owner | backup + destructive isolated restore + integrity + RPO/RTO | BLOCKED |
| GL-07 | rollback و deploy workflow اجرایی نیست؛ روش مستند اما تمرین نشده | P1 | 21–26 | تخصیص‌نیافته؛ Release/DB | drill backend/web/DB/Android | BLOCKED |
| GL-08 | UAT/Phase23 family export `404`, family delete `405`; capability ناکافی/غایب در evidence | P1 | 23–25 | تخصیص‌نیافته؛ Product/Privacy/API | scope review + approved implementation + test، یا مصوبهٔ محدودیت قابل قبول | BLOCKED |
| GL-09 | body limit Phase24 کد تغییر داده اما compile/runtime test موجود نیست؛ Phase23 1MiB probe به `401` رسید | P1 | 23–24 | تخصیص‌نیافته؛ Backend/Security | valid-auth boundary tests در Ktor/proxy | BLOCKED |
| GL-10 | Android APK release و real signing key/cert/hash/artifact/provenance وجود ندارد | P1 | 23–26 | تخصیص‌نیافته؛ Android Release | signed builds, verified cert, key recovery | BLOCKED |
| GL-11 | Android runtime/device matrix و release build اثبات نشده | P1 | 23–25 | تخصیص‌نیافته؛ Android QA | build/install/UAT smoke روی device | BLOCKED |
| GL-12 | Backend tests کامل Docker/Testcontainers و current build/runtime اثبات نشده | P1 | 23–25 | تخصیص‌نیافته؛ Backend QA | build + full suite + staging integration | BLOCKED |
| GL-13 | Core E2E، cross-family, notification, SOS, offline/reconnect روی current RC اجرا نشده | P1 | 23–25 | تخصیص‌نیافته؛ QA | E2E واقعی و safe acceptance | BLOCKED |
| GL-14 | Web automation/UAT و serve/login path انجام نشده | P1 | 25–26 | تخصیص‌نیافته؛ Web QA | same-origin staging smoke/UI E2E | BLOCKED |
| GL-15 | monitoring/alert destinations، on-call owner و incident paging راه نیفتاده | P1 | 18–26 | تخصیص‌نیافته؛ SRE/Incident Lead | provisioned alerts + fired/resolved drills | BLOCKED |
| GL-16 | process-local rate limiter؛ production replica count و shared limiter حل نشده | P1 | 23–26 | تخصیص‌نیافته؛ Backend/Architecture | single replica constraint with evidence or shared limiter | BLOCKED |
| GL-17 | HTTPS cert/DNS/renewal/redirect/HSTS/device trust از اینترنت تست نشده | P1 | 22–26 | تخصیص‌نیافته؛ Network/SRE | deployed TLS verification | BLOCKED |
| GL-18 | CORS/origin/browser security behavior در hostname واقعی آزمایش نشده | P2 | 23–26 | تخصیص‌نیافته؛ Web/Security | origin policy decision and tests | NOT_TESTED |
| GL-19 | notification provider/FCM, monitoring, storage integrations config/credentials absent | P1 | 23–26 | تخصیص‌نیافته؛ Integrations Owner | decide scope and verify or disclose unsupported | BLOCKED |
| GL-20 | Privacy retention/delete/export, audit evidence و policy approval کامل نیست | P1 | 20–26 | تخصیص‌نیافته؛ Privacy/Product | functional review and jurisdiction-specific approval | BLOCKED |
| GL-21 | Production config approval/version artifact/commit provenance ندارد؛ workspace Git metadata ندارد | P2 | 24–26 | تخصیص‌نیافته؛ Release | immutable source/artifact attestations | BLOCKED |
| GL-22 | لو رفتن secret در git history و secret manager inventory verify نشده | P1 | 26 | تخصیص‌نیافته؛ Security | full history scan + key inventory/rotation | BLOCKED |

**حکم:** بیش از یک Go-Live blocker باقی است؛ هیچ blockerی صرف وجود راهنما، Compose، CI یا script بسته نشده. تمام Ownerهای انسانی `UNASSIGNED` هستند.
