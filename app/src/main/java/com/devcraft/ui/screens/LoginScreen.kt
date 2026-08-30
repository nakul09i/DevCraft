package com.devcraft.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devcraft.ui.AuthStep
import com.devcraft.ui.AuthUiState
import com.devcraft.ui.theme.DevCraftMark

/**
 * Phone/OTP sign-in. Sending a code needs network - that is inherent to SMS -
 * so this screen always offers an explicit offline route rather than trapping
 * the merchant behind a login wall the app does not actually require.
 */
@Composable
fun LoginScreen(
    state: AuthUiState,
    onCountryCodeChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSendOtp: () -> Unit,
    onResendOtp: () -> Unit,
    onVerify: () -> Unit,
    onBackToPhone: () -> Unit,
    onContinueOffline: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        DevCraftMark(size = 64.dp)
        Spacer(Modifier.height(20.dp))

        Text("DevCraft", fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
        Text(
            "by Neutron",
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Your business, organized.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(40.dp))

        when (state.step) {
            AuthStep.PHONE -> PhoneStep(
                state = state,
                onCountryCodeChange = onCountryCodeChange,
                onPhoneChange = onPhoneChange,
                onSendOtp = onSendOtp,
            )
            AuthStep.CODE -> CodeStep(
                state = state,
                onCodeChange = onCodeChange,
                onVerify = onVerify,
                onResendOtp = onResendOtp,
                onBackToPhone = onBackToPhone,
            )
        }

        AnimatedVisibility(visible = state.error != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // Offline-first escape hatch. Orders, parsing and queries never need an account.
        TextButton(onClick = onContinueOffline, enabled = !state.busy) {
            Text("Continue offline without signing in")
        }
        Text(
            "Signing in is only needed for multi-device sync.",
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PhoneStep(
    state: AuthUiState,
    onCountryCodeChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSendOtp: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = state.countryCode,
            onValueChange = onCountryCodeChange,
            label = { Text("Code") },
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.width(96.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        OutlinedTextField(
            value = state.phone,
            onValueChange = onPhoneChange,
            label = { Text("Phone number") },
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
    }

    Spacer(Modifier.height(20.dp))

    Button(
        onClick = onSendOtp,
        enabled = !state.busy && state.phone.length >= 6,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        if (state.busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CodeStep(
    state: AuthUiState,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResendOtp: () -> Unit,
    onBackToPhone: () -> Unit,
) {
    Text("Enter verification code", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Text(
        "Sent to ${state.e164}",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(20.dp))

    OutlinedTextField(
        value = state.code,
        onValueChange = onCodeChange,
        singleLine = true,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            textAlign = TextAlign.Center,
            letterSpacing = 14.sp,
            fontWeight = FontWeight.Bold,
        ),
        placeholder = {
            Text(
                "······",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                letterSpacing = 14.sp,
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )

    Spacer(Modifier.height(20.dp))

    Button(
        onClick = onVerify,
        enabled = !state.busy && state.code.length >= 6,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        if (state.busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text("Verify", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    Spacer(Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBackToPhone, enabled = !state.busy) {
            Text("Change number", fontSize = 13.sp)
        }
        if (state.resendSeconds > 0) {
            Text(
                "Resend in ${state.resendSeconds}s",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TextButton(onClick = onResendOtp, enabled = !state.busy) {
                Text("Resend code", fontSize = 13.sp)
            }
        }
    }
}
