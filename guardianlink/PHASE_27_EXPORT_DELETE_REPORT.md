# گزارش Export/Delete و Retention — Phase 27

**وضعیت:** `FAILED` برای قابلیت family/account export/delete؛ `CLOSED` برای soft removal دستگاه آزمایشی.

## Probeهای API

- `GET /v1/families/me/export` → HTTP 404.
- `DELETE /v1/families/me` → HTTP 405.
- `DELETE /v1/me` → HTTP 405.
- بنابراین endpoint قابل استفاده برای family/account export یا حذف کامل حساب در API فعلی پیدا نشد؛ semantics یا پاک‌سازی داده ادعا نمی‌شود.

## حذف device آزمایشی

- Parent A دستگاه synthetic متعلق به family خودش را حذف کرد: HTTP 204.
- دستگاه از فهرست active خارج شد؛ query metadata DB نشان داد وضعیت `removed`، device secret hash پاک و ۱/۱ session revoke شده است.
- این عملیات soft removal بود؛ داده‌های تاریخی/family/account به‌صورت erase نشدند. Parent A نتوانست device Family B را حذف کند (404).

## تصمیم ایمنی

این مورد یک capability failure واقعی برای release gate است. هیچ cascade/schema/retention تغییر ندادیم؛ پیش از پیاده‌سازی export/delete باید owner-approved data map، retention/backup-copy policy، authorization، cascade/rollback review و disposable-DB regression طراحی شود. حقوق subject یا compliance قانونی بدون حوزهٔ قضایی مشخص تأیید نمی‌شود (`JURISDICTION-SPECIFIC REQUIREMENT`).
