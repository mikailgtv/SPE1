package ir.guardianlink.feature.usage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.guardianlink.core.designsystem.GlCard
import ir.guardianlink.core.designsystem.GlErrorBanner
import ir.guardianlink.core.designsystem.GlPrimaryButton
import ir.guardianlink.core.designsystem.faNum
import ir.guardianlink.core.network.ScheduleOut
import ir.guardianlink.core.network.ScreenTimeReportOut

/** برچسب روزهای هفته از bitmask (bit0=Monday). */
fun daysLabelFa(bits: Int): String {
    val names = listOf("د", "س", "چ", "پ", "ج", "ش", "ی")
    val picked = names.filterIndexed { i, _ -> (bits and (1 shl i)) != 0 }
    return if (picked.size == 7) "هر روز" else picked.joinToString(" ")
}

@Composable
fun stateLabelFa(state: String): String = when (state) {
    "reached" -> stringResource(R.string.st_state_reached)
    "near" -> stringResource(R.string.st_state_near)
    else -> stringResource(R.string.st_state_under)
}

/**
 * کارت خلاصه‌ی Screen Time برای داشبورد والد:
 * امروز / سقف / باقی‌مانده + نوار پیشرفت + ۳ اپ پرمصرف + وضعیت دسترسی مصرف.
 */
@Composable
fun ScreenTimeCard(
    report: ScreenTimeReportOut?,
    usageAccessGranted: Boolean?,
    onClick: () -> Unit,
) {
    GlCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onClick() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⏳ " + stringResource(R.string.st_card_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (report != null) Text(stateLabelFa(report.today.state), style = MaterialTheme.typography.labelMedium)
        }
        if (report == null) {
            Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodySmall)
        } else {
            val t = report.today
            Text(hmFa(t.totalMinutes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.st_today_usage), style = MaterialTheme.typography.bodySmall)
            val lim0 = t.limitMinutes
            if (t.limitEnabled && lim0 != null) {
                LimitBar(t.totalMinutes, lim0, t.state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.st_limit, hmFa(lim0)), style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(R.string.st_remaining, hmFa(t.remainingMinutes ?: 0)),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            if (usageAccessGranted == false) {
                Text(
                    "🔴 " + stringResource(R.string.st_usage_access_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            t.topApps.take(3).forEach { a ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(a.appName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Text(hmFa(a.usageMinutes), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClick) { Text(stringResource(R.string.st_open)) }
        }
    }
}

/** صفحه‌ی اصلی Screen Time یک دستگاه: امروز، هفته، برنامه‌ها، گزارش‌ها، تنظیمات. */
@Composable
fun ScreenTimeScreen(
    deviceId: String,
    onOpenApps: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenApp: (String) -> Unit,
    onBack: () -> Unit,
    vm: ScreenTimeViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(deviceId) { vm.load(deviceId) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.st_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        GlErrorBanner(ui.error)
        if (ui.loading) {
            Text(stringResource(R.string.loading))
            return@Column
        }
        val r = ui.report ?: return@Column

        // ---- امروز ----
        GlCard {
            Text(stringResource(R.string.st_today), style = MaterialTheme.typography.labelMedium)
            Text(hmFa(r.today.totalMinutes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            val lim = r.today.limitMinutes
            if (r.today.limitEnabled && lim != null) {
                LimitBar(r.today.totalMinutes, lim, r.today.state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.st_limit, hmFa(lim)), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.st_remaining, hmFa(r.today.remainingMinutes ?: 0)), style = MaterialTheme.typography.bodySmall)
                }
                Text(stateLabelFa(r.today.state), style = MaterialTheme.typography.labelSmall)
            }
            // ---- پرمصرف‌ها ----
            Text(stringResource(R.string.st_top_apps), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            r.today.topApps.take(5).forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenApp(a.packageName) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(a.appName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(hmFa(a.usageMinutes) + "  (" + faNum(a.percent.toInt()) + "٪)", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ---- هفته ----
        GlCard {
            Text(stringResource(R.string.st_week), style = MaterialTheme.typography.labelMedium)
            WeeklyBars(r.weekly.days)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.st_week_total, hmFa(r.weekly.totalMinutes)), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.st_week_avg, hmFa(r.weekly.averageMinutes)), style = MaterialTheme.typography.bodySmall)
            }
            r.weekly.mostUsedDay?.let { Text(stringResource(R.string.st_most_day, dayLabel(it)), style = MaterialTheme.typography.bodySmall) }
            r.weekly.leastUsedDay?.let { Text(stringResource(R.string.st_least_day, dayLabel(it)), style = MaterialTheme.typography.bodySmall) }
        }

        // ---- سقف روزانه ----
        GlCard {
            Text(stringResource(R.string.st_daily_limit), style = MaterialTheme.typography.labelMedium)
            if (r.settings.enabled) {
                Text(
                    stringResource(R.string.st_limit_value, hmFa(r.settings.dailyLimitMinutes)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(stringResource(R.string.st_limit_off), style = MaterialTheme.typography.bodyMedium)
            }
        }

        // ---- برنامه‌ها ----
        GlCard {
            Text(stringResource(R.string.st_schedules), style = MaterialTheme.typography.labelMedium)
            if (r.schedules.isEmpty()) {
                Text(stringResource(R.string.st_no_schedules), style = MaterialTheme.typography.bodySmall)
            } else {
                r.schedules.forEach { s -> ScheduleRow(s) }
            }
            Text(
                stringResource(R.string.st_advisory_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlPrimaryButton(stringResource(R.string.st_open_apps), onClick = onOpenApps, modifier = Modifier.weight(1f))
            GlPrimaryButton(stringResource(R.string.st_open_reports), onClick = onOpenReports, modifier = Modifier.weight(1f))
        }
        GlPrimaryButton(stringResource(R.string.st_open_settings), onClick = onOpenSettings)
    }
}

@Composable
fun ScheduleRow(s: ScheduleOut) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text((if (s.enabled) "🟢 " else "⚪ ") + s.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        Text(
            faNum(s.startTime) + " → " + faNum(s.endTime) + "  ·  " + daysLabelFa(s.daysOfWeek),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
