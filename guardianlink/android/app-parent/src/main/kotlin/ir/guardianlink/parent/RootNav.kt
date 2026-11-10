package ir.guardianlink.parent

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ir.guardianlink.core.network.ApiException
import ir.guardianlink.core.network.ErrCode
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.feature.auth.AuthScreen
import ir.guardianlink.feature.auth.FamilySetupScreen
import ir.guardianlink.feature.dashboard.DashboardScreen
import ir.guardianlink.feature.pairing.ParentPairingScreen
import kotlinx.coroutines.launch

/**
 * Splash logic:
 *  1) آیا کاربر login است؟  2) آیا Family وجود دارد؟  3) Dashboard (با یا بدون دستگاه)
 */
@Composable
fun RootNav() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val notificationIntent = remember { (context as? android.app.Activity)?.intent?.action == ir.guardianlink.feature.dashboard.ParentNotifier.ACTION_OPEN_NOTIFICATION }
    val initialNotificationId = remember { (context as? android.app.Activity)?.intent?.getStringExtra(ir.guardianlink.feature.dashboard.ParentNotifier.EXTRA_NOTIFICATION_ID) }
    val emergencyIntent = remember { (context as? android.app.Activity)?.intent?.action == ir.guardianlink.feature.dashboard.ParentNotifier.ACTION_OPEN_EMERGENCY }
    val initialSosId = remember { (context as? android.app.Activity)?.intent?.getStringExtra(ir.guardianlink.feature.dashboard.ParentNotifier.EXTRA_SOS_ID) }
    var start by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        start = try {
            if (GlServices.creds.get() == null) {
                "login"
            } else {
                runCatching { GlServices.api.me() }.onFailure {
                    if (it is ApiException && (it.code == ErrCode.SESSION_EXPIRED || it.code == ErrCode.SESSION_REVOKED || it.code == ErrCode.UNAUTHORIZED)) {
                        GlServices.creds.clear()
                    }
                }
                if (GlServices.creds.get() == null) {
                    "login"
                } else {
                    val fam = try {
                        GlServices.api.familyMe()
                    } catch (e: ApiException) {
                        if (e.code == ErrCode.NO_FAMILY) null else throw e
                    }
                    when {
                        fam == null -> "family"
                        emergencyIntent -> "emergency"
                        notificationIntent -> "notifications"
                        GlServices.api.devices().isEmpty() -> "onboarding"
                        else -> "dashboard"
                    }
                }
            }
        } catch (e: Exception) {
            if (GlServices.creds.get() == null) "login" else "dashboard"
        }
    }

    val s = start
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    NavHost(navController = nav, startDestination = s) {
        composable("login") {
            AuthScreen(onDone = {
                // پس از ورود: خانواده دارد؟
                nav.navigate("familycheck") { popUpTo("login") { inclusive = true } }
            })
        }
        composable("familycheck") {
            FamilyGate(nav)
        }
        composable("family") {
            FamilySetupScreen(onDone = { nav.navigate("onboarding") { popUpTo("family") { inclusive = true } } })
        }
        composable("onboarding") {
            ParentOnboardingScreen(
                onOpenPairing = { nav.navigate("pairing") },
                onFinish = { nav.navigate("dashboard") { popUpTo("onboarding") { inclusive = true } } },
            )
        }
        composable("dashboard") {
            DashboardScreen(
                onAddDevice = { nav.navigate("pairing") },
                onOpenScreenTime = { id -> nav.navigate("screentime/$id") },
                onOpenMap = { id -> nav.navigate("locmap/$id") },
                onOpenLocationHistory = { id, filter -> nav.navigate("lochistory/$id?filter=$filter") },
                onOpenLocationSettings = { id -> nav.navigate("locsettings/$id") },
                onOpenSafeZones = { id -> nav.navigate("safezones/$id") },
                onOpenAppManagement = { id -> nav.navigate("appmanagement/$id") },
                onOpenWebSafety = { id -> nav.navigate("websafety/$id") },
                onOpenNotifications = { nav.navigate("notifications") },
                onOpenEmergency = { nav.navigate("emergency") },
                onOpenPrivacyCenter = { nav.navigate("privacy") },
            )
        }
        composable("pairing") {
            ParentPairingScreen(onDone = { nav.popBackStack() })
        }
        composable("notifications") {
            ir.guardianlink.feature.dashboard.NotificationCenterScreen(initialNotificationId = initialNotificationId, onBack = { nav.popBackStack() })
        }
        composable("emergency") {
            ir.guardianlink.feature.dashboard.EmergencyCenterScreen(initialSosId = initialSosId, onBack = { nav.popBackStack() })
        }
        composable("privacy") {
            PrivacyCenterScreen(onBack = { nav.popBackStack() })
        }
        // ---------- Phase 7: transparent managed-Chrome domain rules ----------
        composable("websafety/{deviceId}") { entry ->
            val id = entry.arguments?.getString("deviceId") ?: return@composable
            ir.guardianlink.feature.usage.WebSafetyScreen(deviceId = id, onBack = { nav.popBackStack() })
        }
        // ---------- Phase 6: App inventory and per-app rules ----------
        composable("appmanagement/{deviceId}") { entry ->
            val id = entry.arguments?.getString("deviceId") ?: return@composable
            ir.guardianlink.feature.usage.AppManagementScreen(deviceId = id, onBack = { nav.popBackStack() })
        }
        // ---------- Phase 3: Screen Time ----------
        composable("screentime/{deviceId}") { entry ->
            val id = entry.arguments?.getString("deviceId") ?: return@composable
            ir.guardianlink.feature.usage.ScreenTimeScreen(
                deviceId = id,
                onOpenApps = { nav.navigate("appusage/$id") },
                onOpenReports = { nav.navigate("usagereports/$id") },
                onOpenSettings = { nav.navigate("stsettings/$id") },
                onOpenApp = { pkg -> nav.navigate("appdetail/$id/$pkg") },
                onBack = { nav.popBackStack() },
            )
        }
        composable("appusage/{deviceId}") { entry ->
            val id = entry.arguments?.getString("deviceId") ?: return@composable
            ir.guardianlink.feature.usage.AppUsageScreen(
                deviceId = id,
                onOpenApp = { pkg -> nav.navigate("appdetail/$id/$pkg") },
                onBack = { nav.popBackStack() },
            )
        }
        composable("appdetail/{deviceId}/{pkg}") { entry ->
            val id = entry.arguments?.getString("deviceId") ?: return@composable
            val pkg = entry.arguments?.getString("pkg") ?: return@composable
            ir.guardianlink.feature.usage.AppDetailScreen(
                deviceId = id,
                pkg = pkg,
                onBack = { nav.popBackStack() },
            )
        }
        composable("usagereports/{deviceId}") { entry ->
            val id = entry.arguments?.getString("deviceId") ?: return@composable
            ir.guardianlink.feature.usage.ReportsScreen(
                deviceId = id,
                onBack = { nav.popBackStack() },
            )
        }
        composable("stsettings/{deviceId}") { entry ->
            val id = entry.arguments?.getString("deviceId") ?: return@composable
            ir.guardianlink.feature.usage.ScreenTimeSettingsScreen(
                deviceId = id,
                onBack = { nav.popBackStack() },
            )
        }
        // ---------- Phase 4: Location ----------
        composable("locmap/{deviceId}") { entry ->
            val id = entry.arguments?.getString("deviceId") ?: return@composable
            ir.guardianlink.feature.location.MapScreen(
                deviceId = id,
                onOpenHistory = { nav.navigate("lochistory/$id?filter=all") },
                onOpenSettings = { nav.navigate("locsettings/$id") },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "lochistory/{deviceId}?filter={filter}",
            arguments = listOf(
                androidx.navigation.navArgument("deviceId") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("filter") { defaultValue = "all" },
            ),
        ) { entry ->
            val id = entry.arguments?.getString("deviceId") ?: return@composable
            val filter = entry.arguments?.getString("filter") ?: "all"
            ir.guardianlink.feature.location.LocationHistoryScreen(
                deviceId = id,
                initialFilter = filter,
                onBack = { nav.popBackStack() },
            )
        }
        composable("locsettings/{deviceId}") { entry ->
            val id = entry.arguments?.getString("deviceId") ?: return@composable
            ir.guardianlink.feature.location.LocationSettingsScreen(
                deviceId = id,
                onBack = { nav.popBackStack() },
            )
        }
        // ---------- Phase 5: Safe Zones / Geofence ----------
        composable("safezones/{deviceId}") { entry ->
            val id = entry.arguments?.getString("deviceId") ?: return@composable
            ir.guardianlink.feature.location.SafeZonesScreen(
                deviceId = id,
                onBack = { nav.popBackStack() },
                onOpenHistory = { nav.navigate("geohistory/$id") },
            )
        }
        composable("geohistory/{deviceId}") { entry ->
            val id = entry.arguments?.getString("deviceId") ?: return@composable
            ir.guardianlink.feature.location.GeofenceHistoryScreen(id, onBack = { nav.popBackStack() })
        }
    }
}

@Composable
private fun FamilyGate(nav: androidx.navigation.NavHostController) {
    var dest by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    suspend fun check() {
        dest = null
        error = null
        try {
            val family = try {
                GlServices.api.familyMe()
            } catch (e: ApiException) {
                if (e.code == ErrCode.NO_FAMILY) null else throw e
            }
            dest = when {
                family == null -> "family"
                GlServices.api.devices().isEmpty() -> "onboarding"
                else -> "dashboard"
            }
        } catch (e: ApiException) {
            error = e.code
        } catch (_: Exception) {
            error = ErrCode.NETWORK
        }
    }
    LaunchedEffect(Unit) { check() }
    val d = dest
    if (error != null) {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            androidx.compose.material3.Text(stringResource(R.string.family_gate_error), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            androidx.compose.material3.TextButton(onClick = { scope.launch { check() } }) { androidx.compose.material3.Text(stringResource(R.string.family_gate_retry)) }
        }
    } else if (d == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        LaunchedEffect(d) {
            nav.navigate(d) { popUpTo("familycheck") { inclusive = true } }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}
