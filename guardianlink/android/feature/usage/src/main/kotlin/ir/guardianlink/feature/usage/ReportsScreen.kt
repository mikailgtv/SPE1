package ir.guardianlink.feature.usage

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
import ir.guardianlink.core.designsystem.faNum

/**
 * گزارش روزانه + هفتگی.
 * دسته‌بندی‌ها فقط وقتی نشان داده می‌شوند که سیستم مقدار واقعی داده باشد
 * (دسته‌ی ساختگی هرگز نمایش داده نمی‌شود).
 */
@Composable
fun ReportsScreen(
    deviceId: String,
    onBack: () -> Unit,
    vm: ReportsViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(deviceId) { vm.load(deviceId) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rp_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        GlErrorBanner(ui.error)
        if (ui.loading) {
            Text(stringResource(R.string.loading))
            return@Column
        }

        ui.daily?.let { d ->
            GlCard {
                Text(stringResource(R.string.rp_daily, faNum(d.date)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ReportRow(stringResource(R.string.rp_total), hmFa(d.totalMinutes))
                ReportRow(
                    stringResource(R.string.rp_most_used),
                    d.mostUsedApp?.let { it.appName + " (" + hmFa(it.usageMinutes) + ")" } ?: "—",
                )
                ReportRow(stringResource(R.string.rp_apps_used), faNum(d.appsUsed).toString())
                ReportRow(stringResource(R.string.rp_sessions), faNum(d.sessions).toString())
                if (d.categories.isNotEmpty()) {
                    Text(stringResource(R.string.rp_categories), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                    val catTotal = d.categories.sumOf { it.minutes }.coerceAtLeast(1)
                    d.categories.forEach { c ->
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(categoryLabel(c.category), style = MaterialTheme.typography.bodySmall)
                                Text(hmFa(c.minutes), style = MaterialTheme.typography.bodySmall)
                            }
                            PercentBar(c.minutes * 100.0 / catTotal)
                        }
                    }
                }
            }
        }

        ui.weekly?.let { w ->
            GlCard {
                Text(stringResource(R.string.rp_weekly), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                WeeklyBars(w.days)
                w.days.forEach { day ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(dayLabel(day.date) + "  " + faNum(day.date), style = MaterialTheme.typography.bodySmall)
                        Text(hmFa(day.totalMinutes), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    }
                }
                ReportRow(stringResource(R.string.rp_week_total), hmFa(w.totalMinutes))
                ReportRow(stringResource(R.string.rp_week_avg), hmFa(w.averageMinutes))
                ReportRow(stringResource(R.string.rp_most_day), w.mostUsedDay?.let { dayLabel(it) } ?: "—")
                ReportRow(stringResource(R.string.rp_least_day), w.leastUsedDay?.let { dayLabel(it) } ?: "—")
            }
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
