package com.moneyplanner.ui.screens.lock

import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.ui.components.PrimaryActionButton

/**
 * The app lock.
 *
 * The PIN is checked against a salted hash rather than a stored PIN, and repeated wrong
 * attempts introduce a wait, so guessing four digits by hand stops being practical.
 * Biometric unlock is offered where the device supports it, with the PIN always kept as
 * the fallback so nobody can be locked out by a failing sensor.
 */
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    viewModel: LockViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    LaunchedEffect(state.isUnlocked) {
        if (state.isUnlocked) onUnlocked()
    }

    // Offer the biometric prompt straight away when it is available and enabled, so the
    // common case needs no interaction at all.
    LaunchedEffect(state.biometricAvailable, state.biometricEnabled) {
        if (state.biometricAvailable && state.biometricEnabled && !state.isUnlocked) {
            showBiometricPrompt(activity as? FragmentActivity, onSuccess = viewModel::unlock)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(Modifier.height(20.dp))
        Text("Money Planner is locked", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Enter your PIN to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = state.pinInput,
            onValueChange = viewModel::updatePin,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("PIN") },
            singleLine = true,
            enabled = !state.isLockedOut,
            isError = state.error != null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            // While barred, count the wait down rather than leaving the field dead with
            // no explanation of when it will work again.
            supportingText = when {
                state.isLockedOut -> {
                    {
                        Text(
                            "Too many attempts. Try again in " +
                                "${state.lockoutSecondsRemaining}s.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> state.error?.let { message ->
                    { Text(message, color = MaterialTheme.colorScheme.error) }
                }
            }
        )

        Spacer(Modifier.height(16.dp))
        PrimaryActionButton(
            text = "Unlock",
            onClick = viewModel::verifyPin,
            enabled = state.pinInput.length >= 4 && !state.isLockedOut
        )

        if (state.biometricAvailable && state.biometricEnabled) {
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = {
                    showBiometricPrompt(activity as? FragmentActivity, onSuccess = viewModel::unlock)
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Use biometrics")
                }
            }
        }
    }
}

private fun showBiometricPrompt(activity: FragmentActivity?, onSuccess: () -> Unit) {
    if (activity == null) return
    if (BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) !=
        BiometricManager.BIOMETRIC_SUCCESS
    ) {
        return
    }

    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        }
    )

    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Money Planner")
            .setSubtitle("Use your fingerprint or face to continue")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
    )
}
