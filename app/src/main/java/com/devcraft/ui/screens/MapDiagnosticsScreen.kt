package com.devcraft.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devcraft.mapping.GeoPoint
import com.devcraft.mapping.MapDiagnosticsState
import com.devcraft.mapping.MappingProvider
import com.devcraft.mapping.MappingResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapDiagnosticsScreen(
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val mappingRepo = remember { MappingProvider.create() }
    var diagnosticsState by remember { mutableStateOf(mappingRepo.getDiagnostics()) }
    var testLog by remember { mutableStateOf("Ready to run Mappls diagnostic tests...") }
    var isTesting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map Diagnostics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Mappls (MapmyIndia) Integration", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Android SDK & REST Web Services Verification", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            // Diagnostic Status Grid
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("DIAGNOSTIC STATUS CHECK", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Divider()

                    DiagnosticRow("Map Provider", diagnosticsState.provider, isPass = true)
                    DiagnosticRow("SDK Status", diagnosticsState.sdkStatus, isPass = diagnosticsState.sdkStatus == "PASS")
                    DiagnosticRow("Authentication", diagnosticsState.authentication, isPass = diagnosticsState.authentication == "PASS")
                    DiagnosticRow("Configuration", diagnosticsState.configuration, isPass = diagnosticsState.configuration == "PASS")
                    DiagnosticRow("Package Match (com.neutron.devcraft)", diagnosticsState.packageMatch, isPass = diagnosticsState.packageMatch == "PASS")
                    DiagnosticRow("SHA-256 Certificate Match", diagnosticsState.sha256Match, isPass = diagnosticsState.sha256Match == "PASS")
                    DiagnosticRow("Network Connectivity", diagnosticsState.networkStatus, isPass = diagnosticsState.networkStatus == "ONLINE")
                    DiagnosticRow("Map Rendering", diagnosticsState.mapLoading, isPass = diagnosticsState.mapLoading == "PASS")
                    DiagnosticRow("Geocoding Engine", diagnosticsState.geocoding, isPass = diagnosticsState.geocoding == "PASS")
                    DiagnosticRow("Autosuggest & Search", diagnosticsState.searchStatus, isPass = diagnosticsState.searchStatus == "PASS")
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Last Error: ${diagnosticsState.lastError}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (diagnosticsState.lastError == "None") Color(0xFF2E7D32) else Color.Red)
                }
            }

            // Interactive Test Buttons
            Text("LIVE MAP INTEGRATION TESTS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            isTesting = true
                            testLog = "Running Geocode Test: Bhopal..."
                            when (val res = mappingRepo.geocode("MP Nagar, Bhopal")) {
                                is MappingResult.Success -> testLog = "✓ Geocode PASS!\nAddress: ${res.value.formattedAddress}\nCoords: ${res.value.point.latitude}, ${res.value.point.longitude}"
                                is MappingResult.Failure -> testLog = "X Geocode Failed: ${res.message}"
                                else -> testLog = "Geocode offline fallback active."
                            }
                            isTesting = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isTesting
                ) {
                    Text("Test Geocode", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        scope.launch {
                            isTesting = true
                            testLog = "Running Reverse Geocode Test: (23.2599, 77.4126)..."
                            when (val res = mappingRepo.reverseGeocode(GeoPoint(23.2599, 77.4126))) {
                                is MappingResult.Success -> testLog = "✓ Reverse Geocode PASS!\nResolved: ${res.value.formattedAddress}"
                                is MappingResult.Failure -> testLog = "X Reverse Geocode Failed: ${res.message}"
                                else -> testLog = "Reverse Geocode offline fallback active."
                            }
                            isTesting = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isTesting
                ) {
                    Text("Test Rev-Geocode", fontSize = 12.sp)
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        isTesting = true
                        testLog = "Running Autosuggest Search: 'MP Nagar'..."
                        when (val res = mappingRepo.searchAutosuggest("MP Nagar Bhopal")) {
                            is MappingResult.Success -> testLog = "✓ Autosuggest PASS!\nResults Found: ${res.value.size}\nFirst: ${res.value.firstOrNull()?.formattedAddress}"
                            else -> testLog = "Autosuggest test completed."
                        }
                        isTesting = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTesting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Test Search Autosuggest")
            }

            // Test Output Log Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("LOGCAT & DIAGNOSTIC AUDIT LOG", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(testLog, fontSize = 13.sp, color = Color.White, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, isPass: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Badge(
            containerColor = if (isPass) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
            contentColor = if (isPass) Color(0xFF2E7D32) else Color(0xFFC62828)
        ) {
            Text(value, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}
