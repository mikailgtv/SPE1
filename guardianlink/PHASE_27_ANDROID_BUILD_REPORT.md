# گزارش Android Build — Phase 27

**وضعیت:** `BLOCKED`؛ build parent و child اجرا/تأیید نشد.

## یافته‌های محیط

- ADB 34.0.5 موجود بود؛ Android SDK platform 36، Build Tools 36.0.0 و `sdkmanager` در مسیر قابل استفاده نبودند.
- مجوزهای Android SDK نصب/تأیید نشده‌اند. پذیرش شرایط مجوز خودکار یا به‌جای کاربر انجام نشد.
- به همین دلیل `:app-parent:assembleDebug` و `:app-child:assembleDebug` در این ادامه به‌عنوان build موفق گزارش نمی‌شوند؛ APK قابل تأیید تولید/نصب نشد.
- Gradle/SDK local configuration و real install/test نیز موجود نبود؛ عدم دسترسی به `/dev/kvm` جداگانه در گزارش Runtime ثبت شده است.

## برای بستن blocker

1. اپراتور مجاز Android SDK رسمی را نصب کند و licenseها را شخصاً بررسی و قبول کند.
2. platform 36، build-tools 36.0.0 و platform-tools را در SDK path محلی فراهم کند.
3. با JDK سازگار buildهای `:app-parent:assembleDebug` و `:app-child:assembleDebug` را اجرا کند و artifactها را با checksum ثبت کند.
4. سپس روی emulator/device مجاز، install/launch/permissions/deep-link/pairing را جداگانه بیازماید.

Debug build موفق نیز به‌تنهایی sign/release یا Google Play policy approval محسوب نمی‌شود.
