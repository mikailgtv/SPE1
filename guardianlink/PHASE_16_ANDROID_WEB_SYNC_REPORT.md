# Phase 16 — گزارش همگامی Android و Web

## قاعدهٔ منبع حقیقت

برای دادهٔ اصلی، Android Parent، Android Child و Parent Web در source به `/v1` Backend مشترک وصل می‌شوند؛ PostgreSQL پشت همان Backend است. Web `/preview` فقط UI نمایشی است و API/data protected ندارد. Service Worker فقط offline shell ثابت را cache می‌کند و `/v1` را از cache/fetch handler خارج می‌کند. بنابراین هیچ‌یک از cacheهای UI/صف محلی جایگزین DB اصلی نیستند.

## ماتریس قابلیت مشترک Parent

| حوزه | Android Parent | Parent Web | منبع و همگامی مورد انتظار | تأیید واقعی |
|---|---|---|---|---|
| Login/session | user auth و SecureStore؛ refresh client | login/register/logout، refresh-on-401؛ token حافظه‌ای tab | Backend user session/DB | **BLOCKED**؛ ورود واقعی مشترک آزمایش نشده. |
| Family/devices | family, health/status, remove | family, device list/detail/remove | `families/me`, `families/me/devices` | **BLOCKED**؛ سازگاری واقعی state/authorization آزمایش نشده. |
| Pairing | ایجاد code؛ Child pair flow | صفحهٔ Devices، ایجاد short-lived code در درخواست کاربر | `POST /v1/pairing/sessions`; Child exchange/confirm | **BLOCKED**؛ no device/backend. source هر دو Parent را به همان route وصل می‌کند. |
| Usage/Screen Time | امروز/هفته/detail/report/settings | usage report, settings و reports | usage/screen-time API و DB | **BLOCKED**؛ payload و روز boundary با دادهٔ واقعی مقایسه نشده. timezone source fix انجام شد ولی compile/test مسدود است. |
| Location | map/history/settings | map/history/settings | shared location API/DB؛ permission/sharing server state | **BLOCKED**؛ grant/revoke, stale/current cross-client مقایسه نشده. Child source اکنون اول sharing response می‌گیرد و سپس در صورت `true` batch جدا می‌فرستد؛ device validation نشده. |
| Geofence | zone CRUD/history | zone CRUD/history | server state/events | **BLOCKED**؛ فقط server-side fix evaluator در source است. |
| App rules | inventory, rules, enforcement status | inventory/rules/events | versioned backend rules + Child sync report | **BLOCKED**؛ DB version/state/applied status در دستگاه واقعی دیده نشده. |
| Web Safety | policy/rule/status UI | policy/rule/status UI | rule storage + Child reported capability/status | **BLOCKED**؛ Chrome/DPM effect خودکار verify نمی‌شود. |
| Notifications | feed/unread/read/ack/resolve و local notification اختیاری | feed/unread/read/ack/resolve/preferences | shared notification rows؛ read per-user | **BLOCKED**؛ خواندن در یک client و بازخوانی در client دیگر اجرا نشده. |
| SOS | active/history/detail/ack/resolve | active/detail/action UI | shared SOS sessions/events/locations | **BLOCKED**؛ هیچ Parent واقعی notification/receipt دریافت نکرده. |

## سازگاری status/presence و freshness

- API device model، status، presence، health، connection state، last seen/sync، battery و permission snapshot را عرضه می‌کند. Android Parent و Web از همین Backend data می‌خوانند و UI جداگانه format/map می‌کند.
- Android Parent داشبورد را در حالی که screen باز است هر ۳۰ ثانیه refresh می‌کند. Web active SOS را هر ۳۰ ثانیه و unread count را هر ۶۰ ثانیه refresh می‌کند؛ queries دیگر هنگام load/refresh/invalidation تازه می‌شوند. این‌ها polling UI هستند، نه realtime guarantee.
- سلامت online/active از آخرین heartbeat/sync و lease/settings سرور مشتق می‌شود؛ به معنی اثبات آنلاین‌بودن لحظه‌ای Child نیست. Web ممکن است تا refresh بعدی stale بماند؛ API runtime/clock skew بررسی نشده.
- Android Child خانهٔ خود را از آخرین وضعیت sync ذخیره‌شده روی همان دستگاه نشان می‌دهد؛ ممکن است تا sync بعدی با Parent feed تفاوت زمانی داشته باشد. این status محلی cache شدهٔ نمایش است، نه منبع دوم دادهٔ server.

## Pairing و خانواده

- Parent Android و Web هر دو می‌توانند pairing session/código را از Backend بسازند. Web کد را state حافظه‌ای نگه می‌دارد و هنگام reload/ترک صفحه آن را از UI از دست می‌دهد. Child Android exchange/confirm را با کد انجام می‌دهد.
- session، family ownership، device secret/session در Backend نگهداری می‌شوند. هیچ Web-only device/family record یا pairing databaseی دیده نشد.
- تست‌های منبع `PairingTest` وجود دارند، از جمله valid/expired/replay/duplicate-install/authorization scenarioها؛ در این محیط اجرا نشده‌اند.
- **BLOCKED:** pair واقعی، sync دو Parent، revoke/remove و login یک user در هر دو platform اجرا نشده است.

## Event، اعلان و read state

- Eventهای منتخب Child در Backend ثبت و notification projection در همان DB ایجاد می‌شوند. Parent Android dashboard feed را poll می‌کند و در صورت permission سیستم می‌تواند local notification نشان دهد؛ Web feed را query می‌کند.
- Android 13+ Parent برای notification سیستم permission رسمی درخواست می‌کند و UI خاموش بودن آن را نشان می‌دهد. notification داخل برنامه با permission OS قابل‌مشاهده می‌ماند.
- Push server-side و live event stream وجود ندارد؛ health `push_notifications=not_configured` است. بنابراین «اعلان رسید» را نباید معادل تحویل فوری دانست.
- Read state per user است؛ Parent Web و Android از همان user باید پس از fetch، همان `isRead` بگیرند. ACK/RESOLVED به feed مشترک مربوط است. این cross-client effect فعلاً **BLOCKED** است.

## Usage، location و geofence

- Usage upload از Child و summary از دو Parent یک DB است. Collector فعلی امروز و دیروز را با timezone دستگاه گردآوری می‌کند؛ اگر sync بیش از بازهٔ collector عقب افتد، صف مستقل usage برای روزهای قدیمی وجود ندارد. جزئیات در `PHASE_16_OFFLINE_SYNC_REPORT.md`.
- ممیزی یک ناسازگاری calendar-zone در `UsageService` پیدا کرد و پس از ثبت ممیزی، محدود اصلاح شد: تمام day-based query/evaluation و epoch boundaries از timezone device می‌گیرند (fallback UTC). Server test/build **BLOCKED**؛ بنابراین cross-client report correctness نهایی نشده.
- Location fixes در Backend واحد ذخیره می‌شوند. Parent Android/Web هر دو همان current/history/settings endpointها را می‌خوانند. Worker پس از اصلاح، مختصات را در status sync نمی‌فرستد و فقط پس از پاسخ server `locationSharing=true` از endpoint location sync جداگانه ارسال می‌کند؛ false/null fail-closed و صف را پاک می‌کند. Backend نیز هنگام sharing off ذخیره را رد می‌کند. Permission/provider/age labels و UI refresh واقعی هنوز مقایسه نشده‌اند.
- geofence calculation سمت Backend از uploaded points؛ Android `GeofencingClient` ندارد. UI دو Parent ممکن است همان zone/state/history را ببیند؛ transition notification وابسته به fix upload و server computation است، نه listener محلی فوری.

## Policy state و status صداقت‌مندانه

- App rule persistence روی Backend مشترک است؛ Child policy cache رمزگذاری‌شده و versioned است. Parent UI باید `normal/managed/unsupported`, `lastEnforcementStatus` و eventها را از API نشان دهد؛ ثبت rule در سرور معادل suspend شدن اپ نیست.
- Web Safety stored settings و Child-reported mode/status جدا هستند. در managed mode، صرف set کردن Chrome restrictions نتیجهٔ واقعی داخل Chrome را اثبات نمی‌کند؛ UI باید `UNSUPPORTED`, `ERROR`, `policy_stored_effect_unverified` را پنهان نکند.
- Permission status در snapshot سرور از Child می‌آید؛ تغییر permission روی Android تا sync بعدی stale می‌ماند. وجود model به معنی دسترسی granted نیست.

## یافته‌های unavailable در همهٔ clientها

- APIهای call log و contacts در source جاری دیده نشدند؛ صفحه/دادهٔ آن‌ها نباید ساخته یا ادعا شود.
- Camera/microphone، private messages، keystrokes، clipboard، photos، search/page content و cookies جمع‌آوری نمی‌شوند؛ هیچ قابلیت hidden monitoring در scope این گزارش نیست.
- Web dashboard یا preview جایگزین login/authorization برای دادهٔ واقعی نیست.

## محدودیت نتیجه

همهٔ نتیجه‌های بالا دربارهٔ همگامی cross-client، جز build و unit test Web، **استنباط از mapping/source** هستند. هیچ backend/DB/device/browser واقعی برای مقایسهٔ همزمان در این محیط اجرا نشده است. هر assertion اجرایی باید پس از فراهم شدن test environment با کاربر/دادهٔ آزمایشی مجاز، دو Parent session و Child device تکرار شود.
