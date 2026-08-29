package com.devcraft.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devcraft.data.local.entities.ConflictEntity

@Composable
fun ConflictsScreen(
    conflicts: List<ConflictEntity>,
    onNavigateBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Conflict Log (${conflicts.size})", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onNavigateBack) { Text("Back") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (conflicts.isEmpty()) {
            Text("No sync conflicts recorded. All updates merged cleanly.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(conflicts) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Field: ${item.field}", fontWeight = FontWeight.Bold)
                            Text("Local: ${item.localValue} vs Remote: ${item.remoteValue}")
                            Text("Winner: ${item.winningValue}", fontWeight = FontWeight.SemiBold)
                            Text("Reason: ${item.resolutionReason}", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
