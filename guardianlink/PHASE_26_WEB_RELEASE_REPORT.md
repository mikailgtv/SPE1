# گزارش Web Release فاز ۲۶

**تاریخ بررسی:** ۷ اکتبر ۲۰۲۶  
**محدوده:** بازبینی مخزن و ابزارهای همین workspace؛ هیچ دسترسی به حساب cloud، production، staging، DNS، credential یا سرویس بیرونی وجود نداشت. پیکربندی/سند به‌تنهایی اثبات آمادگی نیست.
## اجرای واقعی

در `/home/user/guardianlink/web`: اولین `npm run build` به دلیل نبود `node_modules/tsc` اجرا نشد. سپس `npm ci --ignore-scripts` با موفقیت 161 package نصب کرد و `npm run build` موفق شد: TypeScript و Vite 7.3.6، 90 modules. `dist` شامل `index.html` 709 byte، JS 385,929 byte، CSS 35,460 byte، manifest/service worker/offline page است. SHA-256: index `5e4e7da0749a1aed6bbe02125a448f3ea14d4c04ba9e2a723b5ae1b58ec7854c`; JS `aaa15bde152d3430d4d49417ed9167d4a218d30a00dbd607b9a39c16a77ea83c`; CSS `225037313f775a7fdfc69381da380abcf891fee175943fb2e96c293d2a5aa5a2`.

`npm ci` 0 vulnerabilities reported by npm; this is not full supply-chain attestation. Version `0.1.0-rc.1`. Production environment/API same-origin host, feature flags, debug/source-map hosting/CDN, CSP/header deployment were not assigned/verified. Build source contains production SPA fallback and Caddy policy config, but no served production web server. Backend 8080 closed. Browser login, UAT critical path, and web security headers **BLOCKED/NOT_TESTED**. Build PASS only; not release ready.
