# گزارش جداسازی دادهٔ خانواده‌ها

**تاریخ بررسی:** ۷ اکتبر ۲۰۲۶  
**محدوده:** بازبینی مخزن و ابزارهای همین workspace؛ هیچ دسترسی به حساب cloud، production، staging، DNS، credential یا سرویس بیرونی وجود نداشت. پیکربندی/سند به‌تنهایی اثبات آمادگی نیست.
Phase23 test DB API flow تاریخی دو synthetic families و probes cross-family/device reads را PASS گزارش کرده بود. این evidence محدود به آن اجرا و API requests است؛ Phase25 هیچ UAT تازه اجرا نکرد و Phase26 هیچ production DB/deployment ندارد. بنابراین data isolation production **NOT_VERIFIED** و Go-Live blocker.

لازم است در staging مستقل حساب‌های QA authorization‌شده ایجاد شوند؛ Family A/B tokens, own-family/device reads/writes, notification/SOS/location/usage/export/delete, predictable IDs, concurrent/revoked sessions و object-level access probes را از طریق API و UI اجرا کنید؛ raw identifiers/secrets را در گزارش نگذارید. Production real-user data برای cross-family test ممنوع. Signoff مستقل Security/QA لازم است.
