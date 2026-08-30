package com.devcraft.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devcraft.domain.model.ParsedMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreAnalysisSheet(
    parsed: ParsedMessage,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PARSING ANALYSIS",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Real deterministic evaluation breakdown",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (parsed.classification.isOrder) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                ) {
                    Text(
                        text = parsed.classification.label.uppercase(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (parsed.classification.isOrder) Color(0xFF2E7D32) else Color(0xFFE65100)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // High-Level Metric Cards Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricScoreCard(
                    title = "Classification",
                    score = parsed.classification_score,
                    subtitle = parsed.classification.label,
                    modifier = Modifier.weight(1f)
                )
                MetricScoreCard(
                    title = "Field Accuracy",
                    score = parsed.field_extraction_score,
                    subtitle = "60% Weight",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricScoreCard(
                    title = "Date Resolution",
                    score = parsed.date_resolution_score,
                    subtitle = "20% Weight",
                    modifier = Modifier.weight(1f)
                )
                MetricScoreCard(
                    title = "Clarification",
                    score = parsed.clarification_decision_score,
                    subtitle = "20% Weight",
                    modifier = Modifier.weight(1f)
                )
            }

            // Overall Weighted Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "OVERALL TEST A SCORE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "0.60×Field + 0.20×Date + 0.20×Clarification",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }

                    Text(
                        text = "${(parsed.overall_score * 100).toInt()}%",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Detailed Field Breakdown Checklist
            Text(
                text = "Field-Level Extraction Breakdown",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FieldStatusRow(
                        label = "Customer Name",
                        value = parsed.customer,
                        isExtracted = parsed.customer != null,
                        icon = Icons.Default.Person
                    )
                    FieldStatusRow(
                        label = "Quantity",
                        value = parsed.quantity?.toString(),
                        isExtracted = parsed.quantity != null,
                        icon = Icons.Default.Numbers
                    )
                    FieldStatusRow(
                        label = "Item Description",
                        value = parsed.itemDescription?.takeIf { it != "Unspecified item" },
                        isExtracted = !parsed.itemDescription.isNullOrBlank() && parsed.itemDescription != "Unspecified item",
                        icon = Icons.Default.Inventory2
                    )
                    FieldStatusRow(
                        label = "Total Amount",
                        value = parsed.amount?.let { "₹%,.0f".format(it) },
                        isExtracted = parsed.amount != null,
                        icon = Icons.Default.CurrencyRupee
                    )
                    FieldStatusRow(
                        label = "Due Date",
                        value = parsed.display_date ?: parsed.due_date,
                        isExtracted = parsed.due_date != null,
                        subtitle = parsed.raw_date_text?.let { "Raw: \"$it\"" },
                        icon = Icons.Default.CalendarToday
                    )
                    FieldStatusRow(
                        label = "Delivery Address",
                        value = parsed.delivery_address,
                        isExtracted = !parsed.delivery_address.isNullOrBlank(),
                        icon = Icons.Default.LocationOn
                    )
                    FieldStatusRow(
                        label = "Payment Method",
                        value = parsed.payment_method,
                        isExtracted = parsed.payment_method != null,
                        icon = Icons.Default.Payment
                    )
                    FieldStatusRow(
                        label = "PIN Code",
                        value = parsed.pincode,
                        isExtracted = parsed.pincode != null,
                        icon = Icons.Default.PinDrop
                    )
                }
            }

            // Review Notes if any
            if (parsed.review_notes.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "PARSER REVIEW NOTES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        parsed.review_notes.forEach { note ->
                            Text(
                                text = "• $note",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Close Analysis")
            }
        }
    }
}

@Composable
private fun MetricScoreCard(
    title: String,
    score: Float,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "${(score * 100).toInt()}%",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (score >= 0.8f) Color(0xFF2E7D32) else if (score >= 0.6f) Color(0xFFE65100) else Color(0xFFC62828)
            )
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun FieldStatusRow(
    label: String,
    value: String?,
    isExtracted: Boolean,
    icon: ImageVector,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (isExtracted && value != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.RemoveCircleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Not provided",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
