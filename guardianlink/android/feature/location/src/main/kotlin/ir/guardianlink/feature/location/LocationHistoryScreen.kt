package ir.guardianlink.feature.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import ir.guardianlink.core.network.LocationFixOut
import java.time.OffsetDateTime

/**
 * تاریخچه‌ی موقعیت: Timestamp / Latitude / Longitude / Accuracy هر رکورد.
 * فیلتر امروز/دیروز/همه. داده بیشتر از retention نگهداری نمی‌شود (purge سمت سرور).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationHistoryScreen(
    deviceId: String,
    initialFilter: String = "all",
    onBack: () -> Unit,
    vm: LocationHistoryViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(deviceId) {
        vm.load(
            deviceId,
            when (initialFilter) {
                "today" -> HistoryFilter.TODAY
                "yesterday" -> HistoryFilter.YESTERDAY
                else -> HistoryFilter.ALL
            },
        )
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(stringResource(R.string.hist_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (ui.retentionDays <= 0) stringResource(R.string.loc_retention_none)
                    else stringResource(R.string.loc_retention_days, faNum(ui.retentionDays)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        GlErrorBanner(ui.error)
        if (!ui.sharingEnabled) {
            Text("🔴 " + stringResource(R.string.loc_sharing_off_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = ui.filter == HistoryFilter.ALL, onClick = { vm.setFilter(deviceId, HistoryFilter.ALL) }, label = { Text(stringResource(R.string.loc_all)) })
            FilterChip(selected = ui.filter == HistoryFilter.TODAY, onClick = { vm.setFilter(deviceId, HistoryFilter.TODAY) }, label = { Text(stringResource(R.string.loc_today)) })
            FilterChip(selected = ui.filter == HistoryFilter.YESTERDAY, onClick = { vm.setFilter(deviceId, HistoryFilter.YESTERDAY) }, label = { Text(stringResource(R.string.loc_yesterday)) })
        }
        if (ui.loading) {
            Text(stringResource(R.string.loading))
        } else if (ui.points.isEmpty()) {
            GlCard { Text(stringResource(R.string.hist_empty), style = MaterialTheme.typography.bodyMedium) }
        } else {
            Text(stringResource(R.string.hist_count, faNum(ui.points.size)), style = MaterialTheme.typography.labelMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ui.points, key = { it.timestamp + it.latitude }) { p -> HistoryRow(p) }
            }
        }
    }
}

@Composable
private fun HistoryRow(p: LocationFixOut) {
    GlCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(timeFa(p.timestamp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    coordFa(p.latitude) + "، " + coordFa(p.longitude),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    p.accuracyM?.let { stringResource(R.string.loc_accuracy_value, faNum(it.toInt().toString())) } ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(sourceLabelFa(p.source), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun timeFa(iso: String): String {
    val t = runCatching { OffsetDateTime.parse(iso) }.getOrNull() ?: return "—"
    val l = t.toLocalDateTime()
    return faNum("%02d:%02d".format(l.hour, l.minute)) + "  " + faNum("%d/%02d/%02d".format(l.year, l.monthValue, l.dayOfMonth))
}

@Composable
fun sourceLabelFa(source: String): String = when (source) {
    "gps" -> stringResource(R.string.src_gps)
    "network" -> stringResource(R.string.src_network)
    "last_known" -> stringResource(R.string.src_last_known)
    "passive" -> stringResource(R.string.src_passive)
    else -> stringResource(R.string.src_fused)
}
