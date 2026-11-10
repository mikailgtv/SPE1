# گزارش UX خطاها

**تاریخ ارزیابی:** ۷ اکتبر ۲۰۲۶  
**اصل شواهد:** تست واقعی کاربر فقط زمانی PASS است که اجرا، مشاهده و ثبت شود. کد/گزارش قبلی/تست خودکار به‌تنهایی UAT نیست.

401/403/404/409/422/429/500، timeout، offline، server unavailable، expired session و expired pairing عمداً از UI واقعی ایجاد/مشاهده نشدند؛ **NOT_TESTED / BLOCKED_BY_ENVIRONMENT**. بنابراین روشن‌بودن پیام، next action، raw backend leakage، redirect/refresh، infinite retry و duplicate submit اثبات نشده.

Phase23 تاریخی response statusهای API و rate limit را بررسی کرده اما UX را نه؛ همان گزارش یک ناسازگاری completion/error logging نیز ذکر می‌کند که در Phase25 بازآزمایی نشده. پیش از قبول release باید خطاها در UI واقعی و با پاسخ‌های کنترل‌شده از staging تست و متن‌های نمایش‌داده‌شده بازبینی شوند؛ داده/secret در screenshot/log نباشد.
