# 13 — Android API Matrix, Limitations, Normal vs Managed Device

## ۱. ماتریس APIهای مورد نیاز به تفکیک Phase

### Phase 1 — Auth + Pairing + Dashboard + Anti-Tamper core
| قابلیت | API رسمی | Permission / min API | نکته |
|---|---|---|---|
| Pairing QR scan | CameraX + ZXing (یا ML Kit Barcode) | `CAMERA` · 21+ | fallback: ورود کد |
| QR ساخت والد | ZXing core (encode) | — | payload `gl1://pair` |
| Secure storage | Android Keystore + Tink AEAD + Proto DataStore | — · 23+ (Tink OK روی 26) | بدون EncryptedSharedPreferences (deprecated) |
| Heartbeat/baseline | WorkManager Periodic + AlarmManager chained | — · 26+ | بدون FGS ⇒ بدون نوتیف |
| Wake رویدادی | `BroadcastReceiver` (boot, power, battery, time, user-present, connectivity app-registered) | — | manifest-registered فقط برای boot/power/time |
| FCM wake | firebase-messaging **data-only** | `POST_NOTIFICATIONS` فقط برای Parent · 26+ | Child بدون نوتیف |
| Device Admin early-warning | `DeviceAdminReceiver` | `BIND_DEVICE_ADMIN` · 21+ | اختیاری در Child، توصیه‌شده |
| DO detection | `DevicePolicyManager.isAdminActive/isDeviceOwnerApp` | — | برای Capability Ledger |
| Watchdog | WorkManager + `AppOpsManager.startWatchingMode` | — · 29+ برای unsafeCheckOp | رسمی |
| Battery/DeviceInfo | `BatteryManager`, `Build.*`, `StorageStatsManager`, `SystemClock` | normal/none | — |
| Presence server-side | SSE + FCM ping probe | — | سند ۱۲ |

### Phase 2 — Device Status + Battery + Last Sync
همان لایه‌های P1 + `ACTION_BATTERY_CHANGED` sticky read + `PowerManager.isPowerSaveMode` + `ConnectivityManager.registerDefaultNetworkCallback`.

### Phase 3 — Screen Time
| قابلیت | API | Permission | mode |
|---|---|---|---|
| Daily/app usage | `UsageStatsManager.queryEvents/queryUsageStats` | `PACKAGE_USAGE_STATS` (special access، دستی) | M0 |
| Screen on/off دقیق | `PowerManager` interactive + receiver `ACTION_SCREEN_ON/OFF` (app-registered) | — | M0 |
| Limit/Bedtime enforcement | **M2:** `setPackagesSuspended` / restrictions · **M0:** advisory (فقط رویداد به والد) | DO | M2 / advisory |
| Exact alarm برای bedtime | `AlarmManager.setExactAndAllowWhileIdle` | `SCHEDULE_EXACT_ALARM` (کاربر grant) · 31+ | fallback `setWindow` |

### Phase 4 — Location
| قابلیت | API | Permission | mode |
|---|---|---|---|
| Current/last-known | `FusedLocationProviderClient.getCurrentLocation/getLastLocation` | `ACCESS_COARSE/FINE` · 23+ | M0 |
| Background updates بدون FGS | `requestLocationUpdates` با **PendingIntent → BroadcastReceiver/Worker** | + `ACCESS_BACKGROUND_LOCATION` (Settings-flow) · 29+ | M0 (رسمی، بدون نوتیف) |
| Battery-aware | adaptive interval + `isPowerSaveMode` + significant-motion | — | M0 |
| History encryption | AES-256-GCM per-family DEK (سرور) + Room type-converter (کلاینت) | — | — |

### Phase 5 — Geofence
`GeofencingClient.addGeofences` + PendingIntent (بدون FGS، بدون نوتیف) · `ACCESS_BACKGROUND_LOCATION` · حداکثر ۱۰۰ zone · dwell/responsiveness تنظیم‌پذیر · fallback محلی (distance compute در wakeها) اگر trigger سیستم نامنظم بود.

### Phase 6 — App Management
| قابلیت | API | mode |
|---|---|---|
| Inventory | `PackageManager.getInstalledApplications` + `QUERY_ALL_PACKAGES` (declaration) | M0 |
| Usage per app | از P3 | M0 |
| Block واقعی | `DevicePolicyManager.setPackagesSuspended()` · `setApplicationHidden()` | **M2 فقط** |
| Restrictions | `addUserRestriction(DISALLOW_INSTALL_APPS, DISALLOW_USB…, DISALLOW_SAFE_BOOT)` | **M2 فقط** |
| Advisory limit | رویداد به والد + policy versioning | M0 |
| ~~Accessibility interstitial~~ | **حذف‌شده در v2** (Accessibility Abuse ممنوع) | — |

### Phase 7 — Web Safety
| گزینه | API | نوتیف؟ | tradeoff |
|---|---|---|---|
| A) Local VPN + on-device DNS filter | `VpnService` | **نوتیف سیستمی VPN** (OS می‌سازد) | فیلتر قوی دامنه؛ مغایرت با «Child بدون نوتیف» ⇒ فقط با افشا و toggle والد |
| B) Private DNS | تنظیم دستی/راهنما (API رسمی ندارد حتی در DO) | ✖ | بدون نوتیف؛ DoH سخت‌کد را رد نمی‌کند |
| C) M2 restrictions | `DISALLOW_CONFIG_PRIVATE_DNS`? (محدود) + safe-search DNS binding | ✖ | رسمی ولی محدود |
**تصمیم در Phase 7 با شما:** پیش‌فرض پیشنهادی = B در M0 و A فقط با رضایت صریح والد + افشا در Child UI.

### Phase 8 — Notifications (Parent-side) — سند ۱۲
### Phase 9 — SOS
FGS **مجاز است؟** خیر — قانون Child-zero-notification شامل SOS هم می‌شود؛ SOS از UI foreground شروع می‌شود (نیاز به FGS ندارد: یک job کوتاه + location one-shot + retry outbox). Parent-side: full-screen intent + channel MAX.

### Phase 10 — Hardening
Play Integrity API · QR zero-touch DO provisioning · AMA integration · Lock Task (اختیاری) · certificate pinning نهایی · KMS · load test.

---

## ۲. محدودیت‌های احتمالی Android (خلاصه v2 + ارجاع)

| محدودیت | اثر | مقابله |
|---|---|---|
| FGS ⇒ نوتیف اجباری | با قانون «Child بدون نوتیف» ناسازگار | **حذف کامل FGS** + لایه‌های wake (سند ۱۲) |
| WorkManager ≥ ۱۵m در Doze | کندی baseline | push-verified presence + رویدادها |
| OEM kill (MIUI/OneUI/…) | کار پس‌زمینه می‌میرد | M2 exemption + boot receiver + chained alarms + silence analyzer |
| Exact alarm deny (31+) | bedtime دقیق نه | `setWindow` + DO grant |
| Background location Settings-flow (29+) | UX مجوز سخت‌تر | ویزارد مرحله‌ای + تشخیص |
| 100 geofence limit | — | اعتبارسنجی domain |
| VPN system notification | تضاد با قانون نوتیف | تصمیم Phase 7 (گزینه B پیش‌فرض) |
| Accessibility policy risk | — | **کلاً استفاده نمی‌کنیم** |
| Factory reset | همه‌چیز پاک | فقط تشخیص + هشدار (سند ۱۱ T-13) |
| Play sensitive permissions | رد شدن | APK-direct تا P9؛ فرم‌ها در P10 |
| Edge-to-edge (35+) / Predictive back (36) | UI | insets + BackHandler از روز اول |

جزئیات کامل‌تر OEM/نسخه‌ها: [`07-android-capabilities-and-limits.md`](./07-android-capabilities-and-limits.md)

---

## ۳. **Normal vs Managed Device** — چه چیزی واقعاً عوض می‌شود (جدول مقایسه رسمی)

| قابلیت | Normal (M0) | Device Admin (M1) | **Device Owner (M2)** | Android Enterprise/AMA (M3) |
|---|---|---|---|---|
| جلوگیری از Uninstall اپ | ✖ | ✖ (فقط early-warning) | ✔ سیستم block | ✔ |
| جلوگیری از Force-stop | ✖ | ✖ | ✔ | ✔ |
| جلوگیری از Clear data/cache | ✖ | ✖ | ✔ | ✔ |
| Block/suspend واقعی اپ | ✖ (advisory) | ✖ | ✔ `setPackagesSuspended` | ✔ |
| Hidden app | ✖ |  | ✔ `setApplicationHidden` | ✔ |
| User restrictions (نصب اپ/USB/safe-boot/debug) | ✖ | ✖ | ✔ | ✔ |
| Lock Task / kiosk | ✖ |  | ✔ | ✔ |
| Password policy | ✖ | ✖ | ✔ | ✔ |
| معافیت battery optimization/standby | ✖ |  | ✔ | ✔ |
| Silent install/uninstall | ✖ | ✖ | ✔ (با package installer مجاز) | ✔ cloud |
| مدیریت از راه دور policy | ✖ (server app-level) | ✖ | ✔ app-level + DO policies | ✔ cloud-first |
| نیاز به provisioning خاص | ✖ | یک tap کاربر | QR (دستگاه نو) یا adb (بدون حساب) | cloud enrollment |
| برگشت‌پذیری | — | کاربر غیرفعال می‌کند | فقط factory reset | admin حذف می‌کند |
| تجربه کاربر فرزند | عادی | عادی | «دستگاه مدیریت‌شده» badge در Settings | مشابه M2 |

**نتیجه محصولی:** همه‌ی «کنترل‌های واقعی» (block، جلوگیری از حذف، kiosk) **فقط در M2/M3** رسمی‌اند. در M0 محصول یک **مانیتورینگ قوی + تشخیص tamper + advisory** است و UI صادقانه همین را می‌گوید. این دقیقاً همان مرزی است که Android و Google Play تعیین کرده‌اند — و ما از آن عبور مخفی نمی‌کنیم.

---

## ۴. راهنمای انتخاب mode per خانواده

| سناریو | پیشنهاد |
|---|---|
| گوشی نو برای فرزند می‌خرید | **مسیر A (QR zero-touch)** ⇒ M2 از اولین روشن‌کردن |
| گوشی فعلی فرزند، بدون حساب Google | **مسیر B (adb)** ⇒ M2 |
| گوشی فعلی با حساب Google فرزند | M0/M1 + راهنمای حذف حساب در صورت تمایل به M2؛ وگرنه مانیتورینگ کامل M0 |
| ناوگان مدرسه | **M3 (AMA)** |
| والد فقط «دید» می‌خواهد نه کنترل | M0 |

---

## ۵. Capability Ledger — برچسب صداقت در UI (پاسخ به «جعل کن ولی بگو»)

هر tile/ردیف در Parent App یک برچسب از این مجموعه دارد:

| برچسب | معنا |
|---|---|
| `REAL` | داده/کنترل واقعی و رسمی در mode فعلی |
| `SYSTEM` | توسط OS اعمال می‌شود (مثل صفحه app-disabled در M2) |
| `ADVISORY` | فقط گزارش/هشدار به والد؛ اجرای فیزیکی نداریم (M0 limits) |
| `NEEDS_MODE` | در mode فعلی ممکن نیست + دکمه «ارتقا به M2» |
| `NEEDS_PERMISSION` | مجوز خاص لازم است + ویزارد |
| `DEMO` | فقط Demo Mode (debug build، واترمارک بزرگ) |

**هیچ‌جا عدد یا وضعیت ساختگی به‌جای داده واقعی نمایش داده نمی‌شود**؛ وقتی داده نیست ⇒ حالت `Empty/Offline/Stale` با برچسب زمانی واقعی.
