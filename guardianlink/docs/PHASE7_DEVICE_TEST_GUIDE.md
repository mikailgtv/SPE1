# راهنمای آزمون دستگاه واقعی Phase 7 — نتیجه‌ها Pending

این راهنما فقط برای دستگاهی است که مالک آن اجازه داده و با مسیر رسمی Android Enterprise به‌صورت Device Owner یا Profile Owner provision شده است. **هیچ مجوز یا مدیریت دستگاهی را دور نزنید.** در دستگاه شخصی/حالت عادی، نتیجهٔ مورد انتظار «Unsupported» است و block نباید ادعا شود.

## پیش‌شرط‌ها

1. پیش از نصب، Server/Android build و migration `V7__phase7_web_safety.sql` را در CI/محیط آزمایشی اجرا کنید؛ هیچ‌یک در محیط فعلی تأیید نشده‌اند.
2. دستگاه آزمایشی پاک/مخصوص QA و حساب Parent آزمایشی داشته باشید؛ provisioning رسمی باید توسط DPC/Android Enterprise انجام شود. توجه: Device Owner setup معمولاً به provisioning روی دستگاه تازه/بازنشانی‌شده وابسته است.
3. Chrome Android نصب و به‌روز باشد. برای اثبات دامنه فقط از test domain تحت کنترل تیم استفاده کنید.
4. اعلان‌های Parent و گزینهٔ `alertsEnabled` را روشن کنید؛ اعلان‌ها به Parent می‌روند، نه Child.
5. در Child Home، disclosure ایمنی وب را بازبینی کنید. تأیید کنید scope Chrome-only و عدم جمع‌آوری سابقه/بازدید روشن است.

## ماتریس آزمون

| مورد | روش | نتیجهٔ مورد انتظار |
|---|---|---|
| حالت عادی | روی دستگاه غیرمدیریت‌شده، ایمنی وب را روشن و یک domain BLOCK اضافه کنید | Parent باید `UNSUPPORTED / managed_device_required` نشان دهد؛ نباید ادعای block داشته باشد؛ بازدید عادی Chrome نباید به‌عنوان کنترل‌شده گزارش شود |
| DPM managed configuration | روی دستگاه Owner/Profile Owner، domain تحت کنترل آزمایش را BLOCK کنید و sync اجرا کنید | Child گزارش `CONFIGURATION_APPLIED_UNVERIFIED` فقط پس از set/readback DPM؛ Parent صریحاً اثر نهایی را تأییدنشده نمایش دهد |
| Chrome policy | در خود Chrome، `chrome://policy` را باز کنید و `URLBlocklist`/`URLAllowlist` را refresh و مقدارها را بررسی کنید | مشاهدهٔ policy در Chrome، شاهد جداگانهٔ QA است؛ وضعیت برنامه همچنان readback DPM را با اثر واقعی یکی نمی‌داند |
| دامنه و زیردامنه | rule دامنهٔ ریشه روی test domain بگذارید، سپس apex و subdomain را بررسی کنید | bare domain باید به apex و زیردامنه‌ها برسد؛ رفتار واقعی را در نسخهٔ Chrome همان دستگاه ثبت کنید |
| میزبان دقیق | API `scope=EXACT_HOST` را روی یک host آزمایشی تنظیم کنید | rule از syntax دقیق Chrome استفاده می‌کند؛ host دیگر نباید به‌واسطهٔ آن rule متأثر شود |
| allow exception | دامنه را BLOCK و همان سطح را ALLOW کنید | precedence مطابق سیاست native Chrome در `chrome://policy` و مرور واقعی بررسی شود؛ اگر نتیجه مبهم است، release را متوقف کنید |
| allow-listed-only | حالت «فقط موارد مجاز» را فعال کنید و allow domain کنترل‌شده اضافه کنید | `URLBlocklist` شامل `*` و allow ruleها در `URLAllowlist` باشد؛ حتماً دسترسی اضطراری و صفحات داخلی Chrome را بررسی کنید |
| زمان‌بندی | schedule کوتاه با start/end و منطقه‌زمانی دستگاه تعریف کنید؛ در لحظه‌های قبل/حین/بعد وضعیت Chrome را بررسی کنید | تغییرات فقط پس از اجرای Worker/رسیدن policy اعمال می‌شود؛ زمان‌بندی realtime تضمین نشده است |
| نیمه‌شب/DST | schedule عبوری از نیمه‌شب و تغییر ساعت منطقه‌ای را در تاریخ آزمون کنید | مرز روز/ساعت در zone دستگاه بررسی شود؛ نتیجهٔ DST ثبت و ایرادها رفع شوند |
| override موقت | یک block داشته باشید، اجازهٔ ۱۵ دقیقه‌ای بسازید و در لحظهٔ انقضا بررسی کنید | override فقط روی Chrome policy منعکس می‌شود؛ در offline/Doze، expiry ممکن است تا اجرای بعدی WorkManager با تأخیر اعمال شود |
| Category | category rule را فقط از تست API ثبت کنید | `categoryEnforceable=false` و توضیح provider unavailable؛ هیچ URLBlocklist جعلی از category ساخته نشود |
| VPN/DNS | API را روی `VPN_BASED` یا `DNS_BASED` قرار دهید | `UNSUPPORTED` با reason روشن؛ هیچ VPN consent، VPN connection، DNS resolver یا ادعای فیلتر ایجاد نشود |
| Chrome غایب | Chrome را در دستگاه تستی حذف/غیرفعال کنید | `UNSUPPORTED / chrome_not_installed`؛ سایر برنامه‌ها همچنان خارج از scope هستند |
| alert | وضعیت unsupported/error را با alerts روشن و سپس خاموش آزمایش کنید | فقط با alerts روشن، Parent notification؛ هیچ اعلان Phase 7 برای Child ساخته نشود |
| حریم خصوصی و داده | DB/event payload و network capture را بازبینی کنید | فقط rule config، زمان و reason/status محدود؛ هیچ URL بازشده، query، تاریخچه، محتوای صفحه، form/password/cookie یا blocked-visit event نباشد |
| حذف/خاموش‌سازی | protection را خاموش و rule حذف کنید | DPM restrictions پاک شوند (در نقش managed مجاز)، Parent UI وضعیت غیرفعال/همگام‌سازی را نشان دهد؛ بررسی کنید Chrome policy قدیمی باقی نمانده باشد |
| ایزوله‌سازی | از Parent خانوادهٔ دیگر device ID و rule ID را صدا بزنید؛ از Child token دیگر device sync کنید | Parent درخواست باید 404/رد و Child فقط سیاست دستگاه خودش دریافت کند |

## مواردی که باید ثبت شوند

- دستگاه/Android build، نسخهٔ Chrome، Device Owner یا Profile Owner، نوع پروفایل.
- مقدار دقیق `chrome://policy` قبل/بعد، refresh/policy status، عمل دستی در Chrome و نتیجه.
- timestamp سرور، DPM readback، sync و تأخیر WorkManager؛ اعداد فقط پس از اندازه‌گیری.
- نسخهٔ API/server، migration status، test output، logcat فقط با حذف token/اطلاعات شخصی.
- اگر Chrome policy نامعتبر یا بی‌اثر است، گزارش را `ERROR`/unverified نگه دارید؛ به `Protected/Active` تغییر ندهید.

## آزمون‌های خودکار پیشنهادی پیش از دستگاه

- Server: `WebSafetyPolicyTest`, `Phase7Test`, تست migration/CRUD/rollback و family/device authorization.
- Android: fake DPM adapter tests برای normal/Owner/ProfileOwner، Chrome missing، Bundle contents/readback mismatch، precedence، expiry، روزهای هفته و overnight schedule.
- Release gate: هر دو اپ/سرور compile و تست کامل؛ تست واقعی Chrome؛ Phase 6 regressions جداگانه. تا زمان اجرا، همه Pending هستند.
