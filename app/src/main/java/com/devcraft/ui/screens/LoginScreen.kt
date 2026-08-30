package com.devcraft.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devcraft.ui.AuthStep
import com.devcraft.ui.AuthUiState
import com.devcraft.ui.theme.DevCraftMark

/**
 * Authentication entry. Signing in needs network - inherent to both SMS and
 * password verification - so every pane keeps an explicit offline route rather
 * than trapping the merchant behind a wall the app does not actually require.
 */
@Composable
fun LoginScreen(
    state: AuthUiState,
    onChooseMethod: (AuthStep) -> Unit,
    onCountryCodeChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSendOtp: () -> Unit,
    onResendOtp: () -> Unit,
    onVerifyOtp: () -> Unit,
    onSubmitEmail: () -> Unit,
    onToggleCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
    onBackToChooser: () -> Unit,
    onBackToPhone: () -> Unit,
    onContinueOffline: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        DevCraftMark(size = 64.dp)
        Spacer(Modifier.height(18.dp))
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

        Spacer(Modifier.height(36.dp))

        when (state.step) {
            AuthStep.CHOOSE -> ChooserPane(state, onChooseMethod)
            AuthStep.PHONE -> PhonePane(state, onCountryCodeChange, onPhoneChange, onSendOtp, onBackToChooser)
            AuthStep.CODE -> CodePane(state, onCodeChange, onVerifyOtp, onResendOtp, onBackToPhone)
            AuthStep.EMAIL -> EmailPane(
                state, onEmailChange, onPasswordChange, onSubmitEmail,
                onToggleCreateAccount, onForgotPassword, onBackToChooser,
            )
        }

        AnimatedVisibility(visible = state.error != null) {
            Banner(
                text = state.error.orEmpty(),
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                showIcon = true,
            )
        }
        AnimatedVisibility(visible = state.notice != null) {
            Banner(
                text = state.notice.orEmpty(),
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                showIcon = false,
            )
        }

        Spacer(Modifier.height(28.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onContinueOffline, enabled = !state.busy) {
            Text("Continue offline without signing in")
        }
        Text(
            "Orders, parsing and search never need an account.\nSigning in is only for multi-device sync.",
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChooserPane(state: AuthUiState, onChoose: (AuthStep) -> Unit) {
    Button(
        onClick = { onChoose(AuthStep.PHONE) },
        enabled = !state.busy,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("Continue with Phone", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }

    Spacer(Modifier.height(12.dp))

    OutlinedButton(
        onClick = { onChoose(AuthStep.EMAIL) },
        enabled = !state.busy,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(Icons.Default.MailOutline, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("Continue with Email", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PhonePane(
    state: AuthUiState,
    onCountryCodeChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSendOtp: () -> Unit,
    onBack: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
    Spacer(Modifier.height(18.dp))
    PrimaryAction("Send code", state.busy, state.phone.length >= 6, onSendOtp)
    TextButton(onClick = onBack, enabled = !state.busy) { Text("Use a different method", fontSize = 13.sp) }
}

@Composable
private fun CodePane(
    state: AuthUiState,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
    Text("Enter verification code", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Text("Sent to ${state.e164}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(18.dp))

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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
    Spacer(Modifier.height(18.dp))
    PrimaryAction("Verify", state.busy, state.code.length >= 6, onVerify)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack, enabled = !state.busy) { Text("Change number", fontSize = 13.sp) }
        if (state.resendSeconds > 0) {
            Text(
                "Resend in ${state.resendSeconds}s",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TextButton(onClick = onResend, enabled = !state.busy) { Text("Resend code", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun EmailPane(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleCreate: () -> Unit,
    onForgot: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        if (state.creatingAccount) "Create your account" else "Sign in with email",
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(18.dp))

    OutlinedTextField(
        value = state.email,
        onValueChange = onEmailChange,
        label = { Text("Email") },
        singleLine = true,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.password,
        onValueChange = onPasswordChange,
        label = { Text("Password") },
        singleLine = true,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        supportingText = if (state.creatingAccount) {
            { Text("At least 6 characters", fontSize = 11.sp) }
        } else null,
    )

    Spacer(Modifier.height(18.dp))
    PrimaryAction(
        label = if (state.creatingAccount) "Create account" else "Sign in",
        busy = state.busy,
        enabled = state.email.isNotBlank() && state.password.isNotBlank(),
        onClick = onSubmit,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onToggleCreate, enabled = !state.busy) {
            Text(
                if (state.creatingAccount) "I have an account" else "Create account",
                fontSize = 13.sp,
            )
        }
        if (!state.creatingAccount) {
            TextButton(onClick = onForgot, enabled = !state.busy) {
                Text("Forgot password?", fontSize = 13.sp)
            }
        }
    }
    TextButton(onClick = onBack, enabled = !state.busy) {
        Text("Use a different method", fontSize = 13.sp)
    }
}

@Composable
private fun PrimaryAction(label: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy && enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Banner(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color, showIcon: Boolean) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showIcon) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(text = text, color = content, fontSize = 13.sp)
        }
    }
}
