# Phase 16 — گزارش Offline و Sync

## طراحی sync آفلاین فعلی

| داده | رفتار source فعلی | محدودیت/ریسک |
|---|---|---|
| Device status/telemetry | `StatusSyncWorker` با WorkManager، constraint شبکه، cadence دوره‌ای حداقل ۱۵ دقیقه و work یک‌باره در شروع/وصل شبکه. retry برای خطاهای قابل retry. | اجرای زمان‌بندی توسط Android تضمین زمان دقیق ندارد؛ Doze/battery/OS policy می‌تواند تأخیر دهد. دادهٔ status قدیمی تا sync بعدی باقی می‌ماند. runtime retry آزموده نشده. |
| Location عادی | `LocationQueue` در `SecureStore`، AES-GCM/Keystore؛ cap=240 fix، dedupe client timestamp؛ clear پس از upload موفق. ابتدا status sync بدون مختصات؛ capture/upload تک‌شات فقط پس از پاسخ server `locationSharing=true` و از endpoint `/v1/device/location/sync`; false/null صف را پاک می‌کند. old last-known بالای ۴۸ ساعت رد می‌شود. | cap و permission/user sharing باعث drop/عدم capture می‌شوند. نقطه‌ها تضمین ارسال فوری ندارند. sharing off صف را پاک و Backend را از opt-out مطلع می‌کند. |
| Usage | collector در زمان sync از `UsageStatsManager` امروز و دیروز را query می‌کند؛ صف مستقل پایدار usage دیده نشد. collector حداکثر ۶۰ app/day برمی‌گزیند؛ Backend upsert idempotent است و تا ۸ روز گذشته را می‌پذیرد. | upload نشدن مستقل از sync؛ پس از بیشتر از window دو روزه، روز قدیمی در بستهٔ بعدی collector نیست و بازیابی آن تضمین نمی‌شود، حتی اگر Backend ۸ روز را قبول کند. Usage Access لغوشده یعنی payload خالی/null. |
| App rules | snapshot versioned/encrypted در cache Child؛ cache پس از sync موفق از پاسخ سرور update می‌شود؛ قواعد cacheشده به‌صورت opportunistic اجرا می‌شوند. | offline بودن، rule changes جدید Parent را به Child نمی‌رساند. deleted rules در cache tombstone می‌شوند تا در Device Owner پس از sync امکان restore package فراهم باشد. |
| App restriction events | encrypted cache، bounded تا ۵۰۰ event؛ `clientEventId` و ACK فقط بعد از sync موفق. | سقف ثابت؛ رفتار drop در شرایط پرشدن/اختلال runtime بررسی نشده. |
| Web Safety | encrypted policy snapshot و event cache (حداکثر ۱۰۰/queue batch حداکثر ۲۰)؛ ACK پس از sync؛ اجرای محلی پس از policy cache. | فقط managed Chrome path پشتیبانی محدود دارد؛ cache قدیمی offline policy را stale می‌کند؛ اثر Chrome verify نمی‌شود. |
| SOS activation/tracking | outbox رمزگذاری‌شده با TTL=۲۴ ساعت و سقف نقاط؛ پس از network و authenticated status sync flush می‌شود. UI تا پذیرش server صریحاً delivered نمی‌گوید. | SOS آفلاین به معنی رسیدن فوری Parent نیست؛ expiry، لغو session یا authorization ممکن است مانع flush شود. هیچ تماس emergency service ثالثی انجام نمی‌شود. |
| Notifications/Parent actions | notifications منبع Backend است؛ Parent Web/Android تغییرات را API می‌فرستند. | Parent mutations (rule/settings/notification ack و …) برای offline queue عمومی دیده نشدند. در Web، API no-store و shell-only cache است؛ offline دادهٔ protected قابل استفاده نیست. |

## Work scheduling، permission و مصرف باتری

- Sync عادی FGS دائمی/loop مکان ندارد؛ `PeriodicWorkRequestBuilder` حداقل ۱۵ دقیقه و شرط `CONNECTED` دارد. `NetworkWatcher` روی دسترسی شبکه one-time work enqueue می‌کند.
- `LocationCollector` یک fix current می‌گیرد، timeout=۱۲ ثانیه، GMS Fused API را ترجیح می‌دهد و framework Android fallback دارد. Location provider off یا permission denied باعث عدم fix می‌شود؛ صف/last known نباید این status را به `granted` بدل کند.
- SOS tracking مسیر جدا و user-triggered است: foreground service و notification دائمیِ قابل‌مشاهده، permission/background permission و duration/interval محدود. تنها پس از تأیید کاربر؛ این محدودیت‌ها bypass نمی‌شوند.
- Android restrictions و background policy در runtime/device/API versionهای متفاوت **BLOCKED** هستند؛ WorkManager documentation/limits از source config قابل استنباط‌اند، نه اثبات اجرای زمان‌بندی.

## Offline در Parent Web

- Web tokenها فقط در حافظهٔ tab هستند؛ reload باعث logout عملی/نیاز به ورود می‌شود. Theme/language تنها localStorage مشاهده‌شده است.
- Service Worker `offline.html`, manifest و icon را cache می‌کند؛ برای navigation، network failure به offline page fallback می‌دهد. `/v1/` و `/health` عمداً از service worker bypass شده‌اند؛ API cache/persisted query cache یا offline mutation queue پیدا نشد.
- `/preview` مسیر نمایشی بدون API است؛ offline shell/preview هیچ دسترسی به family/device data نمی‌دهد.

## امنیت و چرخهٔ ACK

- Location queue و SOS outbox با SecureStore امن نگه داشته می‌شوند؛ app/Web Safety cache نیز encrypted است. صف‌های bounded و dedupe key دارند.
- Worker پس از پاسخ موفق sync، event IDs را acknowledge و نقاط location را حذف می‌کند. برای SOS pending flush مجزا بعد از authenticated sync اجرا می‌شود.
- پس از fail-closed تغییر Phase 16، در صورت sharing فعال و داشتن fix، ارسال location یک درخواست API جدا بعد از status sync دارد؛ این به‌جای payload مختصات در sync است و یک round-trip شبکهٔ اضافه ایجاد می‌کند. مقدار مصرف باتری/شبکه اندازه‌گیری نشده (**BLOCKED**).
- **BLOCKED:** fault-injection برای crash بین server commit و local ACK، partial batch، retry/backoff، storage corruption، clock drift، حذف اپ، revoke device و expiry آفلاین انجام نشده. اسناد/comments کد retry-safe بودن را توصیف می‌کنند ولی runtime اثبات نیست.

## راهنمای status برای Parent UI

`lastSync`/`lastSeen` باید freshness indicator باشند؛ location نباید «زنده» خوانده شود مگر timestamp تازه و sharing/permission/provider مناسب باشد. SOS `QUEUED`، `EXPIRED_NOT_DELIVERED` یا tracking `LIMITED/STOPPED` نباید به‌عنوان delivered یا emergency service notification تعبیر شوند. App rules فقط `applied_*` پس از گزارش Child را از unsupported/failed جدا کنند. این semantics از model/status source است؛ UI real-device validation **BLOCKED** است.

## اصلاح مرتبط Phase 16

پیش از تغییر کد ثبت شد که Child local timezone می‌فرستد ولی چند مسیر Backend از timezone JVM استفاده می‌کرد. `UsageService` اکنون timezone device را برای برچسب روز و limit/schedule/report استفاده می‌کند. این اصلاح در source اعمال شده، اما Backend build/tests در محیط حاضر **BLOCKED** اند؛ هنوز نمی‌توان آن را verified fix نامید.
