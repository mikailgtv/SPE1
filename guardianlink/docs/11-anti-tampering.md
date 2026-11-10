# 11 — Anti-Tampering Architecture (v2)

> هدف: فرزند نتواند به‌سادگی اپ را حذف کند، سرویس‌ها را متوقف کند، مجوزها را بی‌ثبت رویداد تغییر دهد، ارتباط را قطع کند، تنظیمات را دستکاری کند یا با پاک‌کردن Cache/Data کنترل‌ها را دور بزند.
>
> **خط قرمزهای پروژه (غیرقابل مذاکره):** بدون Root Exploit · بدون Spyware/Malware · بدون **Accessibility Abuse** · بدون Exploit · بدون دور زدن Android Security · بدون دور زدن Google Play Policies.
>
> **اصل جایگزین:** «پیشگیری فقط با مکانیزم رسمی OS؛ در غیر این صورت **تشخیص + گزارش + بازیابی**.» هر جا Android به‌صورت رسمی امکان پیشگیری می‌دهد (Device Owner / Android Enterprise / Device Admin)، همان را پیاده‌سازی می‌کنیم و دقیقاً مستند می‌کنیم.

---

## ۱. مدل سطح حفاظت (Protection Modes)

| mode | چگونه فعال می‌شود | چه چیزی **رسماً** پیشگیری می‌شود | نقش در محصول |
|---|---|---|---|
| **M0 Normal** | نصب معمولی APK/Play | هیچ پیشگیری‌ای؛ فقط تشخیص | حالت پایه؛ مانیتورینگ کامل، enforcement فقط advisory |
| **M1 Device Admin** | کاربر در Settings فعال می‌کند (`BIND_DEVICE_ADMIN`) | هیچ پیشگیری قطعی؛ ولی **هشدار اولیه**: غیرفعال‌کردن admin باید از مسیر سیستم بگذرد و `onDisableRequested/onDisabled` فراخوانی می‌شود ⇒ رویداد TAMPER قبل از حذف اپ | «کمربند ایمنی»: سیگنال early-warning برای uninstall |
| **M2 Device Owner (DO)** | Provisioning رسمی: QR zero-touch روی دستگاه نو/factory-reset، یا `adb shell dpm set-device-owner` | **Uninstall مسدود** · **Force-stop مسدود** · **Clear data/cache مسدود** · `setPackagesSuspended` واقعی · `setApplicationHidden` · User Restrictions (نصب اپ، USB، debug، …) · Lock Task (kiosk) ·_policyهای رمز/دوربین_ · معافیت از battery optimization و standby bucket | **حالت توصیه‌شده پروژه** برای Child؛ تنها مسیر رسمی «کنترل واقعی» |
| **M3 Android Enterprise / AMA** | Android Management API (cloud) یا EMM | همه M2 + مدیریت از راه دور سازمانی، silent install/uninstall، policy از سرور | برای ناوگان/مدارس؛ integration اختیاری Phase 10 |
| **M4 Profile Owner** | Work profile | مدیریت اپ‌های داخل پروفایل (نه کل دستگاه) | مقایسه فقط؛ انتخاب نشده (تجربه کاربردی پیچیده برای خانواده) |
| **Family Link** | محصول خود Google | — | **قابل استفاده توسط اپ ثالث نیست**؛ فقط به‌عنوان مرجع طراحی UX؛ وابستگی نداریم |

> **پاسخ صریح به «اگر Android اجازه نمی‌دهد جعل کن»:** ما جعلِ **داده** انجام نمی‌دهیم (چون روی گوشی واقعی تست می‌کنید و داده جعلی تست‌ها را بی‌معنا می‌کند). آنچه انجام می‌دهیم: **Capability Ledger** — هر قابلیت در UI برچسب دقیق «واقعی/سیستمی/مشاوره‌ای/غیرممکن در این mode» می‌گیرد + یک **Demo Mode** کاملاً برچسب‌دار (فقط debug) برای ضبط ویدیو. جدول کامل در [`13-android-api-and-modes.md`](./13-android-api-and-modes.md) §5.

---

## ۲. سطح حملات Tamper × پیشگیری × تشخیص (ماتریس اصلی)

| # | بردار Tamper | M0/M1 (پیشگیری) | M2 DO (پیشگیری رسمی) | تشخیص (همه modeها) | رویداد `[TAMPER]` |
|---|---|---|---|---|---|
| T-01 | **حذف اپ (Uninstall)** | ✖ ممکن | ✔ سیستم مسدود می‌کند (`DO` ⇒ uninstall blocked) | M1: کاربر مجبور است اول admin را غیرفعال کند ⇒ `DeviceAdminReceiver.onDisabled` ⇒ رویداد فوری · M0: سکوت دستگاه + `lease expired` + هیوریستیک re-enroll | `app.uninstall_attempt` / `protection.removed` / `device.silent` |
| T-02 | **Force Stop** | ✖ ممکن | ✔ سیستم غیرفعال می‌کند (Force-stop خاکستری) | سرور: شکاف heartbeat بدون دلیل connectivity · کلاینت در launch بعدی: `app_restart_count++` + forensic diff | `service.force_stopped` |
| T-03 | **Clear Data / Cache** | ✖ ممکن | ✔ مسدود توسط سیستم | کلاینت: SecureStore خالی ولی Keystore key موجود ⇒ حالت Recovery · سرور: device قدیمی silent + enroll جدید با fingerprint مشابه | `data.cleared_suspected` |
| T-04 | **تغییر Permission بدون ثبت** | ✖ prevent نیست | ✔ سیاست‌های DO برخی را قفل می‌کند | `AppOpsManager.startWatchingMode()` (رسمی) + SnapshotDiffer در هر wake + WatchdogWorker دوره‌ای ⇒ diff | `permission.changed` (severity بر اساس حساسیت) |
| T-05 | **غیرفعال‌کردن حفاظت (Admin/DO)** | n/a | DO قابل غیرفعالسازی توسط کاربر نیست (مگر factory reset) | `onDisableRequested` / `onDisabled` ⇒ رویداد **قبل** از انجام | `protection.disable_requested` / `protection.removed` |
| T-06 | **کشتن کار پس‌زمینه / OEM kill** | ✖ | ✔ DO app معاف از standby/Doze optimization | زنجیره self-rescheduling: اگر Alarm بعدی اجرا نشد ⇒ در launch بعدی گزارش شکاف · سرور: silence analyzer | `background.killed` |
| T-07 | **قطع ارتباط (Airplane / Wi-Fi off عمدی)** | ✖ |  (ارتباط radio در کنترل OS است) | ConnectivityReceiver + سکوت بیش از lease + FCM ping بدون پاسخ | `connectivity.cut` (با `presence_reason`) |
| T-08 | **دستکاری ساعت دستگاه** | ✖ | ✔ تا حدی (policy) | `ACTION_TIME_CHANGED` / `TIMEZONE_CHANGED` + skew با `server_time` | `time.tampered` |
| T-09 | **دستکاری تنظیمات/Policy والد** | n/a (تنظیمات server-authoritative است) | ✔ DO policyها در boot و reconcile دوره‌ای **re-assert** می‌شوند | version mismatch بین `settings_version` دستگاه و سرور ⇒ reconcile + رویداد | `policy.mismatch` |
| T-10 | **Safe Mode / boot loop برای فرار** | ✖ | ✔ DO app در boot با `BOOT_COMPLETED` بالا می‌آید؛ safe mode اجرای DO را متوقف نمی‌کند (system-bound) | boot marker در Room؛ اگر boot دیده شد ولی worker نه ⇒ رویداد | `boot.evasion_suspected` |
| T-11 | **Root / Hook / Emulator** | فقط تشخیص | فقط تشخیص + Play Integrity | بررسی‌های read-only (build tags، su paths، tracerpid، hook frameworks) + **Play Integrity API** (رسمی، Phase 10) | `integrity.root_suspected` / `integrity.failed` |
| T-12 | **Downgrade / نصب APK قدیمی** | ✖ | ✔ DO می‌تواند install/uninstall را restrict کند | بررسی `versionCode < last_known` در heartbeat | `app.downgrade` |
| T-13 | **Factory Reset** | ✖ | ✖ (DO با reset پاک می‌شود) | پس از reset: device کاملاً جدید ⇒ device قبلی در سرور `silent` می‌ماند و والد هشدار می‌گیرد | `device.silent` + `device.factory_reset_suspected` (هیوریستیک) |
| T-14 | **VPN/DNS filtering (در Phase 7 جاری پیاده نشده)** | خارج از scope | خارج از scope | هیچ VPN service یا resolver در پروژه نیست؛ مسیر موجود فقط managed Chrome DPM policy است | status فقط unsupported می‌شود؛ هیچ `webfilter.disabled` telemetry ادعا نمی‌شود |

**قاعده خروجی:** هر رویداد TAMPER شامل چهار فیلد درخواستی شماست: `event_type` · `timestamp (server)` · `device_id` · `status (new/ack/resolved/false_alarm)` + `severity` + `evidence` (redacted).

---

## ۳. موتور تشخیص سمت Child (بدون هیچ exploit)

```
service/tamper/
├── GuardianDeviceAdminReceiver.kt   # hooks رسمی: onDisableRequested/onDisabled/onEnabled
├── WatchdogWorker.kt                # WorkManager periodic (15m): IntegritySnapshot diff
├── IntegritySnapshotter.kt          # snapshot: permissions, admin/DO state, install_id,
│                                    #   versionCode, time_skew, root indicators, settings_version
├── AppOpsWatcher.kt                 # AppOpsManager.startWatchingMode (رسمی) برای ops حساس
├── EventReceivers.kt                # BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_CHANGED,
│                                    #   TIMEZONE_CHANGED, POWER_CONNECTED/DISCONNECTED,
│                                    #   BATTERY_LOW/OKAY, USER_PRESENT, CONNECTIVITY
├── LaunchForensics.kt               # در هر start: مقایسه last-state Room با واقعیت
│                                    #   ⇒ force-stop/data-clear/boot-evasion را بازسازی می‌کند
├── TamperEventQueue.kt              # Outbox رمزنگاری‌شده + dedupe + severity-based priority
└── DeviceOwnerReceiver.kt           # DeviceAdminReceiver subclass برای DO provisioning
```

**سمت سرور — Silence Analyzer:**
```
هر device: lease(20m) + FCM-ping probe (وقتی والد dashboard را باز می‌کند)
اگر: heartbeat gap > lease AND بدون رویداد connectivity  ⇒ device.silent (TAMPER/warning)
اگر: gap > lease AND enroll جدید با (model+brand+family) در ≤ 24h  ⇒ data.cleared/reinstall suspected
اگر: admin onDisabled دیده شد و سپس gap  ⇒ uninstall_attempt (critical)
```

---

## ۴. Prevention رسمی — چه چیزی را کجا اجرا می‌کنیم

| کنترل | API رسمی | mode لازم | Phase |
|---|---|---|---|
| مسدودسازی حذف اپ | DO provisioning (سیستم خودکار block می‌کند) | M2 | P1 (receiver + guide) / P10 (wizard کامل) |
| مسدودسازی Force-stop / Clear data | ذات DO | M2 | P1 |
| Block/Restrict واقعی اپ | `DevicePolicyManager.setPackagesSuspended()` / `setApplicationHidden()` | M2 | P6 |
| محدودیت‌های سیستمی | `addUserRestriction(DISALLOW_INSTALL_APPS / DISALLOW_USB_FILE_TRANSFER / DISALLOW_SAFE_BOOT / DISALLOW_DEBUGGING_FEATURES …)` | M2 | P6/P10 |
| Kiosk / screen pinning برای حالت مدرسه | `startLockTask()` + `setLockTaskFeatures()` | M2 | P10 |
| سیاست رمز/صفحه قفل | `setPasswordQuality()` … | M2 | P10 |
| Re-assert policy بعد از boot | `BOOT_COMPLETED` + reconcile worker | M2 | P6 |
| Early-warning حفاظت | `DeviceAdminReceiver` callbacks | M1 | **P1** |
| Watchdog/detection | WorkManager + AlarmManager + receivers + AppOps watch | M0 | **P1** |
| Attestation | **Play Integrity API** (رسمی) | M0 | P10 |

### ۴.۱ Provisioning رسمی Device Owner (سه مسیر، همه رسمی)

**مسیر A — QR Zero-Touch (دستگاه نو یا factory-reset):** در Setup Wizard اندروید، QR با JSON استاندارد:
```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME":
    "com.guardianlink.child/.tamper.DeviceOwnerReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME": "com.guardianlink.child",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION":
    "https://dl.guardianlink.app/child-0.1.0.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": "sha256:…",
  "android.app.extra.PROVISIONING_SKIP_USER_CONSENT": false,
  "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE":
    {"pairing_code":"7QK4M2XP","server":"https://api.guardianlink.app"}
}
```
⇒ اپ نصب و **به‌صورت اتمیک** DO می‌شود و pairing code از همان QR خوانده می‌شود. (والد این QR را از اپ Parent می‌سازد.)

**مسیر B — ADB (دستگاه موجود، بدون حساب Google login‌شده):**
```
adb shell dpm set-device-owner com.guardianlink.child/.tamper.DeviceOwnerReceiver
```
راهنمای گام‌به‌گام تصویری در اپ Parent («حفاظت پیشرفته») + پیش‌نیازها و خطاهای رایج (`"Not allowed to set device owner"` یعنی حساب موجود است ⇒ باید حذف حساب شود یا مسیر A).

**مسیر C — Android Management API (AMA):** برای ناوگان/مدارس؛ policy از cloud اعمال می‌شود؛ در Phase 10 به‌صورت integration جدا (نیاز به سرور enrollment) مستند و پیاده می‌شود.

**چرا Family Link نه؟** API عمومی برای third-party ندارد؛ وابستگی به محصول رقیب/Google-only است. ما الگوی UX آن را وام می‌گیریم ولی مستقل می‌مانیم.

### ۴.۲ UX پیشنهاد mode در اپ Parent
پس از Pairing: کارت «سطح حفاظت: پایه (M0)» + دکمه «فعال‌سازی حفاظت کامل (M2)» ⇒ ویزارد تشخیص می‌دهد کدام مسیر A/B/C ممکن است (بررسی account، build، adb connectivity) و راهنمای دقیق می‌دهد. تا قبل از آن، همه‌چیز در M0 کار می‌کند (مانیتورینگ کامل + تشخیص tamper).

---

## ۵. بازیابی رسمی پس از Tamper (Recovery Pairing)

وقتی در M0 داده اپ پاک/حذف شده و فرزند با والد توافق می‌کند برگردد:
1. سرور device قدیمی را در وضعیت `tamper_suspected` نگه می‌دارد (تاریخچه حفظ می‌شود).
2. والد «کد بازیابی» می‌سازد که **به همان `device_id` قدیمی bound** است.
3. Child با کد بازیابی ⇒ `POST /pairing/exchange {recovery_for: device_id}` ⇒ credential جدید روی همان رکورد ⇒ تاریخچه/تنظیمات/consentها حفظ می‌شوند + رویداد `device.recovered`.
4. اگر والد تأیید نکند ⇒ رکورد قدیمی پس از ۳۰ روز anonymize.

**نکته Keystore:** کلیدهای Android Keystore پس از Clear Data باقی می‌مانند (ولی ciphertext داخل DataStore پاک می‌شود) ⇒ ما secret را بازسازی نمی‌کنیم؛ مسیر رسمی recovery بالا را داریم. در M2 اصلاً Clear Data غیرممکن است.

---

## ۶. چه چیزهایی **واقعاً غیرممکن** است (صادقانه)

| مورد | چرا |
|---|---|
| جلوگیری از uninstall / force-stop / clear-data در M0 | سیاست اصلی Android: کاربر مالک دستگاه است؛ هیچ API رسمی برای اپ ثالث وجود ندارد |
| جلوگیری از Factory Reset | reset تمام stateها از جمله DO را پاک می‌کند (فقط FRP حساب Google باقی می‌ماند) |
| پنهان‌کردن کامل VPN | در Phase 7 VPN پیاده نشده است؛ مسیر جاری فقط سیاست Chrome مدیریت‌شده با DPM است. اگر در آینده VPN اضافه شود، رضایت رسمی و disclosure الزامی است؛ هیچ تلاش برای پنهان‌کردن notification سیستم مجاز نیست |
| جلوگیری از DoH سخت‌کد شده در برخی مرورگرها (فیلتر DNS) | محدودیت ذاتی DNS filtering |
| اجرای دقیق‌تر از ~۱۵ دقیقه برای WorkManager در Doze بدون FGS | سیاست باتری OS ⇒ طراحی ما push-verified presence است (سند ۱۲) |
| دسترسی به محتوای پیام‌ها/تماس‌ها/عکس‌ها | هرگز در scope نیست (policy + قانون + اخلاق) |

---

## ۷. انطباق با Google Play Policies

| تکنیک | وضعیت Play | اقدام ما |
|---|---|---|
| Device Admin / Device Owner | مجاز (مدیریت دستگاه رسمی) | declaration معمولی |
| WorkManager/AlarmManager/Receivers | مجاز | — |
| AppOps watch / UsageStats | مجاز با declaratioن special access | فرم‌ها در P10 |
| Play Integrity | سرویس رسمی Google | P10 |
| Accessibility برای enforcement | **ریسک «abuse»** ⇒ **استفاده نمی‌کنیم** (طبق خط قرمز شما) | حذف‌شده از معماری v2 |
| VPN service | مجاز با disclosure؛ نوتیف سیستمی دارد | تصمیم در Phase 7 با افشا |
| پنهان‌کردن اپ / آیکون | ممنوع (deception) | انجام نمی‌دهیم |
| خواندن SMS/Call log | ممنوع برای این کاربرد | انجام نمی‌دهیم |

**استراتژی انتشار:** Phase 1–9 توزیع مستقیم APK (بدون محدودیت Play) برای تست واقعی؛ Phase 10 پرونده Play با فرم‌های declaration. در هر دو مسیر، هیچ تکنیک ممنوعه استفاده نمی‌شود.

---

## ۸. توزیع قابلیت‌های Tamper در Phaseها

| Phase | افزودنی Tamper |
|---|---|
| **P1** | AdminReceiver + WatchdogWorker + LaunchForensics + Silence Analyzer + `tamper_events` + UI والد (Tamper Events) + Recovery Pairing |
| **P2** | AppOpsWatcher برای battery/connectivity-relevant ops + time.tampered |
| **P3/P4** | watch روی usage access و location permissions + `permission.changed` severity map |
| **P6** | DO OwnerEnforcer (suspend/restrictions) + policy re-assert + downgrade check |
| **P7** | `webfilter.disabled` (onRevoke) |
| **P10** | Play Integrity + QR zero-touch wizard + AMA integration + Lock Task (اختیاری) |
