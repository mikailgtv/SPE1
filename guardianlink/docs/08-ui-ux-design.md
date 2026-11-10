# 08 — UI/UX Design System

## ۱. اصول طراحی محصول

| اصل | معنا در عمل |
|---|---|
| **افشا در اتصال، مدیریت با والد (v0.2)** | فرزند در لحظه اتصال می‌بیند چه مواردی فعال می‌شود؛ پس از آن نمای او فقط‌خواندنی است و فعال/غیرفعال/حذف فقط با والد است — فرزند نمی‌تواند نظارت را متوقف کند |
| **یک حقیقت در یک نگاه** | Dashboard باید در ≤ ۲ ثانیه بگوید: آنلاین؟ باتری؟ آخرین sync؟ مشکل مجوز؟ |
| **رنگ کم، معنا زیاد** | فقط ۴ رنگ معنادار (موفقیت/هشدار/خطر/اطلاع) + یک رنگ برند |
| **اقدام قابل دسترس** | هر کارت وضعیت، یک اقدام مستقیم دارد (نه منوی تو در تو) |
| **صادق بودن درباره محدودیت** | badge «دقت کمتر» / «نیاز به مجوز» / «سطح کنترل: بازدارنده» |
| **آرامش، نه ترس** | لحن پیام‌ها: حمایتی و بدون اتهام (نه «فرزند شما تخلف کرد») |
| **RTL درجه یک** | فارسی زبان پیش‌فرض است؛ همه‌جا mirror شده، نه فقط `dir=rtl` |

---

## ۲. Design Tokens (Material 3)

### ۲.۱ رنگ

**Brand**
| Token | Light | Dark |
|---|---|---|
| `primary` | `#3D5AFE` | `#8FA3FF` |
| `onPrimary` | `#FFFFFF` | `#0B1020` |
| `primaryContainer` | `#E4E9FF` | `#1D2750` |
| `secondary` (teal) | `#00A99D` | `#5CD6C8` |

**Semantic (محدود و ثابت در هر دو تم)**
| Token | مقدار | کاربرد |
|---|---|---|
| `success` | `#22C55E` | 🟢 Online · granted · synced |
| `warning` | `#F59E0B` | باتری ۲۰–۳۵٪ · مجوز لازم · stale |
| `danger` | `#EF4444` | Offline · باتری < ۲۰٪ · SOS · revoked |
| `info` | `#38BDF8` | اطلاع‌رسانی خنثی |
| `neutral` | `#94A3B8` | unavailable · disabled |

**Surface (Dark)**
`background #0B0D12` · `surface #12151C` · `surfaceContainer #171B24` · `surfaceContainerHigh #1E232E` · `outline #272C38` · `onSurface #E6E9EF` · `onSurfaceVariant #A7AEBE`

**Surface (Light)**
`background #F6F7FB` · `surface #FFFFFF` · `surfaceContainer #F0F2F7` · `outline #DCE0E9` · `onSurface #111827` · `onSurfaceVariant #5B6474`

**قواعد:**
- Dynamic Color (Material You) فقط در Dark/Light به‌عنوان گزینه؛ پیش‌فرض = پالت برند (تا هویت بصری حفظ شود).
- کنتراست متن/زمینه ≥ ۴.۵:۱ (WCAG AA)، برای متن بزرگ ≥ ۳:۱.
- رنگ **هرگز** تنها حامل معنا نیست ⇒ همیشه آیکون/متن همراه (برای کوررنگی).

### ۲.۲ تایپوگرافی

| زبان | فونت | fallback |
|---|---|---|
| فارسی | **Vazirmatn** (وزن‌های 400/500/700) | `system-ui`, `Tahoma` |
| انگلیسی | **Inter** | `Roboto` |
| اعداد | **رقم‌های فارسی در locale `fa`** و لاتین در `en` (قابل تنظیم در Settings) | — |

**Type scale (M3)**
| نقش | size/line | weight | کاربرد |
|---|---|---|---|
| `displaySmall` | 36/44 | 700 | درصد باتری بزرگ، زمان Screen Time |
| `headlineMedium` | 28/36 | 700 | عنوان صفحه pairing |
| `titleLarge` | 22/28 | 700 | Top bar |
| `titleMedium` | 16/24 | 500 | عنوان کارت |
| `bodyLarge` | 16/24 | 400 | متن اصلی |
| `bodyMedium` | 14/20 | 400 | توضیحات |
| `labelLarge` | 14/20 | 500 | دکمه‌ها |
| `labelSmall` | 11/16 | 500 | badge، `آخرین sync` |

### ۲.۳ فاصله، گوشه، سایه

| Token | مقدار |
|---|---|
| spacing | `4, 8, 12, 16, 20, 24, 32` (grid 4dp) |
| screen padding | `16dp` (افقی)، `12dp` بین کارت‌ها |
| corner card | `20dp` · button `14dp` · chip `10dp` · sheet `28dp` |
| elevation | فقط سایه نرم در Light؛ در Dark از **border `1dp outline`** به‌جای سایه |
| icon size | `20dp` (inline), `24dp` (action), `40dp` (empty state) |
| touch target | حداقل `48×48dp` |

### ۲.۴ اجزای اصلی (از Phase 1 در `core:designsystem`)

| کامپوننت | توضیح |
|---|---|
| `AppCard` | کارت استاندارد با عنوان/آیکون/اقدام اختیاری |
| `StateView<T>` | رندر `Loading/Success/Empty/Error/Offline` — **یک API برای همه صفحه‌ها** |
| `StatusDot` | 🟢🟡🔴⚪ با pulse animation برای «در حال اتصال» |
| `BatteryPill` | آیکون باتری + درصد + رنگ معنادار + `charging` |
| `LastSyncRow` | «آخرین همگام‌سازی: ۲ دقیقه پیش» (relative time دوزبانه) |
| `PermissionChip` | `granted/denied/not_requested/unavailable` با رنگ و آیکون |
| `QuickActionTile` | کاشی مربع با آیکون + برچسب + badge «به‌زودی» |
| `InfoRow` | کلید/مقدار برای Device Info |
| `ConfirmDangerDialog` | دیالوگ حذف با تایپ نام/hold-to-confirm |
| `EmptyStateView` | تصویر SVG ساده + متن + اقدام |
| `SegmentedTab`, `CountdownRing`, `ConsentCard`, `SosButton` (Phase 8) |

### ۲.۵ آیکون‌ها
Material Symbols Rounded (وزن ۴۰۰، grade ۰، optical size ۲۴). آیکون‌های سفارشی برای: خانه، مدرسه، سپر، SOS، QR.

---

## ۳. معماری ناوبری

### ۳.۱ Parent App
```
Root (Splash → Auth gate)
└── ParentNavHost
    ├── auth/            (login, register, forgot)        ← بدون bottom bar
    ├── onboarding/      (intro 3 اسلاید + ساخت خانواده)   ← بدون bottom bar
    └── main/            ← Bottom Navigation (۴ تب)
        ├── home         Dashboard
        ├── devices      DeviceList → DeviceDetail → {Permissions, Info, Sessions, Timeline}
        ├── alerts       AlertsCenter → AlertDetail (شامل Tamper Events)
        ├── notify       NotificationSettings (Toggle per-type + quiet hours + digest + تست)
        ├── audit        AuditLog + TamperEvents (فیلتر severity/status + ack + export)
        └── settings     Settings → {Account, Security, Language, Theme, Server(dev), About}
    Modal routes: pairing (bottom sheet تمام‌صفحه), deviceRemoveConfirm
```

### ۳.۲ Child App
```
Root (Splash → Pairing gate)
├── unpaired/
│   ├── welcome          «این اپ چیست»
│   ├── consent          چه چیزی به اشتراک گذاشته می‌شود
│   └── pair             (enter-code | scan-qr) → connecting
└── paired/  (Bottom Navigation ۳ تب)
    ├── status           وضعیت اتصال + باتری + آخرین sync + سلامت مجوزها
    ├── privacy          نمای فقط‌خواندنی حریم خصوصی و مجوزها (بدون دکمه کنترل)
    └── me               نام/آواتار، راهنمای مجوزها، درباره اپ
    Modal: sos (از Phase 9، دکمه داخل صفحه — نه شناور، نه نوتیفیکیشن)
    قانون v2: **صفر Notification** — هیچ FGS و هیچ فراخوانی NotificationManager در کل codebase کودک
```

**قانون:** Predictive back (API 36) با `BackHandler`؛ هیچ `onBackPressed` دستی. Deep linkها فقط `gl1://pair` (Child) و `gl1://device/{id}` (Parent، از نوتیف).

---

## ۴. RTL و دوزبانگی

| مورد | راه‌حل |
|---|---|
| جهت | `CompositionLocalProvider(LocalLayoutDirection provides …)` بر اساس locale + `android:supportsRtl="true"` |
| منابع | `values/strings.xml` (en پیش‌فرض فنی) + `values-fa/strings.xml` + `values-b+fa+IR/` |
| آیکون‌های جهت‌دار | flip خودکار (arrow, back)؛ آیکون‌های بدون جهت (battery, home) flip نمی‌شوند |
| اعداد | `NumberFormat` با locale؛ گزینه «رقم فارسی/لاتین» در Settings |
| تاریخ/زمان | `java.time` + `DateTimeFormatter` locale-aware؛ relative time («۲ دقیقه پیش» / «2 min ago») |
| مخلوط فارسی/لاتین | `BidiFormatter` برای فیلدهایی مثل email و package name |
| فونت | Vazirmatn از assets با `FontFamily` در Compose (بدون نیاز به دانلود) |
| تست | UI test با `locale=fa` و `en` برای هر صفحه اصلی + screenshot test |

**قاعده متن‌ها:** همه رشته‌ها در `strings.xml` با `key` معنادار + `plurals` برای شمارش + بدون hardcode در Composable. لحن: فارسی محترمانه و ساده («دستگاه سارا ۲ دقیقه پیش به‌روز شد»).

---

## ۵. حالت‌های UI (پاسخ به بند ۲۰ پرامپت)

هر صفحه **دقیقاً** این پنج حالت را دارد و با `StateView` رندر می‌شود:

| حالت | ظاهر | نمونه پیام (fa) |
|---|---|---|
| `Loading` | Skeleton shimmer (نه spinner خالی) | — |
| `Success` | محتوا | — |
| `Empty` | تصویر ساده + متن + CTA | «هنوز دستگاهی متصل نکرده‌اید» + «افزودن دستگاه» |
| `Error` | آیکون + پیام انسانی + «تلاش دوباره» + `request_id` در جزئیات (برای پشتیبانی) | «اتصال به سرور برقرار نشد» |
| `Offline` | banner بالای صفحه + داده کش‌شده با badge «قدیمی» + «تلاش دوباره» | «آفلاین هستید — داده‌ها مربوط به ۵ دقیقه پیش است» |

**قواعد:** هیچ‌وقت صفحه خالی سفید نشان داده نمی‌شود · هیچ‌وقت crash در قطع اینترنت · pull-to-refresh در همه لیست‌ها · optimistic UI با rollback برای rename/remove.

---

## ۶. صفحه‌های Phase 1 — Wireframe متنی

### ۶.۱ Dashboard (والد) — مطابق بند ۴ و ۱۶ پرامپت

```
┌─────────────────────────────────────────┐  ← TopBar: «خانواده رضایی» + آواتار + ⚙
│ 🟠 آفلاین هستید — داده‌ها ۵ دقیقه پیش    │  ← فقط در حالت Offline
├─────────────────────────────────────────┤
│ ┌─────────────────────────────────────┐ │
│ │ ⭐ سارا            🟢 آنلاین        │ │  ChildStatusCard
│ │ گوشی سارا · Pixel 8 · Android 16    │ │
│ │                                     │ │
│ │  🔋 ۷۴٪          ⏱ —               │ │  ← battery pill + screen time (P2: «—» تا فعال‌سازی)
│ │  📶 Wi-Fi        🔄 ۲ دقیقه پیش     │ │
│ │  📍 مکان: غیرفعال  🛡 مجوزها: ۲/۵   │ │
│ │                                     │ │
│ │  [ جزئیات دستگاه ]   [ به‌روزرسانی ] │ │
│ └─────────────────────────────────────┘ │
│ ┌─────────────────────────────────────┐ │
│ │ فعالیت امروز            ۶۵٪ ▓▓▓▓░░ │ │  ← در P1: «به‌زودی در Phase 2» (ساختار آماده)
│ └─────────────────────────────────────┘ │
│  دسترسی سریع                            │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐            │
│  │ 📍 │ │ ⏱ │ │ 📱 │ │ 🏠 │            │  Location/ScreenTime/Apps/Geofence
│  │مکان│ │زمان│ │برنامه│ │Geofence│        │  badge «به‌زودی»/`NEEDS_MODE` تا فعال شدن
│  └────┘ └────┘ └────┘ └────┘            │
│  ┌────┐ ┌────┐                          │
│  │ 🚨 │ │ ⚙ │   Alerts + Settings      │
│  └────┘ └────┘                          │
│  Recent Events                           │
│  • 🔋 Battery Low               ۱۲ دقیقه │
│  • 📍 Location Event             ۲۵ دقیقه │
│  • 🛡 Permission Changed          ۱ ساعت │
│  • 🚨 Tamper Attempt (admin off)  ۲ ساعت │
├─────────────────────────────────────────┤
│   🏠 خانه   📱 دستگاه‌ها   🚨 هشدارها   ⚙ │  BottomBar (RTL: از راست)
└─────────────────────────────────────────┘
```

### ۶.۲ Device Detail
```
TopBar: «گوشی سارا» + ⋮ (rename / logout / remove)
┌ وضعیت ─────────────────────────────┐
│ 🟢 آنلاین از ۱۲:۳۰ · lease تا ۱۲:۴۲ │
│ 🔋 ۷۴٪ در حال تخلیه · 🌡 ۳۱°C       │
│ 📶 Wi-Fi · 🗄 ۱۸ GB آزاد · ⏳ uptime ۳ روز │
└─────────────────────────────────────┘
┌ مجوزها (۲ از ۵ لازم) ──────────────┐
│ ✅ اعلان‌ها            [تنظیم]       │
│ ✅ بهینه‌سازی باتری    [تنظیم]       │
│ ⚪ Device Admin       [فعال‌سازی]    │
│ ⚪ دسترسی مصرف        [فعال‌سازی]    │
│ ⚪ مکان               [Phase 3]      │
└─────────────────────────────────────┘
┌ اطلاعات دستگاه ────────────────────┐
│ مدل / سازنده / Android / نسخه اپ    │
│ device_id: ***7b2c  · install_id    │
│ paired: ۲۸ سپتامبر ۱۲:۳۵            │
└─────────────────────────────────────┘
┌ رضایت فرزند ───────────────────────┐
│ ✅ وضعیت  ✅ باتری  ✅ اطلاعات  ✅ مجوزها │
│ ⛔ مکان (غیرفعال) — نسخه ۱.۰       │
└─────────────────────────────────────┘
[ خروج از نشست ]  [ حذف دستگاه 🔴 ]
```

### ۶.۳ Pairing (والد)
```
┌ افزودن دستگاه فرزند ────────────────┐
│  پروفایل: ⭐ سارا  [تغییر]           │
│        ┌──────────────┐             │
│        │   ▓▓ QR ▓▓   │             │
│        └──────────────┘             │
│         7QK4 - M2XP                 │
│   [کپی]  [اشتراک‌گذاری]              │
│      ⏳ ۰۷:۴۲ باقی‌مانده             │
│  وضعیت: در انتظار اسکن فرزند …      │
│  ۱ ساخت کد ✓  ۲ اسکن …  ۳ تأیید …   │
│       [کد جدید]  [لغو]              │
└─────────────────────────────────────┘
```

### ۶.۴ Child — صفحه رضایت (قبل از pairing)
```
┌ قبل از اتصال، بخوان 🧒 ─────────────┐
│ سلام سارا! والد تو می‌خواهد از این   │
│ دستگاه مراقبت کند. این‌ها را می‌بیند: │
│  ✅ اینکه گوشی روشن و آنلاین است     │
│  ✅ درصد باتری                       │
│  ✅ مدل دستگاه و نسخه Android        │
│  ✅ وضعیت مجوزهای اپ                 │
│  ⛔ مکان تو (فعلاً غیرفعال است)      │
│  ⛔ برنامه‌هایی که استفاده می‌کنی     │
│ مدیریت این موارد و حذف دستگاه        │
│ فقط با والد توست.                    │
│      [می‌پذیرم و وصل می‌شوم]         │
│   [می‌خواهم اول با والد صحبت کنم]    │
└─────────────────────────────────────┘
```

### ۶.۵ صفحه‌های جدید v2 (والد)

**Tamper Events**
```
TopBar: «رویدادهای حفاظتی» + فیلتر [همه | new | ack | critical]
┌  critical · new ──────────────── ۱۲:۴۴ ┐
│ نوع: protection.removed                 │
│ دستگاه: گوشی سارا · mode: M1            │
│ شرح: Device Admin غیرفعال شد —          │
│      احتمال تلاش برای حذف اپ            │
│ evidence: {source:'admin_receiver'}     │
│            [ تأیید (ack) ] [ بررسی ]    │
└─────────────────────────────────────────┘
┌ ⚠️ warning · ack ───────────────── ۱۱:۰۲ ┐
│ نوع: permission.changed (location)      │ …
```

**Audit Log**
```
TopBar: «Audit Log» + فیلتر category + بازه تاریخ + [export JSON]
[AUTH]    auth.login            success   12:41  actor=parent:***3f9a
[PAIRING] pairing.redeemed      success   12:35  device=***7b2c
[TAMPER]  protection.removed    failure   12:44  device=***7b2c
[SECURITY] signature_mismatch   failure   12:40  reason=ts_skew
```

**Notification Settings** — وایرفریم متنی در [`12-notification-architecture.md`](./12-notification-architecture.md) §2.3 و نسخه گرافیکی در `wireframes/parent-app.html` (فریم ۱۴).

---

## ۷. Micro-interactions و Motion

| مورد | مشخصات |
|---|---|
| تغییر وضعیت presence | crossfade ۲۰۰ms + pulse سبز یک‌بار |
| pull-to-refresh | M3 default، ۳۰۰ms |
| صفحه pairing موفق | تیک با spring (۴۰۰ms) + haptic `Success` |
| باتری کم | رنگ به `warning`→`danger` با انیمیشن ۲۵۰ms + haptic |
| حذف دستگاه | hold-to-confirm ۱.۵s با پیشرفت دایره‌ای (ضد حذف تصادفی) |
| SOS (P8) | hold ۳s + vibration pattern + شمارش معکوس قابل لغو |
| Motion | همه انیمیشن‌ها `MotionDurationShort/Medium`؛ با `Reduce motion` سیستم غیرفعال می‌شوند |

## ۸. دسترس‌پذیری (A11y)

TalkBack labels برای همه آیکون‌ها · `contentDescription` دوزبانه · touch target ≥ 48dp · پشتیبانی font scale تا ۲۰۰٪ بدون برش متن · کنتراست AA · هیچ اطلاعاتی فقط با رنگ · focus order منطقی در RTL · `liveRegion` برای تغییر وضعیت آنلاین/باتری.

## ۹. Theme و حالت‌ها

`ThemeMode { System, Light, Dark }` در Settings · Dark پیش‌فرض پیشنهادی (مصرف باتری کمتر در OLED) · پشتیبانی Dynamic Color به‌عنوان گزینه · همه رنگ‌ها از `MaterialTheme.colorScheme` (بدون hardcode در featureها).

🖼 **Wireframeهای واقعی (قابل مشاهده در مرورگر):**
[`wireframes/parent-app.html`](./wireframes/parent-app.html) · [`wireframes/child-app.html`](./wireframes/child-app.html)
