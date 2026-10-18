package curiousnom.dsusideloader.ui.sdialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import curiousnom.dsusideloader.R
import curiousnom.dsusideloader.core.PayloadManager.PayloadPartition
import curiousnom.dsusideloader.ui.components.CustomBottomSheet
import curiousnom.dsusideloader.ui.components.buttons.PrimaryButton
import curiousnom.dsusideloader.ui.components.buttons.SecondaryButton

@Composable
fun PayloadCustomizerSheet(
    partitions: List<PayloadPartition>,
    selectedPartitions: Set<String>,
    onTogglePartition: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    CustomBottomSheet(
        title = stringResource(id = R.string.customize_partitions_title),
        icon = Icons.Outlined.Tune,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(id = R.string.customize_partitions_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(id = R.string.select_all),
                    onClick = onSelectAll,
                )
                SecondaryButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(id = R.string.deselect_all),
                    onClick = onDeselectAll,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val physical = partitions.filter { it.isPhysical() }
                val logical = partitions.filter { !it.isPhysical() }

                if (physical.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(id = R.string.physical_boot_partitions),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(physical) { part ->
                        val isChecked = selectedPartitions.contains(part.name)
                        PartitionItemRow(
                            partition = part,
                            isChecked = isChecked,
                            onToggle = { onTogglePartition(part.name) },
                            typeDesc = stringResource(id = R.string.physical_slot_target),
                        )
                    }
                }

                if (logical.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(id = R.string.logical_dynamic_partitions),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(logical) { part ->
                        val isChecked = selectedPartitions.contains(part.name)
                        PartitionItemRow(
                            partition = part,
                            isChecked = isChecked,
                            onToggle = { onTogglePartition(part.name) },
                            typeDesc = stringResource(id = R.string.dsu_system_target),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            PrimaryButton(
                modifier = Modifier.fillMaxWidth(),
                text = "${stringResource(id = R.string.done)} (${selectedPartitions.size}/${partitions.size})",
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun PartitionItemRow(
    partition: PayloadPartition,
    isChecked: Boolean,
    onToggle: () -> Unit,
    typeDesc: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(8.dp),
        color = if (isChecked) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = partition.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${partition.getFormattedSize()} • $typeDesc",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(
                checked = isChecked,
                onCheckedChange = { onToggle() },
            )
        }
    }
}
