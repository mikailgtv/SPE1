# گزارش Web Runtime — Phase 27

**وضعیت:** `CLOSED` برای production build، unit tests و مرورگر محلی متصل به API synthetic.

## شواهد

- `npm run build` در اجرای Phase 27 موفق بود؛ TypeScript/Vite production bundle ساخته شد.
- `npm test`: دو test file و ۴ test پاس شدند.
- در ادامه، Vite dev server با `API_PROXY_TARGET=http://127.0.0.1:18080` بالا آمد؛ API proxy سمت سرور و same-origin بود.
- Chromium 154 headless با ChromeDriver 154 و WebDriver واقعی اجرا شد: صفحهٔ ورود باز شد، حساب والد synthetic از API محلی وارد شد و route `/` dashboard را نشان داد. نام family و device که در DOM مشاهده شدند از GETهای backend بودند؛ دادهٔ ثابت/موک برای نمایش dashboard استفاده نشد.
- credentialهای موقت فقط در فایل mode 0600 زیر `/tmp` بودند، در log یا repository چاپ/ذخیره نشدند و پس از آزمون حذف شدند.

## محدودیت

این آزمون مرورگر محلی/API synthetic است؛ staging/production، چند مرورگر/موبایل، cross-origin production، TLS، accessibility کامل، UAT کاربر واقعی و Android WebView را پوشش نمی‌دهد. برای handoff، dev server و backend متوقف شدند و DB fixtureها پاک شدند.
