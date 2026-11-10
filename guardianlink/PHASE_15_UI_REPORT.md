# Phase 15 — گزارش UI/UX

- داشبورد والد با پوستهٔ light/dark، navigation دسکتاپ و bottom navigation/drawer موبایل، کارت وضعیت family/device، unread notification و SOS active.
- مسیرهای `/children`, `/children/:id/usage`, `/location`, `/zones`, `/apps`, `/web-safety`, `/notifications`, `/sos`, `/reports`, `/privacy`, `/settings` با route guard نشست Parent؛ مسیر عمومی `/preview` فقط shell طراحی، بدون داده و بدون mutation.
- device detail از رکورد واقعی `DeviceOut`; وضعیت battery, lastSync, health, permissions از backend. Child name مستقل جعل نمی‌شود.
- کنترل‌های server-backed: create family, create pairing session، حذف device، limit screen time، اشتراک location، zone create/delete، app rule create/toggle/delete، Web Safety policy/rule، notification read/ack/resolve و preferences GET/PUT، SOS acknowledge/resolve.
- حالت loading/error/empty و retry در data panels؛ تغییرات موفق cache را invalidate می‌کنند. زمان‌ها از timestamp API، mobile overflow و reduced-motion در CSS پوشش داده شده.
- Persian RTL پیش‌فرض و English LTR؛ متنهای رابط در dictionary؛ زبان/theme ظاهر در localStorage است (هیچ دادهٔ حسابی/توکنی نه).
- دسترس‌پذیری پایه: semantic heading/main/nav/form labels، accessible name برای icon-only actions، `aria-label`, focus-visible و skip-to-content. Automated screenreader/axe/keyboard audit اجرا نشده و باید پیش از release انجام شود.
- Map: مختصات timestamp/distance/accuracy واقعی، تصویر زمینه تزئینی است نه نقشه؛ field label این را صریح می‌گوید.
- هیچ fixture یا fake device/location/SOS/usage در UI نیست. خطای API به error state و capability غایب به unavailable note می‌رسد.
