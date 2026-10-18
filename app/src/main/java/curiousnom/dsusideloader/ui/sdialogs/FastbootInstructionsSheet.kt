package curiousnom.dsusideloader.ui.sdialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import curiousnom.dsusideloader.R
import curiousnom.dsusideloader.core.SlotPartitionManager
import curiousnom.dsusideloader.ui.components.CustomBottomSheet
import curiousnom.dsusideloader.ui.components.buttons.PrimaryButton
import curiousnom.dsusideloader.ui.components.buttons.SecondaryButton

@Composable
fun FastbootInstructionsSheet(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val primarySlotLetter = SlotPartitionManager.getPrimarySlotSuffix().trimStart('_')
    val commands = stringResource(id = R.string.fastboot_commands, primarySlotLetter)

    CustomBottomSheet(
        title = stringResource(id = R.string.fastboot_instructions_title),
        icon = Icons.Outlined.Terminal,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(id = R.string.unrooted_guest_os_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = stringResource(id = R.string.fastboot_instructions_step1),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = stringResource(id = R.string.fastboot_instructions_step2),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .padding(12.dp),
            ) {
                Text(
                    text = commands,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PrimaryButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(id = R.string.copy_commands),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Fastboot Commands", commands)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, context.getString(R.string.commands_copied), Toast.LENGTH_SHORT).show()
                    },
                )
                SecondaryButton(
                    text = stringResource(id = R.string.close),
                    onClick = onDismiss,
                )
            }
        }
    }
}
