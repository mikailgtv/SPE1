# گزارش UAT دسترس‌پذیری

**تاریخ ارزیابی:** ۷ اکتبر ۲۰۲۶  
**اصل شواهد:** تست واقعی کاربر فقط زمانی PASS است که اجرا، مشاهده و ثبت شود. کد/گزارش قبلی/تست خودکار به‌تنهایی UAT نیست.

**NOT_TESTED.** UI در مرورگر/دستگاه تعاملی قابل‌آزمایش نبود. contrast و font scale، touch target، keyboard-only، focus order/visible focus، screen reader basics، labels، error identification، RTL semantics و Android accessibility service ارزیابی نشده‌اند.

وجود aria/skip-link در source یا پیکربندی CSS اثبات usability دسترس‌پذیر نیست. در نتیجه هیچ PASS و هیچ صفر نقصی ادعا نمی‌شود. اجرای لازم: keyboard pass مرورگرهای هدف، screen reader وب و Android TalkBack روی staging build، ثبت نسخه و شواهد redacted.
