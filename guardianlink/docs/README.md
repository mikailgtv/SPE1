# GuardianLink — Phase 0 Design Package (v2)

> **Parental Control for Android** · والد = مدیر · فرزند زیر ۱۸ = دستگاه مدیریت‌شده · **Child App بدون هیچ Notification** · **Anti-Tamper فقط با روش‌های رسمی**
>
> نام موقت: **GuardianLink** · package: `com.guardianlink` (قابل rename در Phase 1)

---

## 📦 فهرست تحویل‌های Phase 0 (v2 — منطبق بر ۱۷ بند درخواست شما)

| # بند شما | فایل |
|---|---|
| 1 معماری سیستم · 2 Tech Stack | [`01-architecture-and-tech-stack.md`](./01-architecture-and-tech-stack.md) |
| 3 Project Structure | [`02-project-structure.md`](./02-project-structure.md) |
| 4 Database Schema | [`03-database-schema.md`](./03-database-schema.md) |
| 5 Backend Architecture | [`01`](./01-architecture-and-tech-stack.md) §3 + [`04`](./04-api-architecture.md) |
| 6 API Architecture | [`04-api-architecture.md`](./04-api-architecture.md) |
| 7 Authentication Flow | [`05-auth-and-pairing.md`](./05-auth-and-pairing.md) |
| 8 Pairing Flow | [`05-auth-and-pairing.md`](./05-auth-and-pairing.md) + [`diagrams/pairing-flow.svg`](./diagrams/pairing-flow.svg) |
| 9 Security Architecture | [`06-security-architecture.md`](./06-security-architecture.md) |
| **10 Anti-Tampering Architecture** | 🆕 [`11-anti-tampering.md`](./11-anti-tampering.md) |
| **11 Notification Architecture** | 🆕 [`12-notification-architecture.md`](./12-notification-architecture.md) |
| 12 UI/UX Structure | [`08-ui-ux-design.md`](./08-ui-ux-design.md) + wireframes |
| **13 Android APIهای مورد نیاز** | 🆕 [`13-android-api-and-modes.md`](./13-android-api-and-modes.md) §1 |
| **14 محدودیت‌های احتمالی Android** | [`13`](./13-android-api-and-modes.md) §2 + [`07`](./07-android-capabilities-and-limits.md) |
| **15 Normal vs Managed Device** | 🆕 [`13`](./13-android-api-and-modes.md) §3-§4 |
| 16 Roadmap کامل | [`09-roadmap.md`](./09-roadmap.md) |
| 17 Test Strategy | [`10-test-plan.md`](./10-test-plan.md) |
| دیاگرام‌ها | [`diagrams/system-architecture.svg`](./diagrams/system-architecture.svg) · [`diagrams/pairing-flow.svg`](./diagrams/pairing-flow.svg) · [`diagrams/auth-token-lifecycle.svg`](./diagrams/auth-token-lifecycle.svg) |
| Wireframes | [`wireframes/parent-app.html`](./wireframes/parent-app.html) · [`wireframes/child-app.html`](./wireframes/child-app.html) |

---

## 🎯 تصمیم‌های کلیدی v2 (تغییرات نسبت به v1 بولد شده‌اند)

| موضوع | تصمیم v2 |
|---|---|
| **Backend** | Ktor 3.6 (Kotlin/JVM 21) + PostgreSQL 17 + Redis 7، Docker Compose، Multi-tenant با ۳ لایه (principal → scoped query → **RLS**)؛ Firebase فقط FCM و پشت `PushProvider`؛ تعویض‌پذیر با `BackendGateway` |
| **🆕 Child بدون Notification** | **حذف کامل Foreground Service از Child.** کار پس‌زمینه = WorkManager + AlarmManager chained + Broadcast receivers + **FCM data-only wake**. Presence = «Push-Verified + Baseline 15m/lease 20m» ⇒ وضعیت والد تقریباً لحظه‌ای بدون هیچ نوتیف روی گوشی فرزند ([سند ۱۲](./12-notification-architecture.md)) |
| **🆕 Anti-Tamper رسمی** | پیشگیری **فقط** با مکانیزم رسمی: Device Owner (QR zero-touch / adb dpm / AMA). در Normal mode فقط **تشخیص + گزارش + بازیابی**. خط قرمزها: بدون root exploit، بدون spyware، بدون **Accessibility Abuse**، بدون دور زدن Play Policy ([سند ۱۱](./11-anti-tampering.md)) |
| **🆕 Enforcement appها** | M2: `setPackagesSuspended`/restrictions (واقعی و رسمی). M0: **advisory** (گزارش به والد) — مسیر Accessibility حذف شد |
| **🆕 Notification والد** | ۸+۲ نوع هشدار با **Toggle جدا** + Quiet Hours + min severity + digest؛ double-gate (سرور+کلاینت)؛ FCM data-only ⇒ نوتیف محلی در Parent ([سند ۱۲](./12-notification-architecture.md)) |
| **🆕 Tamper Events** | جدول `tamper_events` + رویدادهای `[TAMPER]` با چهار فیلد event_type/timestamp/device/status + UI والد (Tamper Events + Audit Log) |
| مدل کنترل فرزند (v0.2 از قبل) | افشا در لحظه اتصال + نمای فقط‌خواندنی؛ توقف/قطع/لغو توسط فرزند وجود ندارد؛ مدیریت ۱۰۰٪ والد |
| Pairing | کد ۸ کاراکتری Crockford Base32 + QR (`gl1://pair`) · TTL 10m · یک‌بارمصرف · ۱۰ تلاش · تأیید دوطرفه در اتصال + **Recovery Pairing** پس از tamper |
| Auth والد | Argon2id + JWT EdDSA 15m + Refresh چرخشی ۳۰ روز با Reuse Detection |
| Auth Child | Device Credential + HMAC-SHA256 هر درخواست (timestamp+nonce+body-hash) |
| Secure storage | Keystore + Tink AEAD + Proto DataStore (بدون EncryptedSharedPreferences deprecated) |
| Realtime | SSE + `Last-Event-ID` + polling fallback |
| minSdk / target | **26 / 36** (compile 36) |
| زبان | کد انگلیسی · UI دوزبانه (fa-IR RTL پیش‌فرض + en) · مستندات فارسی |

---

## 🗺 Phaseها (v2 — مطابق فهرست شما)

| Phase | محتوا | خروجی تست‌پذیر |
|---|---|---|
| **0** | معماری (همین بسته) | تأیید شما |
| **1** | Authentication + Pairing + Dashboard + Anti-Tamper core (watchdog, admin receiver, silence analyzer, tamper UI) | دو گوشی pair می‌شوند؛ dashboard واقعی؛ tamper detect |
| **2** | Device Status + Battery + Last Sync (+ permission watcher) | Test battery/sync/offline/online |
| **3** | Screen Time (usage stats + reports + advisory limits) | اعداد واقعی usage |
| **4** | Location (current/last/background بدون FGS + history) | مکان واقعی با دقت |
| **5** | Geofence (zones + enter/exit + alerts) | trigger واقعی |
| **6** | App Management (inventory/usage + **DO enforcement**) | block واقعی در M2 |
| **7** | Web Safety (Chrome managed URL policies فقط در Device/Profile Owner؛ VPN/DNS/category filtering اجرا نشده) | تنظیم دامنه با وضعیت صادقانه؛ Chrome-only |
| **8** | Notifications Parent (toggles/quiet hours/digest) | هر ۸ نوع با toggle |
| **9** | SOS | ≤ ۱۵s تا نوتیف والد |
| **10** | Security Hardening + Production (Play Integrity, QR DO wizard, AMA, pinning, Play release) | آماده انتشار |

**پروتکل همکاری (بدون تغییر):** پس از هر Phase متوقف می‌شوم و می‌دهم: ۱) کد ۲) ساختار فایل‌ها ۳) نحوه Build ۴) نصب روی دو دستگاه ۵) Test Plan ۶) مشکلات احتمالی ۷) تست‌های خودکار همان Phase ۸) توقف.
پاسخ شما: `APPROVED` · `FIX` · `NEXT PHASE`

---

## ⚠️ چهار حقیقت فنی که باید بدانید

1. **Child بدون Notification یعنی بدون FGS** ⇒ بدون باز کردن Dashboard توسط والد، تأخیر تشخیص offline تا ~۲۰ دقیقه است؛ با باز کردن Dashboard ≤ ~۷۰ ثانیه (push-verified). این tradeoff را پذیرفته‌ایم و در UI صادقانه نشان می‌دهیم.
2. **در Normal mode هیچ «کنترل واقعی» وجود ندارد** (نه block، نه جلوگیری از حذف). کنترل واقعی = **Device Owner** (مسیر رسمی). اپ Parent ویزارد تشخیص مسیر (QR/adb/AMA) را دارد.
3. **Factory reset راه فرار نهایی است**؛ فقط تشخیص + هشدار می‌دهیم.
4. **Phase 7 در این نسخه فیلتر وب سیستمی نیست:** فقط URLBlocklist/URLAllowlist در Chrome روی Device/Profile Owner تنظیم می‌شود؛ DPM readback اثر واقعی Chrome را تأیید نمی‌کند. VPN، DNS resolver/category provider و event بازدید پیاده نشده‌اند. جزئیات و آزمون‌های Pending: [`PHASE7_REPORT.md`](./PHASE7_REPORT.md) و [`PHASE7_DEVICE_TEST_GUIDE.md`](./PHASE7_DEVICE_TEST_GUIDE.md).

---

## ✅ چک‌لیست تأیید Phase 0 (v2)

- [ ] معماری + Tech Stack (01)
- [ ] Project Structure (02)
- [ ] DB Schema + `tamper_events` (03)
- [ ] Backend/API + SSE + tamper/notifications endpoints (04)
- [ ] Auth + Pairing + Recovery Pairing (05)
- [ ] Security + خط قرمزها (06, 11 §7)
- [ ] **Anti-Tampering (11)** ← مهم‌ترین سند جدید
- [ ] **Notification Architecture + Child zero-notification (12)**
- [ ] **API matrix + Normal vs Managed (13)**
- [ ] UI/UX + wireframes به‌روز (08 + HTML)
- [ ] Roadmap 10 فازی (09)
- [ ] Test Strategy (10)
