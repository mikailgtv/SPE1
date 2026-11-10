package ir.guardianlink.child

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ir.guardianlink.core.network.GlServices
import ir.guardianlink.feature.childstatus.ChildHomeScreen
import ir.guardianlink.feature.childstatus.SyncScheduler
import ir.guardianlink.feature.pairing.ChildSetupScreen

/**
 * Splash logic فرزند: credential داریم ⇒ Home ؛ در غیر این صورت Setup.
 */
@Composable
fun RootNav() {
    val nav = rememberNavController()
    val context = LocalContext.current
    var start by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val disconnected = GlServices.prefs.getString("disconnected_reason")
        start = if (GlServices.creds.get() != null && disconnected == null) "home" else "setup"
    }

    val s = start
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    NavHost(navController = nav, startDestination = s) {
        composable("setup") {
            ChildSetupScreen(
                onConnected = {
                    SyncScheduler.schedulePeriodic(context)
                    ir.guardianlink.feature.childstatus.AppPolicyScheduler.schedule(context)
                    ir.guardianlink.feature.childstatus.NetworkWatcher.register(context)
                    SyncScheduler.once(context)
                    nav.navigate("home") { popUpTo("setup") { inclusive = true } }
                },
            )
        }
        composable("home") {
            ChildHomeScreen(onSyncNow = { SyncScheduler.once(context) },onOpenEmergency={ nav.navigate("emergency") })
        }
        composable("emergency") {
            ir.guardianlink.feature.childstatus.EmergencyScreen(onBack={nav.popBackStack()})
        }
    }
}
