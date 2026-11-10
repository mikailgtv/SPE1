# 12 — Notification Architecture (v2)

## ۱. قانون طلایی: **اپ Child هیچ Notification نمی‌سازد — اصلاً.**

این یک تصمیم محصولی شماست و معماری را بنیادین عوض می‌کند، چون Android برای **Foreground Service** نوتیفیکیشن دائمی **اجباری** می‌کند و راه رسمی برای حذف آن وجود ندارد. بنابراین:

> **Child App هیچ Foreground Service ندارد.** تمام کار پس‌زمینه با مکانیزم‌های رسمیِ بدون-نوتیفیکیشن انجام می‌شود.

### ۱.۱ جایگزین‌های رسمی FGS (لایه‌های Wake)

| لایه | مکانیزم رسمی | نوتیفیکیشن؟ | کدنس | مصرف باتری |
|---|---|---|---|---|
| L1 | **WorkManager PeriodicWork** | ✖ | ≥ ۱۵ دقیقه (سیاست OS) | بسیار کم |
| L2 | **AlarmManager chained windows** (`setWindow` / `setAndAllowWhileIdle`) | ✖ | هر ۵–۱۵ دقیقه (در Doze با تأخیر رسمی) | کم |
| L3 | **Broadcast receivers رویدادی**: `USER_PRESENT`, `SCREEN_ON/OFF`(interactive), `CONNECTIVITY_CHANGE`(app-registered), `BATTERY_CHANGED/LOW/OKAY`, `POWER_CONNECTED/DISCONNECTED`, `TIME_CHANGED`, `BOOT_COMPLETED` | ✖ | opportunistic | ناچیز |
| L4 | **FCM data-only wake**: سرور پیام data می‌فرستد ⇒ Child بیدار می‌شود و فوراً heartbeat می‌زند | ✖ (data-only) | on-demand | ناچیز |
| L5 | **GeofencingClient / location callbacks با PendingIntent** (Phase 4+) | ✖ | رویدادی | کم |
| L6 | **Device Owner exemptions** (M2): معاف از standby bucket/Doze optimization ⇒ L1/L2 قابل اعتمادتر | ✖ | — | — |

### ۱.۲ مدل Presence بدون FGS — «Push-Verified + Baseline»

مشکل: بدون heartbeat هر ۶۰ ثانیه، Online/Offline کند می‌شود. راه‌حل:

```
Baseline:   Child هر ۱۵ دقیقه (WorkManager) + هر wake رویدادی ⇒ heartbeat ⇒ lease = 20 دقیقه
On-demand:  والد Dashboard را باز می‌کند ⇒ سرور FCM data-ping به Child می‌فرستد
            ├── پاسخ ≤ 10s  ⇒ presence = online (فوری و دقیق)
            └── بدون پاسخ 60s ⇒ presence = offline (فوری، بدون انتظار 20 دقیقه)
Offline confirmation: lease منقضی AND ping بی‌پاسخ ⇒ device.offline + رویداد
```

⇒ **تجربه والد: وضعیت تقریباً لحظه‌ای**؛ **تجربه فرزند: صفر نوتیفیکیشن**؛ **باتری: بهتر از FGS**.
عدد واقعی تأخیر offline در بدترین حالت (بدون باز کردن Dashboard توسط والد): ~۲۰ دقیقه؛ با باز کردن Dashboard: ≤ ۷۰ ثانیه. این اعداد در UI والد صادقانه نمایش داده می‌شوند («آخرین تأیید: X پیش»).

### ۱.۳ استثناهای افشا‌شده (نوتیفیکیشن‌هایی که OS می‌سازد، نه اپ ما)

| مورد | چه زمانی | چه کسی می‌سازد | تصمیم |
|---|---|---|---|
| نوتیفیکیشن سیستمی VPN | در Phase 7 فعلی VPN فعال/پیاده‌سازی نشده؛ مسیر موجود، DPM policy برای Chrome مدیریت‌شده است | **سیستم Android** در صورت VPN آینده | در صورت افزودن آینده، فقط با رضایت رسمی و disclosure؛ هرگز notification سیستم پنهان نشود |
| صفحه سیستمی «App disabled by your admin» | وقتی Child در M2 اپی را suspend کرده و فرزند باز می‌کند | سیستم | مطلوب و رسمی (UX واقعی block بدون نوتیف ما) |
| پیام‌های حین provisioning DO | فقط حین setup یک‌بار | سیستم wizard | قابل قبول (یک‌بار، حین نصب) |
| نوتیفیکیشن‌های Parent | همیشه | اپ Parent (محلی) | طراحی زیر |

**هیچ‌کدام توسط کد Child app ساخته نمی‌شوند** (هیچ `NotificationManager.notify` در codebase_child وجود ندارد — با تست خودکار و lint rule اجبار می‌شود).

---

## ۲. سمت Parent: معماری کامل Notification

### ۲.۱ Pipeline

```
رویداد (device.offline, battery.low, tamper.*, geofence.*, …)
   │  (در همان transaction)
   ▼
outbox table ──► NotifyDispatcher (worker)
   │  1) prefs gate سمت سرور: notification_preferences(user, type).enabled?
   │     quiet_hours? min_severity? dedupe window?
   │  2) اگر push لازم بود: FCM **data-only** به دستگاه‌های Parent
   │  3) status = pushed / muted / in_app_only
   ▼
Parent App
   ├── SSE باز است  ⇒ فقط in-app (badge + Recent Events) — بدون نوتیف تکراری
   └── FCM data رسید ⇒ prefs gate سمت کلاینت (double-gate) ⇒
        NotificationManager.notify(channel per type) با اهمیت درست
```

**Double-gate چرا؟** چون toggle باید حتی وقتی سرور prefs را ندیده (offline) هم رعایت شود، و برعکس اگر کاربر در دستگاه Parent دوم toggle را عوض کرد، سرور هم push نفرستد.

### ۲.۲ کانال‌ها و انواع هشدار (هرکدام Toggle جدا — مطابق درخواست شما)

| # | نوع | channel id | اهمیت پیش‌فرض | Toggle پیش‌فرض | صدا/Vibe | Lockscreen | Dedupe |
|---|---|---|---|---|---|---|---|
| 1 | Device Offline | `ch_device_offline` | HIGH | ON | بله | نمایش | ۳۰m |
| 2 | Low Battery (≤20%) | `ch_battery_low` | DEFAULT | ON | بله | نمایش | ۶۰m |
| 3 | Location Event | `ch_location_event` | DEFAULT | ON | خیر | خصوصی | ۱۵m |
| 4 | Geofence Enter/Exit | `ch_geofence` | HIGH | ON | بله | نمایش | per zone ۱۰m |
| 5 | Screen Time Limit | `ch_screen_time` | HIGH | ON | بله | نمایش | ۶۰m |
| 6 | Permission Changed | `ch_permission` | DEFAULT | ON | خیر | خصوصی | ۳۰m |
| 7 | **Tamper Attempt** | `ch_tamper` | **HIGH** | ON | بله | نمایش | ۵m |
| 8 | **SOS** | `ch_sos` | **MAX + full-screen intent** | ON (غیرقابل خاموش‌شدن کامل: حداقل importance HIGH) | بله (full) | نمایش روی lock | بدون dedupe |
| 9 | Device Unpaired / Removed | `ch_lifecycle` | DEFAULT | ON | بله | نمایش | — |
| 10 | Weekly Digest | `ch_digest` | LOW | OFF | خیر | خصوصی | ۷d |

**تنظیمات اضافه در صفحه Notification Settings والد:** Quiet Hours (بازه + استثنا SOS/Tamper) · حداقل severity · صدا/لرزش per channel · «فقط وقتی اپ بسته است» · per-device override (اختیاری P8) · تست ارسال نوتیف آزمایشی per type.

### ۲.۳ UI — صفحه Notification Settings (Parent)

```
┌ Notification Settings ────────────────┐
│ 🔔 اعلان‌های سیستم اندروید: ✅ مجاز   │ [تنظیمات سیستم]
│                                       │
│ هشدارها                               │
│  📴 Device Offline            [ON ]   │  ↳ اهمیت: High · آخرین: ۲m پیش
│  🔋 Low Battery               [ON ]   │
│  📍 Location Event            [ON ]   │
│  🏠 Geofence Event            [ON ]   │
│  ⏱ Screen Time Limit          [ON ]   │
│  🛡 Permission Changed         [ON ]   │
│  🚨 Tamper Attempt             [ON ]   │  (badge: ۲ رویداد جدید)
│  🆘 SOS                        [ON🔒]  │  «حداقل High — برای ایمنی»
│                                       │
│ ساعات سکوت  23:00 – 07:00  [ON]        │
│   استثنا: SOS, Tamper  ✅              │
│ حداقل اهمیت: Warning به بالا           │
│ خلاصه هفتگی (Digest):      [OFF]       │
│ [ارسال نوتیف آزمایشی]                  │
└───────────────────────────────────────┘
```

### ۲.۴ داده و API

- جدول `notification_preferences` (سند ۰۳): `(user_id, alert_type)` UNIQUE + `enabled, quiet_hours, min_severity, channel_prefs jsonb`.
- `GET/PUT /v1/notifications/preferences` (والد) ⇒ sync بین دستگاه‌های والد.
- `POST /v1/notifications/test {type}` ⇒ push آزمایشی.
- `GET /v1/notifications?status=unread` + `POST /v1/notifications/{id}/read` + `read-all`.
- رویدادهای SSE معادل همان types (برای in-app badge بدون push).
- **Parent هم باید `POST_NOTIFICATIONS` (API 33+) داشته باشد**؛ اگر denied ⇒ banner در اپ: «اعلان‌ها خاموش‌اند — هشدارها فقط داخل اپ دیده می‌شوند» + deep-link به Settings.

### ۲.۵ قابلیت اطمینان

| سناریو | رفتار |
|---|---|
| FCM نرسید (شبکه محدود) | SSE/poll در اپ باز؛ Digest روزانه gaps را پوشش می‌دهد |
| Parent offline بود و بعد آنلاین شد | `GET /notifications?since=` ⇒ badge + «N هشدار در زمان آفلاین» |
| Duplicate (SSE + FCM) | `dedupe_key` در کلاینت ⇒ فقط یک بار notify |
| کاربر channel را در سیستم بست | `areNotificationsEnabled`/channel importance بررسی ⇒ banner راهنما |

---

## ۳. تست‌های مخصوص این معماری

| تست | نوع | معیار |
|---|---|---|
| **Child Zero-Notification** | Instrumentation | پس از ۲۴h شبیه‌سازی چرخه‌ها: `NotificationManager.activeNotifications == 0` و هیچ FGS در `ActivityManager.getRunningServices`/`foregroundServices` |
| Lint rule | CI | هیچ refernce به `NotificationManagerCompat.notify` / `Notification.Builder` در source setهای `:app:child` و `:service:*` child |
| Presence push-verified | E2E دو گوشی | باز کردن Dashboard والد ⇒ online/offline صحیح ≤ ۷۰s |
| Baseline lease | E2E | قطع اینترنت Child بدون باز کردن Dashboard ⇒ offline ≤ ۲۰+۲m |
| Prefs double-gate | Unit+API | toggle OFF در سرور ⇒ push ساخته نمی‌شود؛ toggle OFF لوکال با push رسیده ⇒ notify نمی‌شود |
| Quiet hours | Unit | رویداد غیرمستثنا در بازه ⇒ status=muted |
| SOS full-screen | Instrumentation | روی lock screen والد نمایش داده می‌شود حتی با Doze |
