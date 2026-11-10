# راهنمای راه‌اندازی Web Parent Dashboard

## پیش‌نیاز

- Node.js 20+ و npm (در این workspace: Node 20.20.2، npm 10.8.2)
- برای اتصال واقعی: همان Ktor/PostgreSQL پروژهٔ GuardianLink و حساب Parent که family دارد.
- این frontend سرور یا دیتابیس جدید نمی‌سازد.

## اجرای Web

```bash
cd web
cp .env.example .env
npm ci
npm test
npm run build
npm run dev
```

Vite با پیش‌فرض `API_PROXY_TARGET=http://127.0.0.1:8080` مسیرهای `/v1` و `/health` را از process سرور dev به backend موجود proxy می‌کند. مرورگر فقط URL نسبی همان origin را می‌بیند. اگر API روی host/port دیگری است، مقدار **server-only** `API_PROXY_TARGET` را در `web/.env` یا environment تغییر دهید؛ این متغیر با `VITE_` prefix ندارد و وارد bundle مرورگر نمی‌شود. برای stop کردن preview، process را متوقف کنید.

`VITE_API_BASE_URL` اختیاری و فقط یک path نسبی same-origin است؛ مقدار absolute یا protocol-relative رد می‌شود تا bearer token به host دیگری ارسال نشود. در deploy استاندارد خالی بماند.

برای مشاهدهٔ بدون حساب، در برنامه مسیر `/preview` را باز کنید؛ این فقط پیش‌نمایش ایستای رابط است (بدون API، داده یا عملیات تغییردهنده). ورود واقعی همچنان الزامی است تا اطلاعات/کنترل‌های family نمایش داده شوند.

اگر backend محلی با Compose پروژه اجرا می‌شود، مطابق `ENVIRONMENT_GUIDE.md` و `deploy/.env.example` همان stack موجود را آماده کنید، سپس از `deploy/docker-compose.dev.yml` برای bind محلی `8080` استفاده کنید. این workspace ابزار Docker/Gradle/PostgreSQL ندارد؛ این اجرا اینجا انجام نشده است.

## یک origin برای Web و API

در deployment موجود، پس از ساخت frontend:

```bash
cd web
npm ci
npm run build
cd ../deploy
# پس از تکمیل deploy/.env موجود و DOMAIN:
docker compose --profile edge up --build
```

Caddy فعلی در `deploy/Caddyfile` فایل‌های `../web/dist` را سرو می‌کند؛ `/v1/*` و `/health*` را به **همان** service به‌نام `server:8080` می‌فرستد. داده‌های API از backend و DB موجود می‌مانند. این تغییر پیکربندی/راهنما، زیرساخت را خودکار ایجاد نمی‌کند و deployment production در این کار اجرا نشده است. برای rollback می‌توان build Web را کنار گذاشت و پیکربندی edge پیشین را برگرداند؛ پیش از release واقعی باید Caddyfile/Compose در محیط هدف validate شود.

## امنیت نشست

- پس از login، access/refresh token فقط در حافظهٔ tab قرار می‌گیرند؛ refresh با 401 یک‌بار rotate می‌شود. با reload، token وجود ندارد و Parent دوباره وارد می‌شود.
- `localStorage` فقط برای language/theme ظاهر استفاده می‌شود؛ هیچ token، usage یا location در browser storage یا Service Worker cache نمی‌شود.
- Sign out تلاش می‌کند refresh token فعلی را با endpoint موجود revoke کند و در هر حالت حافظه/cache برنامه را پاک می‌کند.
- password reset، مدیریت نشست‌های دیگر، حذف account/export و API تجمیعی report در backend فعلی ندارند.

## مرورگر و قابلیت آفلاین

فارسی RTL و English LTR، پوستهٔ روشن/تیره، layouts واکنش‌گرا، حالت‌های loading/empty/error، focus-visible و skip link تعبیه شده‌اند. Service worker فقط فایل offline shell/manifest/icon را cache می‌کند؛ مسیرهای `/v1` و `/health` از cache عبور نمی‌کنند. Offline shell صریحاً می‌گوید دادهٔ API به‌صورت آفلاین نگهداری نمی‌شود.

## قابلیت‌های پشتیبانی‌نشده

Call log، phone contacts، camera permission/location via camera، محتوای وب/پیام/جست‌وجو، گزارش یکپارچهٔ export، map tiles و تماس با emergency services اضافه نشده‌اند. موقعیت در صفحهٔ Web با مختصات و زمان/دقت API نشان داده می‌شود؛ تصویر زمینه فقط تزئینی است، نقشهٔ جغرافیایی نیست و هیچ سرویس نقشه فراخوانی نمی‌شود. Enforcement محدودیت Android دقیقاً مطابق `managementMode`, `enforcementCapability` و event status برگشتی نشان داده می‌شود؛ موفقیت قطعی/همیشگی وعده داده نمی‌شود.

## دستورات کیفیت

```bash
npm test
npm run build
npm audit --audit-level=moderate
```

این‌ها frontend-only checks هستند؛ E2E به backend واقعی، دو family مجزا، credentials و Android device نیاز دارد.
