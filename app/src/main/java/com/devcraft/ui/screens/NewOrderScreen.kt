package com.devcraft.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devcraft.data.local.entities.MessageSource
import com.devcraft.domain.model.ParsedMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewOrderScreen(
    onParseMessage: (String) -> ParsedMessage,
    onSaveToInbox: (text: String, source: String) -> Unit,
    onConfirmOrder: (ParsedMessage, String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var rawText by remember { mutableStateOf("bhaiya 2 kurta chahiye navy blue chest 40 parso tak") }
    var parsedResult by remember { mutableStateOf<ParsedMessage?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Parse New Order", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Input Multilingual Conversational Order", fontSize = 16.sp, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = rawText,
                onValueChange = { rawText = it },
                label = { Text("Paste Order Message (English/Hinglish/Hindi)") },
                modifier = Modifier.fillMaxWidth().height(130.dp),
                placeholder = { Text("e.g., Ramesh bhai ko kal shaam 10 bori cement bhejo ₹3500") }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { parsedResult = onParseMessage(rawText) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Parse Offline")
                }

                OutlinedButton(
                    onClick = {
                        if (rawText.isNotBlank()) {
                            onSaveToInbox(rawText, MessageSource.MANUAL.name)
                            onNavigateBack()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save to Inbox")
                }
            }

            parsedResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Parsed Output Preview", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Customer: ${result.customer ?: "Unknown/Guest"}")
                        Text("Due Date: ${result.due_date ?: "Not specified"}")
                        Text("Amount: ₹${result.amount ?: 0.0}")
                        Text("Prior Order Ref: ${result.references_prior_order}")
                        Text("Confidence: ${(result.confidence * 100).toInt()}%")
                        Text("Needs Clarification: ${result.needs_clarification}")

                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Items:", fontWeight = FontWeight.SemiBold)
                        result.items.forEach { item ->
                            Text("• ${item.quantity}x ${item.description} ${item.attributes}")
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onConfirmOrder(result, rawText)
                                onNavigateBack()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Confirm & Save Order (Room)")
                        }
                    }
                }
            }
        }
    }
}
