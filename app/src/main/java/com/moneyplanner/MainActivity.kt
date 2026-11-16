package com.moneyplanner

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.ui.nav.MoneyPlannerNavHost
import com.moneyplanner.ui.screens.lock.LockScreen
import com.moneyplanner.ui.screens.onboarding.OnboardingScreen
import com.moneyplanner.ui.theme.MoneyPlannerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity that hosts the whole app.
 *
 * It extends FragmentActivity because the biometric prompt needs a fragment host. When
 * the app lock is on, nothing else is composed until it has been unlocked, so financial
 * data is never briefly visible behind the lock screen.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }

    /**
     * Every activity the app opens for itself passes through here: the document picker
     * for a receipt, a restore or a CSV, the folder picker, the voice recogniser, the share
     * sheet. Each covers the app completely, so the app stops, and the lock used to re-arm
     * on that stop. Coming back then meant the lock screen, and behind it a navigation
     * graph rebuilt from scratch: the expense being edited and the file just picked were
     * both gone. The launch is noted so the lock can tell this stop from leaving the app.
     */
    @Deprecated("Overridden only to notice launches; the call is passed straight through.")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        ExternalActivity.expect()
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun startActivity(intent: Intent, options: Bundle?) {
        ExternalActivity.expect()
        super.startActivity(intent, options)
    }
}

/**
 * Remembers that the app itself is about to open another activity.
 *
 * Read by the lock: a stop that follows one of these within a few seconds is the app
 * stepping aside for its own picker, not the user leaving. It is still bounded, so a phone
 * left on the picker and picked up later by somebody else asks for the PIN again.
 */
object ExternalActivity {
    @Volatile
    private var expectedAt = 0L

    fun expect() {
        expectedAt = SystemClock.elapsedRealtime()
    }

    fun wasJustLaunched(now: Long = SystemClock.elapsedRealtime()): Boolean =
        expectedAt > 0L && now - expectedAt < LAUNCH_TO_STOP_MILLIS

    fun clear() {
        expectedAt = 0L
    }

    /** How soon after a launch the stop has to come to be the launch's. */
    private const val LAUNCH_TO_STOP_MILLIS = 10_000L

    /** How long the app may sit behind its own picker before coming back needs the PIN. */
    const val MAX_AWAY_MILLIS = 5 * 60_000L
}

@Composable
private fun AppRoot(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MoneyPlannerTheme(themeMode = state.themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // Deliberately not rememberSaveable. Saved state survives the process being
            // killed and restored, which would hand back an already-unlocked app to
            // whoever reopened it. The lock is also re-armed whenever the app leaves the
            // foreground, so returning to it asks again rather than trusting a session
            // that may have started hours ago in someone else's hands.
            var unlocked by remember { mutableStateOf(false) }
            ReArmLockWhenBackgrounded(enabled = state.requiresUnlock) { unlocked = false }

            var onboarded by rememberSaveable(state.needsOnboarding) {
                mutableStateOf(!state.needsOnboarding)
            }

            when {
                state.isLoading -> Unit
                state.requiresUnlock && !unlocked -> LockScreen(onUnlocked = { unlocked = true })
                // Setup comes before anything else, so a new user is never dropped onto an
                // empty dashboard showing zero with no idea what to do next.
                !onboarded -> OnboardingScreen(onFinished = { onboarded = true })
                else -> {
                    // Asked here, once the app is unlocked and set up, rather than at the
                    // top: reminders are on by default, and composed above the lock and the
                    // setup screens the prompt arrived on the very first launch, before the
                    // app had shown anything at all.
                    NotificationPermissionRequest(enabled = state.remindersEnabled)
                    MoneyPlannerNavHost()
                }
            }
        }
    }
}

/**
 * Drops the unlocked flag once the app is no longer in the foreground.
 *
 * ON_STOP rather than ON_PAUSE, so that a permission dialog or the share sheet appearing
 * over the app does not force the user to re-authenticate mid-task.
 */
@Composable
private fun ReArmLockWhenBackgrounded(enabled: Boolean, onRelock: () -> Unit) {
    if (!enabled) return
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val relock by rememberUpdatedState(onRelock)

    DisposableEffect(lifecycleOwner) {
        // Set when the app stopped for one of its own pickers, and when.
        var steppedAsideAt = -1L
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (ExternalActivity.wasJustLaunched(now)) steppedAsideAt = now else relock()
                }
                Lifecycle.Event.ON_START -> if (steppedAsideAt >= 0L) {
                    val away = android.os.SystemClock.elapsedRealtime() - steppedAsideAt
                    steppedAsideAt = -1L
                    ExternalActivity.clear()
                    if (away > ExternalActivity.MAX_AWAY_MILLIS) relock()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun NotificationPermissionRequest(enabled: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Reminders simply stay silent if the user declines. */ }

    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
