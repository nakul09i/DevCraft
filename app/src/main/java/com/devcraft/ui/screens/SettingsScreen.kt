package com.devcraft.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devcraft.core.CaptureDiagnostics
import com.devcraft.core.SyncStatus
import com.devcraft.ui.theme.DevCraftLockup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    // Account
    signedInAs: String?,
    authAvailable: Boolean,
    onSignOut: () -> Unit,
    // Connectivity
    syncStatus: SyncStatus,
    lastSyncAt: Long?,
    pendingSyncCount: Int,
    syncError: String?,
    onSyncNow: () -> Unit,
    onDismissSyncError: () -> Unit,
    // SMS
    smsCaptureEnabled: Boolean,
    smsPermissionGranted: Boolean,
    onSmsCaptureChange: (Boolean) -> Unit,
    // Notification capture
    notificationCaptureEnabled: Boolean,
    notificationAccessGranted: Boolean,
    onNotificationCaptureChange: (Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    diagnostics: CaptureDiagnostics,
    onRefreshDiagnostics: () -> Unit,
    // Data
    orderCount: Int,
    messageCount: Int,
    conflictCount: Int,
    databaseVersion: Int,
    appVersion: String,
    onExportOrdersCsv: () -> Unit = {},
    onExportBalancesCsv: () -> Unit = {},
    onNavigateBack: () -> Unit,
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ---------- Account ----------
            SettingsSection("Account") {
                if (!authAvailable) {
                    SettingsRow(
                        label = "Sign-in",
                        value = "Not configured in this build",
                        hint = "Firebase config is absent, so DevCraft runs fully offline.",
                    )
                } else {
                    SettingsRow(
                        label = "Signed in as",
                        value = signedInAs ?: "Working offline (not signed in)",
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                        Text(if (signedInAs != null) "Sign out" else "Sign in")
                    }
                }
            }

            // ---------- Connectivity ----------
            SettingsSection("Connectivity") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Network", fontSize = 14.sp)
                    StatusPill(syncStatus)
                }
                Spacer(Modifier.height(10.dp))
                SettingsRow(
                    label = "Last successful sync",
                    value = lastSyncAt?.let {
                        val diff = System.currentTimeMillis() - it
                        when {
                            diff < 60_000 -> "Just now"
                            diff < 3600_000 -> "${diff / 60_000}m ago"
                            else -> SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(it))
                        }
                    } ?: "Never",
                )
                SettingsRow(
                    label = "Operations pending sync",
                    value = "$pendingSyncCount",
                    hint = when {
                        pendingSyncCount > 0 && syncStatus == SyncStatus.OFFLINE -> "Offline — changes saved locally in Room"
                        pendingSyncCount > 0 && syncStatus == SyncStatus.SYNCING -> "Uploading $pendingSyncCount pending changes..."
                        pendingSyncCount > 0 -> "$pendingSyncCount changes waiting for cloud sync"
                        syncStatus == SyncStatus.ONLINE -> "✓ All changes synced"
                        else -> null
                    }
                )

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSyncNow,
                    enabled = syncStatus != SyncStatus.SYNCING,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (syncStatus == SyncStatus.SYNCING) "Syncing..." else "Sync now")
                }
                if (syncError != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                syncError,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            TextButton(onClick = onDismissSyncError) { Text("Dismiss", fontSize = 12.sp) }
                        }
                    }
                }
            }

            // ---------- Data Export & Spreadsheet ----------
            SettingsSection("Data Export & Spreadsheet") {
                Text(
                    "Export and share your complete orders and customer khata as Excel / Google Sheets compatible CSV files.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onExportOrdersCsv,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export All Orders (Spreadsheet CSV)")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onExportBalancesCsv,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export Customer Khata (Balances CSV)")
                }
            }

            // ---------- SMS ----------

            SettingsSection("SMS order capture") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Capture incoming SMS as orders", fontSize = 14.sp)
                        Text(
                            text = if (smsPermissionGranted) "Permission granted"
                            else "Permission required",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = smsCaptureEnabled, onCheckedChange = onSmsCaptureChange)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Off by default. Authentication codes are never captured. " +
                        "Unrelated to login OTP.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (smsCaptureEnabled && !smsPermissionGranted) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Enabled, but Android has not granted SMS access - " +
                            "no messages will be captured until you allow it.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // ---------- Notification capture ----------
            SettingsSection("Message capture") {
                Text(
                    "WhatsApp: use Share → DevCraft from any chat.",
                    fontSize = 13.sp,
                )
                Text(
                    "DevCraft cannot read a WhatsApp inbox directly. Nothing does, " +
                        "without rooting the phone.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Capture from notifications", fontSize = 14.sp)
                        Text(
                            text = if (notificationAccessGranted) "Access granted"
                            else "Notification access required",
                            fontSize = 12.sp,
                            color = if (notificationAccessGranted)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                    Switch(
                        checked = notificationCaptureEnabled,
                        onCheckedChange = onNotificationCaptureChange,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Reads only messaging apps (WhatsApp, Telegram, Signal, Messages). " +
                        "Bank, email and personal notifications are ignored and never stored. " +
                        "Verification codes are always discarded.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (notificationCaptureEnabled && !notificationAccessGranted) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onOpenNotificationAccess, modifier = Modifier.fillMaxWidth()) {
                        Text("Grant notification access")
                    }
                    Text(
                        "Android has no in-app prompt for this - it opens system settings, " +
                            "where you switch DevCraft on.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "Note: notification text is whatever the sending app displays, so it " +
                        "can be truncated. Share → DevCraft always gives the full message.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------- Capture diagnostics ----------
            // The point of this section: a channel that captures nothing looks
            // identical to one that is working but idle. This tells them apart.
            SettingsSection("Capture status") {
                DiagnosticRow(
                    label = "SMS events seen",
                    value = "${diagnostics.smsSeenCount}",
                    ok = diagnostics.smsSeenCount > 0,
                    hint = if (diagnostics.smsSeenCount == 0)
                        "No SMS has reached the app yet. If you are testing with " +
                            "Google Messages between two Android phones, that is RCS, " +
                            "which does not fire an SMS broadcast."
                    else null,
                )
                DiagnosticRow(
                    label = "Last SMS captured",
                    value = diagnostics.lastSmsCaptureAt.asTimeOrNever(),
                    ok = diagnostics.lastSmsCaptureAt != null,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                DiagnosticRow(
                    label = "Listener connected",
                    value = if (diagnostics.listenerConnected) "Yes" else "No",
                    ok = diagnostics.listenerConnected,
                    hint = if (!diagnostics.listenerConnected)
                        "Android has not bound the service. Grant notification access, " +
                            "and if it was granted before this build, reinstall the app - " +
                            "Android caches the listener list at install time."
                    else null,
                )
                DiagnosticRow(
                    label = "Notifications seen",
                    value = "${diagnostics.notificationSeenCount}",
                    ok = diagnostics.notificationSeenCount > 0,
                )
                DiagnosticRow(
                    label = "Last notification captured",
                    value = diagnostics.lastNotificationCaptureAt.asTimeOrNever(),
                    ok = diagnostics.lastNotificationCaptureAt != null,
                )
                if (!diagnostics.lastSkipReason.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                "LAST EVENT",
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(diagnostics.lastSkipReason, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRefreshDiagnostics, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh status")
                }
            }

            // ---------- Data ----------
            SettingsSection("Local data") {
                SettingsRow(label = "Orders", value = "$orderCount")
                SettingsRow(label = "Messages", value = "$messageCount")
                SettingsRow(label = "Logged conflicts", value = "$conflictCount")
                SettingsRow(label = "Database schema", value = "v$databaseVersion (Room / SQLite)")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Local storage is the source of truth. Nothing is deleted when offline.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------- About ----------
            SettingsSection("About") {
                DevCraftLockup(markSize = 40.dp, nameSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                SettingsRow(label = "Version", value = appVersion)
                SettingsRow(label = "Offline-first", value = "Orders never require a network")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            letterSpacing = 0.9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
        ) {
            Column(modifier = Modifier.padding(14.dp), content = content)
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String, hint: String? = null) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (hint != null) {
            Text(
                hint,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, ok: Boolean, hint: String? = null) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (ok) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.error,
                )
            }
        }
        if (hint != null) {
            Text(
                hint,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun Long?.asTimeOrNever(): String = this?.let {
    SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault()).format(Date(it))
} ?: "Never"

@Composable
private fun StatusPill(status: SyncStatus) {
    val (container, content) = when (status) {
        SyncStatus.ONLINE -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        SyncStatus.SYNCING -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        SyncStatus.SYNC_ERROR -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
        SyncStatus.OFFLINE -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(8.dp), color = container) {
        Text(
            text = status.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
