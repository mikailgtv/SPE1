# 09 — Roadmap v2 (Prototype → MVP → Beta → Production)

## نقشه راه (مطابق فهرست ۱۰ فازی شما)

```
Phase 0  Architecture (همین بسته)                      ◄── تأیید شما
Phase 1  Authentication + Pairing + Dashboard + Anti-Tamper core
Phase 2  Device Status + Battery + Last Sync (+ Permission watcher)
Phase 3  Screen Time
Phase 4  Location
Phase 5  Geofence
Phase 6  App Management (+ Device Owner enforcement)
Phase 7  Web Safety
Phase 8  Notifications (Parent, per-type toggles)
Phase 9  SOS
Phase 10 Security Hardening + Production (+ Play release)
```

| مرحله محصول | فازها | معیار خروج |
|---|---|---|
| **Prototype** | 0 → 1 | دو گوشی واقعی pair می‌شوند؛ Dashboard داده واقعی؛ tamper detect می‌شود |
| **MVP** | 2 → 5 | والد وضعیت، زمان، مکان و منطقه امن را می‌بیند و هشدار می‌گیرد |
| **Beta** | 6 → 9 | کنترل واقعی اپ (M2)، فیلتر وب، نوتیف‌های قابل تنظیم، SOS — روی ۳+ خانواده |
| **Production** | 10 | Play Integrity، QR DO wizard، pinning نهایی، Play Store، backup/restore |

---

## Phase 1 — Authentication + Pairing + Dashboard + Anti-Tamper core

| # | قابلیت | نکته v2 |
|---|---|---|
| 1.1 | Parent Login/Register + Family | Argon2id · JWT 15m · refresh چرخشی + reuse detection |
| 1.2 | Child Setup + افشا در اتصال | صفحه Disclosure فقط‌خواندنی + confirm |
| 1.3 | Pairing (QR + کد ۸ کاراکتری) | TTL 10m · ۱۰ تلاش · SSE وضعیت زنده · **Recovery Pairing** |
| 1.4 | Dashboard والد | ChildStatus + Quick Actions (Location/ScreenTime/Apps/Geofence/Alerts/Settings) + Recent Events (شامل Tamper) |
| 1.5 | Online/Offline | **Push-Verified + Baseline 15m/lease 20m** (بدون FGS) |
| 1.6 | Battery / 1.7 Last Sync / 1.8 Device Info / 1.9 Permission Status | collectorهای واقعی |
| 1.10 | Remove Device + Revoke | revoke + purge + unpair command |
| 1.11 | **Anti-Tamper core** | DeviceAdminReceiver + WatchdogWorker + LaunchForensics + Silence Analyzer + `tamper_events` + UI Tamper Events + Audit Log view |
| 1.12 | زیرساخت | SSE · Outbox · SecureStore · RLS · Audit · Error model · DesignSystem · i18n/RTL · Docker Compose · CI · Notification prefs (جدول + Toggleهای پایه) |

**DoD:** ۸ تست دستی (سند ۱۰ §۴، به‌روزشده برای مدل presence جدید) + تست‌های خودکار سبز + **Child: صفر Notification** (تست instrumentation) + غیرفعال‌کردن Device Admin ⇒ رویداد Tamper در ≤ ۶۰ ثانیه روی Parent.

## Phase 2 — Device Status + Battery + Last Sync
Timeline رویدادها · battery history chart ساده · `permission.changed` watcher با `AppOpsManager.startWatchingMode` · stale badges · DoD: اعداد برابر با واقعیت گوشی + ثبت رویداد تغییر مجوز.

## Phase 3 — Screen Time
`UsageStatsManager` (مجوز special access + ویزارد) · daily/weekly/per-app · reports + نمودار · **advisory limits** در M0 (رویداد `screen_time.limit_reached` به والد) · اجرای واقعی فقط با M2 (زیرساخت policy engine از همین فاز) · DoD: اختلاف ≤ ۵٪ با Digital Wellbeing.

## Phase 4 — Location
current/last-known · background updates **با PendingIntent (بدون FGS)** · history رمزنگاری‌شده + نقشه · battery-aware interval · DoD: مکان واقعی با `accuracy_m` و freshness صادقانه.

## Phase 5 — Geofence
zones (Home/School/Gym/Other, radius 50–2000m, dwell) · `GeofencingClient` + PendingIntent · enter/exit events + alerts · fallback محلی در wakeها · DoD: trigger واقعی با تأخیر مستند Doze.

## Phase 6 — App Management + DO Enforcement
inventory + usage per app · **OwnerEnforcer**: `setPackagesSuspended`/`setApplicationHidden`/`addUserRestriction` · policy re-assert در boot · ویزارد DO provisioning (تشخیص مسیر A/B/C) · downgrade check · DoD: در M2 اپ واقعاً suspend؛ در M0 برچسب `ADVISORY`.

## Phase 7 — Web Safety
پیش‌فرض: **Private DNS guide + SafeSearch DNS binding** (بدون نوتیف) · گزینه VPN local filter **فقط با رضایت صریح والد + افشا در Child UI** (نوتیف سیستمی VPN) · block/allow lists · `web.blocked` events · DoD: فیلتر دامنه واقعی روی مرورگرهای استاندارد.

## Phase 8 — Notifications (Parent)
۸+۲ نوع با Toggle جدا · quiet hours + exceptions · min severity · digest هفتگی · double-gate · `notifications` list + read/unread · تست نوتیف آزمایشی · DoD: هر نوع جدا قابل خاموش‌شدن؛ SOS همیشه ≥ HIGH.

## Phase 9 — SOS
دکمه داخل صفحه Child (hold 3s + شمارش معکوس قابل لغو) · job کوتاه: مکان one-shot + باتری + timestamp · Parent: full-screen intent + channel MAX + acknowledge · DoD: ≤ ۱۵ ثانیه با اینترنت.

## Phase 10 — Hardening + Production
Play Integrity · QR zero-touch DO wizard کامل · AMA integration (اختیاری) · certificate pinning نهایی + KMS · 2FA · backup/restore · crash reporting با redaction · load test ۱۰k دستگاه · پرونده Play (declarations + Data Safety) · web panel اختیاری.

---

## وابستگی‌ها

```
P1 ──► P2 ──► P3 ──► P6 (enforcement از policy engine P3)
 │      └────► P8 (alert types battery/permission)
 ├──► P4 ──► P5 ──► P8 (geofence alerts)
 ├──► P7 (مستقل)
 ├──► P8 (notify hub)
 ├──► P9 (نیاز به P4 برای مکان)
 └──► P10 (همه)
```

## برآورد اندازه

| Phase | ماژول جدید | فایل تقریبی | migration |
|---|---|---|---|
| 1 | ~۴۲ ماژول/بسته | ۱۰۰–۱۳۰ | V1–V3 (+tamper_events, notification_preferences) |
| 2 | collectors + timeline UI | ۱۵–۲۰ | — |
| 3 | feature:screentime + modules/screentime | ۲۵–۳۵ | V4 |
| 4 | feature:location + modules/location | ۲۰–۳۰ | V5 |
| 5 | feature:geofence | ۱۵–۲۰ | V6 |
| 6 | feature:appmgmt + service:enforcement(DO) | ۲۵–۳۵ | V7 |
| 7 | Parent web-safety UI + DPM managed Chrome URL policies; no vpnfilter/DNS provider | ۲۰–۳۰ | V8 |
| 8 | feature:notificationsettings تکمیل + modules/notify | ۲۰–۲۵ | V9 |
| 9 | feature:sos + modules/sos | ۱۲–۱۸ | V10 |
| 10 | hardening | ۳۰+ | V11+ |

---

## ✅ چک‌لیست سراسری هر Phase

- [ ] کد production + تست خودکار (Unit/VM/Repo/API)
- [ ] migration جدید backward-compatible
- [ ] هر ۵ حالت UI (Loading/Success/Empty/Error/Offline)
- [ ] logging با تگ درست + redaction + `request_id`
- [ ] audit event برای اقدام‌های حساس (+ TAMPER در صورت ارتباط)
- [ ] SSE event + (در صورت لزوم) FCM data-only
- [ ] **Child: هیچ Notification جدیدی اضافه نشود** (lint + تست)
- [ ] Capability Ledger برچسب‌ها صادقانه (REAL/SYSTEM/ADVISORY/NEEDS_MODE/NEEDS_PERMISSION/DEMO)
- [ ] دوزبانه fa/en + RTL
- [ ] `docs/testing/phase-N.md`
- [ ] **توقف و انتظار: APPROVED / FIX / NEXT PHASE**
