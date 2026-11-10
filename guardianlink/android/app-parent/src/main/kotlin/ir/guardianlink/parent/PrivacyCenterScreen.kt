package ir.guardianlink.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.guardianlink.core.designsystem.GlCard

/** Transparent, source-backed disclosure only. This screen does not fetch or collect additional data. */
@Composable
fun PrivacyCenterScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("بازگشت") }
        Text("مرکز حریم خصوصی", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("این توضیح بر اساس قابلیت‌ها و API فعلی GuardianLink است؛ برخی عملیات هنوز در برنامه پیاده‌سازی نشده‌اند.")

        GlCard {
            Text("چه داده‌هایی و چرا", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("حساب والد: ایمیل و هش گذرواژه برای ورود؛ نشست‌ها با هش refresh token مدیریت می‌شوند.")
            Text("دستگاه: شناسه، نام و مشخصات فنی محدود، نسخه برنامه/Android، باتری، اتصال، آخرین حضور و وضعیت مجوزها برای اتصال و نمایش سلامت.")
            Text("مصرف برنامه: نام بسته، نام/دسته برنامه و خلاصه مصرف روزانه برای گزارش و قواعد زمان صفحه؛ دسترسی Usage Access تابع مجوز Android است.")
            Text("موقعیت: مختصات، دقت، زمان و منبع برای موقعیت/تاریخچه و geofence؛ فقط با اشتراک فعال و دسترسی‌های Android. تعداد/تناوب واقعی به تنظیمات و سیستم‌عامل وابسته است.")
            Text("اعلان، geofence و SOS: رخدادها و وضعیت‌های لازم برای نمایش و رسیدگی. SOS ممکن است وضعیت اضطراری و نقاط مکانی همان نشست را شامل شود.")
            Text("ایمنی وب: تنظیمات/قواعد و وضعیت قابلیت یا خطا ذخیره می‌شود؛ migration فعلی ثبت تاریخچهٔ سایت‌های مرورشده را تصریحاً ندارد.")
        }

        GlCard {
            Text("نگهداری و کنترل‌های موجود", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("تاریخچه موقعیت برای هر دستگاه قابل تنظیم است: صفر یعنی فقط آخرین نقطه، یا ۱ تا ۳۶۵ روز؛ حذف تاریخچه در API دستگاه وجود دارد. پاک‌سازی خودکار تنبل است و job زمان‌بندی‌شدهٔ سراسری تأیید نشده.")
            Text("رویدادهای geofence با مقدار پیکربندی پیش‌فرض ۳۰ روز پاک می‌شوند، اما اجرای پاک‌سازی به فعالیت مسیرها وابسته است؛ تضمین زمان‌بندی‌شده نیست.")
            Text("حذف دستگاه در API به معنی حذف همه داده‌های وابسته نیست؛ دستگاه غیرفعال/removed می‌شود و برخی تاریخچه باقی می‌ماند.")
            Text("خروجی یکپارچهٔ داده خانواده، حذف حساب/خانواده و retention قابل تنظیم برای همه دسته‌ها در API فعلی موجود نیست.")
            Text("تنظیم موقعیت و حذف تاریخچه از صفحه‌های موقعیت؛ مدیریت دستگاه از داشبورد قابل دسترسی است.")
        }

        GlCard {
            Text("برنامه Child و مجوزها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Child برای sync از شبکه و برای قابلیت‌های اختیاری از مجوزهای مربوط Android استفاده می‌کند. موقعیت پس‌زمینه تابع مجوز و محدودیت‌های نسخه Android است؛ وضعیت ناموفق/محدود نباید موفق جلوه داده شود.")
            Text("در این نسخه قابلیت تماس‌ها، مخاطبان، دوربین، میکروفون، عکس‌ها، clipboard و محتوای پیام‌ها در API پروژه ارائه نشده است.")
        }

        GlCard {
            Text("شفافیت عملیاتی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Backend برای سلامت سرویس، شمارنده‌ها و خطاهای redacted ثبت می‌کند. این جمع‌آوری پنهان نیست و این صفحه قابلیت نظارتی تازه‌ای اضافه نمی‌کند. از logها نباید token، گذرواژه یا مختصات خام استخراج شود.")
        }

        GlCard {
            Text("قابلیت‌های هنوز در دسترس نیست", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Export داده خانواده، حذف حساب/خانواده، صفحهٔ مدیریت retention کلی و درخواست حذف/Export از Privacy Center پیاده‌سازی نشده‌اند. این صفحه هیچ موفقیت یا انجام عملیاتی را وانمود نمی‌کند.")
        }
    }
}
