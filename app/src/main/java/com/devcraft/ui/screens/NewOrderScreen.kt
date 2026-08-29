package com.devcraft.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devcraft.domain.model.ParsedMessage

@Composable
fun NewOrderScreen(
    onParseMessage: (String) -> ParsedMessage,
    onConfirmOrder: (ParsedMessage, String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var rawText by remember { mutableStateOf("bhaiya 2 kurta chahiye navy blue chest 40 parso tak") }
    var parsedResult by remember { mutableStateOf<ParsedMessage?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Create Order via Parser", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = rawText,
            onValueChange = { rawText = it },
            label = { Text("Paste Order Message (English/Hinglish/Hindi)") },
            modifier = Modifier.fillMaxWidth().height(120.dp)
        )

        Button(
            onClick = { parsedResult = onParseMessage(rawText) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Parse Message (Offline)")
        }

        parsedResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Confirm & Save Order (Room)")
                    }
                }
            }
        }
    }
}
