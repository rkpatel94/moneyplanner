package com.moneyplanner

import android.Manifest
import android.os.Build
import android.os.Bundle
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

            // The notification permission is only asked for once reminders are actually
            // switched on, so a new user is not met with a permission prompt before the
            // app has shown them anything useful.
            NotificationPermissionRequest(enabled = state.remindersEnabled)

            var onboarded by rememberSaveable(state.needsOnboarding) {
                mutableStateOf(!state.needsOnboarding)
            }

            when {
                state.isLoading -> Unit
                state.requiresUnlock && !unlocked -> LockScreen(onUnlocked = { unlocked = true })
                // Setup comes before anything else, so a new user is never dropped onto an
                // empty dashboard showing zero with no idea what to do next.
                !onboarded -> OnboardingScreen(onFinished = { onboarded = true })
                else -> MoneyPlannerNavHost()
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
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) relock()
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
