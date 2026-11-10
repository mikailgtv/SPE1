package ir.guardianlink.feature.usage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ir.guardianlink.core.designsystem.faNum
import ir.guardianlink.core.network.UsageDayTotal
import java.time.LocalDate

/**
 * نمودار میله‌ی هفتگی ساده (بدون کتابخانه‌ی chart — فقط Compose Canvas).
 * روند مصرف ۷ روز را خوانا نشان می‌دهد.
 */
@Composable
fun WeeklyBars(
    days: List<UsageDayTotal>,
    modifier: Modifier = Modifier,
    highlightMax: Boolean = true,
) {
    if (days.isEmpty()) return
    val max = days.maxOf { it.totalMinutes }.coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    val dimColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val gap = size.width * 0.03f
            val barW = (size.width - gap * (days.size + 1)) / days.size
            days.forEachIndexed { i, d ->
                val h = size.height * (d.totalMinutes.toFloat() / max).coerceAtLeast(0.02f)
                val isMax = highlightMax && d.totalMinutes == days.maxOf { it.totalMinutes } && d.totalMinutes > 0
                drawRoundRect(
                    color = if (isMax) barColor else dimColor,
                    topLeft = Offset(gap + i * (barW + gap), size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(6f, 6f),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { d ->
                Text(
                    faNum(dayLabel(d.date)),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/** برچسب کوتاه روز هفته از تاریخ ISO — ترتیب نمایش همان ترتیب روزهاست. */
fun dayLabel(isoDate: String): String {
    val d = runCatching { LocalDate.parse(isoDate).dayOfWeek }.getOrNull() ?: return ""
    return when (d) {
        java.time.DayOfWeek.SATURDAY -> "ش"
        java.time.DayOfWeek.SUNDAY -> "ی"
        java.time.DayOfWeek.MONDAY -> "د"
        java.time.DayOfWeek.TUESDAY -> "س"
        java.time.DayOfWeek.WEDNESDAY -> "چ"
        java.time.DayOfWeek.THURSDAY -> "پ"
        java.time.DayOfWeek.FRIDAY -> "ج"
    }
}

/** نوار درصد مصرف یک اپ نسبت به کل. */
@Composable
fun PercentBar(percent: Double, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        drawRoundRect(
            color = color.copy(alpha = 0.18f),
            cornerRadius = CornerRadius(8f, 8f),
        )
        drawRoundRect(
            color = color,
            size = Size(size.width * (percent.toFloat() / 100f).coerceIn(0f, 1f), size.height),
            cornerRadius = CornerRadius(8f, 8f),
        )
    }
}

/** نوار پیشرفت امروز/سقف با رنگ وضعیت (under/near/reached). */
@Composable
fun LimitBar(totalMinutes: Int, limitMinutes: Int?, state: String) {
    val color = when (state) {
        "reached" -> MaterialTheme.colorScheme.error
        "near" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val pct = if (limitMinutes != null && limitMinutes > 0) {
        (totalMinutes * 100.0 / limitMinutes).coerceIn(0.0, 100.0)
    } else {
        0.0
    }
    PercentBar(pct, color)
}

/** نمایش «۲:۳۴» با ارقام فارسی. */
fun hmFa(minutes: Int): String = faNum(ir.guardianlink.core.common.TimeFmt.hm(minutes))
