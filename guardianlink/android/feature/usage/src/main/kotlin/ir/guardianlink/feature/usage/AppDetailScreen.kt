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
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/** نام دسته فقط اگر از سیستم واقعی آمده باشد؛ «undefined» یعنی سیستم دسته نداده است. */
@Composable
fun categoryLabel(cat: String): String = when (cat) {
    "game" -> stringResource(R.string.cat_game)
    "audio" -> stringResource(R.string.cat_audio)
    "video" -> stringResource(R.string.cat_video)
    "image" -> stringResource(R.string.cat_image)
    "social" -> stringResource(R.string.cat_social)
    "news" -> stringResource(R.string.cat_news)
    "maps" -> stringResource(R.string.cat_maps)
    "productivity" -> stringResource(R.string.cat_productivity)
    "accessibility" -> stringResource(R.string.cat_accessibility)
    else -> stringResource(R.string.cat_undefined)
}

private fun relativeFa(iso: String?): String {
    val t = runCatching { OffsetDateTime.parse(iso) }.getOrNull() ?: return "—"
    val min = ChronoUnit.MINUTES.between(t, OffsetDateTime.now())
    return when {
        min < 1 -> "هم‌اکنون"
        min < 60 -> faNum(min.toInt()) + " دقیقه پیش"
        min < 1440 -> faNum((min / 60).toInt()) + " ساعت پیش"
        else -> faNum((min / 1440).toInt()) + " روز پیش"
    }
}

/**
 * جزئیات یک اپ: امروز، هفته، آخرین استفاده، تعداد باز شدن + نمودار روند هفتگی.
 */
@Composable
fun AppDetailScreen(
    deviceId: String,
    pkg: String,
    onBack: () -> Unit,
    vm: AppDetailViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(deviceId, pkg) { vm.load(deviceId, pkg) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ad_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        GlErrorBanner(ui.error)
        if (ui.loading) {
            Text(stringResource(R.string.loading))
            return@Column
        }
        val d = ui.detail ?: return@Column
        GlCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(deviceId, d.packageName, 56)
                Column {
                    Text(d.appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(d.packageName, style = MaterialTheme.typography.labelSmall)
                    Text(categoryLabel(d.category), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        GlCard {
            DetailRow(stringResource(R.string.ad_today), hmFa(d.todayMinutes))
            DetailRow(stringResource(R.string.ad_today_launches), faNum(d.todayLaunches).toString())
            DetailRow(stringResource(R.string.ad_week), hmFa(d.week.sumOf { it.totalMinutes }))
            DetailRow(stringResource(R.string.ad_week_launches), faNum(d.weekLaunches).toString())
            DetailRow(stringResource(R.string.ad_last_used), relativeFa(d.lastUsedAt))
        }
        GlCard {
            Text(stringResource(R.string.ad_trend), style = MaterialTheme.typography.labelMedium)
            WeeklyBars(d.week)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
