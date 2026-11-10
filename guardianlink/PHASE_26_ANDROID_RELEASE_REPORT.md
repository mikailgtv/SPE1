# گزارش Android Production Candidate

**تاریخ بررسی:** ۷ اکتبر ۲۰۲۶  
**محدوده:** بازبینی مخزن و ابزارهای همین workspace؛ هیچ دسترسی به حساب cloud، production، staging، DNS، credential یا سرویس بیرونی وجود نداشت. پیکربندی/سند به‌تنهایی اثبات آمادگی نیست.
**وضعیت: BLOCKED (build/signing).** هیچ Phase26 APK/AAB تولید یا نصب نشد؛ Android wrapper/SDK/JDK17/device/ADB و signing inputs موجود نیستند. `artifacts/phase5/` دو APK debug تاریخی دارد؛ این‌ها Phase5 binary هستند، current RC یا قابل توزیع production نیستند؛ امضا/هش/metadata آن‌ها در این مرحله verify نشد.

Source defaults: app IDs `ir.guardianlink.parent` و `ir.guardianlink.child`; versionName `0.1.0-rc.1`, default versionCode 2; debug API `http://10.0.2.2:8080`; release URL از property `guardianReleaseBaseUrl` و Gradle task enforce https/non-example/non-localhost، code >1, SemVer و signing vars. CI workflow production candidate requires protected environment vars/secrets, cert SHA-256 and runs `apksigner` checks by design; workflow واقعی اجرا نشده. API URL output, manifest merge, minify/R8, permissions, debuggability, network-security, logs/crash, APK signature/cert/hash, key escrow/recovery NOT_TESTED.

**Release signing: BLOCKED (signing key/certificate اثبات نشده).** کلید واقعی، certificate fingerprint، custodians, recovery and rotation plan نداریم. هیچ unsigned package را production artifact ننامید.
