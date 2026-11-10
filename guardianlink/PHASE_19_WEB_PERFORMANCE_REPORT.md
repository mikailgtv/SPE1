# گزارش کارایی Web — فاز ۱۹

**وضعیت:** build artifact اندازه‌گیری شد؛ browser runtime `BLOCKED_BY_ENVIRONMENT`.

## خط مبنای واقعی

پیش از تغییر کد، `npm ci --ignore-scripts && npm run build` موفق شد؛ Vite 7.3.6، 90 ماژول، 2.66 s زمان build.

| artifact | خام | gzip |
|---|---:|---:|
| HTML | 0.71 kB | 0.46 kB |
| CSS | 35.46 kB | 7.61 kB |
| JS | 380.27 kB | 116.14 kB |

زمان build زمان دریافت/اجرای مرورگر نیست. Chromium/اتوماسیون موجود نبود؛ cold/warm load، LCP/INP/CLS، memory، API waterfall و خطاهای console همگی `NOT MEASURED` هستند.

## بررسی ایستای منبع

- React Query polling: health/unread حدود هر 60 ثانیه و active SOS هر 30 ثانیه تعریف شده است. رفتار پس‌زمینهٔ tab، tabهای متعدد و بار واقعی آزموده نشد.
- در صفحه SOS، history برای هر دستگاه جداگانه fetch می‌شود؛ تعداد درخواست‌ها با تعداد دستگاه‌ها رشد می‌کند. این نامزد بهینه‌سازی است، نه bottleneck اندازه‌گیری‌شده.
- در مسیرهای فهرست، location و اعلان‌ها limitهای محدود در درخواست/منبع مشاهده شدند. سایز واقعی پاسخ و زمان API معلوم نیست.
- bundling Vite build موفق است؛ هیچ تغییر split/chunk، lazy loading یا dependency اعمال نشد چون browser profiling و re-measure در دسترس نیست.

## بودجه و مقایسه

هدف موقت: JS ابتدایی gzip ≤ 200 KiB؛ Web LCP p75 ≤ 2.5 s، INP p75 ≤ 200 ms و CLS ≤ 0.1 در مرورگر/دستگاه/شبکهٔ ثبت‌شده. معیار زمان از دید کاربر هنوز اندازه‌گیری نشده. Baseline bundle در build tool با `kB` گزارش شده؛ تبدیل واحد و gate CI باید یکدست شود.

| مورد | قبل | بعد | نتیجه |
|---|---|---|---|
| JS gzip artifact | 116.14 kB | `NOT MEASURED` | بدون تغییر، مقایسه ندارد |
| first load / LCP / INP / CLS | `NOT MEASURED` | `NOT MEASURED` | مرورگر موجود نیست |
| API waterfall / render / memory | `NOT MEASURED` | `NOT MEASURED` | ثبت نشده |

## ادامه

در محیط مرورگر آزمایشی و API staging، trace شبکه و Web Vitals با cache cold/warm و device/network profile مشخص ثبت شود. دادهٔ احراز هویت/حریم خصوصی از HAR و trace حذف شود. سپس فقط bottleneck اثبات‌شده اصلاح و build و مرورگر دوباره سنجیده شوند.
