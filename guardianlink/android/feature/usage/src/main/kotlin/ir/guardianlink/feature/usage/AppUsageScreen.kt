package ir.guardianlink.feature.usage

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.guardianlink.core.designsystem.GlCard
import ir.guardianlink.core.designsystem.GlErrorBanner
import ir.guardianlink.core.designsystem.faNum
import ir.guardianlink.core.network.AppUsageOut

/** آیکون اپ با fallback متنی؛ فقط از cache حافظه + endpoint سرور می‌آید. */
@Composable
fun AppIcon(deviceId: String, pkg: String, size: Int = 40) {
    val icon by produceState<ImageBitmap?>(null, deviceId, pkg) {
        value = runCatching { UsageIcons.load(deviceId, pkg) }.getOrNull()
    }
    val ic = icon
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        if (ic != null) {
            Image(bitmap = ic, contentDescription = null, modifier = Modifier.size(size.dp))
        } else {
            Text("📦", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * صفحه‌ی مصرف اپ‌ها: آیکون، نام، زمان، درصد از کل + نوار درصد.
 * مرتب‌سازی: بیشترین / کمترین / الفبایی.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUsageScreen(
    deviceId: String,
    onOpenApp: (String) -> Unit,
    onBack: () -> Unit,
    vm: AppUsageViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(deviceId) { vm.load(deviceId) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(stringResource(R.string.au_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(R.string.au_total, hmFa(ui.totalMinutes)), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        GlErrorBanner(ui.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = ui.sort == SortMode.MOST_USED, onClick = { vm.setSort(SortMode.MOST_USED) }, label = { Text(stringResource(R.string.sort_most)) })
            FilterChip(selected = ui.sort == SortMode.LEAST_USED, onClick = { vm.setSort(SortMode.LEAST_USED) }, label = { Text(stringResource(R.string.sort_least)) })
            FilterChip(selected = ui.sort == SortMode.ALPHABETICAL, onClick = { vm.setSort(SortMode.ALPHABETICAL) }, label = { Text(stringResource(R.string.sort_alpha)) })
        }
        if (ui.loading) {
            Text(stringResource(R.string.loading))
        } else if (ui.apps.isEmpty()) {
            GlCard { Text(stringResource(R.string.au_empty), style = MaterialTheme.typography.bodyMedium) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ui.apps, key = { it.packageName }) { a ->
                    AppUsageRow(deviceId, a, onClick = { onOpenApp(a.packageName) })
                }
            }
        }
    }
}

@Composable
private fun AppUsageRow(deviceId: String, a: AppUsageOut, onClick: () -> Unit) {
    GlCard {
        Row(
            Modifier.fillMaxWidth().clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppIcon(deviceId, a.packageName)
            Column(Modifier.weight(1f)) {
                Text(a.appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(a.packageName, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                PercentBar(a.percent)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(hmFa(a.usageMinutes), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(faNum(a.percent.toInt()) + "٪", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
