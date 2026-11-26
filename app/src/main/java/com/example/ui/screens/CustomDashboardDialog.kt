package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DEFAULT_DASHBOARD_WIDGETS
import com.example.data.model.DashboardWidgetId
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextSecondary

@Composable
fun CustomDashboardDialog(
    currentOrder: List<String>,
    currentHidden: List<String>,
    onSave: (order: List<String>, hidden: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val mergedOrder = remember(currentOrder) {
        val base = currentOrder.filter { name -> DashboardWidgetId.entries.any { it.name == name } }.toMutableList()
        DashboardWidgetId.entries.forEach { wid ->
            if (!base.contains(wid.name)) base.add(wid.name)
        }
        base
    }
    val ordered = remember { mutableStateListOf<String>().apply { addAll(mergedOrder) } }
    val hiddenSet = remember { mutableStateListOf<String>().apply { addAll(currentHidden) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text("Customize Studio Dashboard", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Reorder widgets and toggle visibility. Preferences sync to your account across mobile & tablet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                LazyColumn(
                    modifier = Modifier.height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(ordered, key = { _, item -> item }) { idx, id ->
                        val widget = DashboardWidgetId.entries.firstOrNull { it.name == id }
                        val visible = !hiddenSet.contains(id)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                            border = BorderStroke(1.dp, if (visible) ElectricBlue.copy(alpha = 0.5f) else BorderSubtle),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = widget?.title ?: id,
                                    color = if (visible) Color.White else TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        if (idx > 0) {
                                            val moved = ordered.removeAt(idx)
                                            ordered.add(idx - 1, moved)
                                        }
                                    },
                                    enabled = idx > 0
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move widget up", tint = ElectricCyan, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        if (idx < ordered.lastIndex) {
                                            val moved = ordered.removeAt(idx)
                                            ordered.add(idx + 1, moved)
                                        }
                                    },
                                    enabled = idx < ordered.lastIndex
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move widget down", tint = ElectricCyan, modifier = Modifier.size(18.dp))
                                }
                                Switch(
                                    checked = visible,
                                    onCheckedChange = { checked ->
                                        if (checked) hiddenSet.remove(id) else if (!hiddenSet.contains(id)) hiddenSet.add(id)
                                    }
                                )
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        ordered.clear()
                        ordered.addAll(DEFAULT_DASHBOARD_WIDGETS)
                        hiddenSet.clear()
                    }
                ) {
                    Text("Reset to Default Layout", color = ElectricCyan)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(ordered.toList(), hiddenSet.toList())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.testTag("save_dashboard_layout_btn")
            ) {
                Text("Save Layout")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
