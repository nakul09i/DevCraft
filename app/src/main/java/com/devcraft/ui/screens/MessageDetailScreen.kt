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
import com.devcraft.data.local.entities.MessageEntity
import com.devcraft.data.local.entities.MessageSource
import com.devcraft.ui.components.ConfidenceMeter
import com.devcraft.ui.components.FieldIcons
import com.devcraft.ui.components.IconLabelRow
import com.devcraft.ui.components.MessageStatusChip
import com.devcraft.ui.components.SourceBadge
import com.devcraft.domain.model.ParsedItem
import com.devcraft.domain.model.ParsedMessage
import java.text.SimpleDateFormat
import java.util.*

/**
 * Compact key/value table of what the parser understood. Deliberately a table:
 * a merchant scanning a screen needs to spot a wrong quantity or a missing
 * address in one glance, which prose does not allow.
 */
@Composable
private fun InterpretationTable(
    rows: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, String>>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            rows.forEachIndexed { index, (icon, label, value) ->
                IconLabelRow(icon = icon, label = label, value = value)
                if (index != rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 40.dp, end = 14.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    message: MessageEntity?,
    onParseText: (String) -> ParsedMessage,
    onConfirmOrder: (
        messageId: String,
        customerName: String,
        dueDate: String?,
        amount: Double?,
        items: List<ParsedItem>,
        rawMessage: String,
        deliveryAddress: String?,
        onComplete: (String) -> Unit
    ) -> Unit,
    onNavigateOrderDetail: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    if (message == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val isConverted = message.status == "CONVERTED"
    val isWhatsApp = message.source == MessageSource.WHATSAPP_SHARE.name

    // Run parser initially if not already parsed
    val initialParsed = remember(message.originalText) {
        onParseText(message.originalText)
    }

    var customerName by remember { mutableStateOf(initialParsed.customer ?: message.senderName ?: "Guest Customer") }
    var dueDate by remember { mutableStateOf(initialParsed.due_date ?: "") }
    var amountText by remember { mutableStateOf(if (initialParsed.amount != null) "${initialParsed.amount}" else "") }
    var items by remember { mutableStateOf(initialParsed.items) }
    var confidence by remember { mutableStateOf(initialParsed.confidence) }
    var needsClarification by remember { mutableStateOf(initialParsed.needs_clarification) }

    var deliveryAddress by remember {
        mutableStateOf(
            listOfNotNull(initialParsed.delivery_address, initialParsed.pincode)
                .joinToString(" ")
                .trim()
        )
    }
    var isSubmitting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Message Interpretation", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Source & Timestamp
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SourceBadge(source = message.source)

                        Text(
                            text = message.senderName ?: message.sender ?: "Unknown Sender",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    MessageStatusChip(status = message.status)
                }
            }

            // Already Converted Banner
            if (isConverted && !message.parsedOrderId.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            Column {
                                Text("Order Created Successfully", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 14.sp)
                                Text("Saved to local Room SQLite database", fontSize = 12.sp, color = Color(0xFF388E3C))
                            }
                        }
                        Button(
                            onClick = { onNavigateOrderDetail(message.parsedOrderId) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("View Order", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Original Message Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ORIGINAL RAW MESSAGE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(message.receivedAt)),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = message.originalText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 22.sp
                    )
                }
            }

            // Structured Extraction Section
            Text(
                text = "Structured Extraction (Offline Parser)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            // Confidence: icon + bar + number, three redundant encodings
            ConfidenceMeter(
                confidence = confidence,
                needsReview = needsClarification,
                modifier = Modifier.fillMaxWidth(),
            )

            // Interpretation table: everything the parser resolved, at a glance.
            // Icons carry the meaning so a dense table stays scannable.
            InterpretationTable(
                rows = buildList {
                    add(Triple(FieldIcons.Customer, "Customer", initialParsed.customer ?: "—"))
                    add(Triple(FieldIcons.Phone, "Phone", initialParsed.phone ?: "—"))
                    add(
                        Triple(
                            FieldIcons.Item, "Item",
                            initialParsed.items.firstOrNull()?.description ?: "—"
                        )
                    )
                    add(
                        Triple(
                            FieldIcons.Quantity, "Quantity",
                            initialParsed.items.firstOrNull()?.quantity?.toString() ?: "—"
                        )
                    )
                    add(
                        Triple(
                            FieldIcons.Amount, "Amount",
                            initialParsed.amount?.let { "₹%,.0f".format(it) } ?: "—"
                        )
                    )
                    add(Triple(FieldIcons.DueDate, "Due date", initialParsed.due_date ?: "—"))
                    add(
                        Triple(
                            FieldIcons.Address, "Delivery address",
                            initialParsed.delivery_address ?: "—"
                        )
                    )
                    add(Triple(FieldIcons.Pincode, "PIN code", initialParsed.pincode ?: "—"))
                    if (initialParsed.references_prior_order) {
                        add(Triple(FieldIcons.Repeat, "Repeat order", "Yes — same as before"))
                    }
                    initialParsed.items.firstOrNull()?.attributes?.forEach { (k, v) ->
                        val icon = when (k.lowercase()) {
                            "color", "colour" -> FieldIcons.Colour
                            "size" -> FieldIcons.Size
                            else -> FieldIcons.Attribute
                        }
                        add(Triple(icon, k.replaceFirstChar { it.uppercase() }, v))
                    }
                },
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Editable Parsed Fields
            OutlinedTextField(
                value = customerName,
                onValueChange = { customerName = it },
                label = { Text("Customer Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isConverted
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Due Date (YYYY-MM-DD)") },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !isConverted
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !isConverted
                )
            }

            OutlinedTextField(
                value = deliveryAddress,
                onValueChange = { deliveryAddress = it },
                label = { Text("Delivery address (optional)") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isConverted,
                minLines = 2,
                supportingText = {
                    Text(
                        text = if (initialParsed.hasLocation) "Read from the message text"
                        else "None found in the message - add it if you know it",
                        fontSize = 11.sp,
                    )
                },
            )

            // Items List
            Text("Extracted Items (${items.size})", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

            items.forEachIndexed { index, item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${item.quantity}x ${item.description}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            if (item.attributes.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    item.attributes.forEach { (k, v) ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "$k: $v",
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action CTAs
            if (!isConverted) {
                Button(
                    onClick = {
                        isSubmitting = true
                        val amount = amountText.toDoubleOrNull()
                        onConfirmOrder(
                            message.messageId,
                            customerName,
                            if (dueDate.isNotBlank()) dueDate else null,
                            amount,
                            items,
                            message.originalText,
                            deliveryAddress.trim().takeIf { it.isNotBlank() }
                        ) { newOrderId ->
                            isSubmitting = false
                            onNavigateOrderDetail(newOrderId)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirm & Create Room Order", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = {
                        val recomputed = onParseText(message.originalText)
                        customerName = recomputed.customer ?: customerName
                        dueDate = recomputed.due_date ?: dueDate
                        amountText = if (recomputed.amount != null) "${recomputed.amount}" else amountText
                        items = recomputed.items
                        confidence = recomputed.confidence
                        needsClarification = recomputed.needs_clarification
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Re-parse Message Offline")
                }
            }
        }
    }
}
