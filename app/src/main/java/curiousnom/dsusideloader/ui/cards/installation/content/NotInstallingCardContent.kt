package curiousnom.dsusideloader.ui.cards.installation.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
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
import curiousnom.dsusideloader.ui.components.FileSelectionBox
import curiousnom.dsusideloader.ui.components.buttons.PrimaryButton
import curiousnom.dsusideloader.ui.components.buttons.SecondaryButton
import curiousnom.dsusideloader.ui.screen.home.InstallationCardState

@Composable
fun NotInstallingCardContent(
    textFieldInteraction: MutableInteractionSource,
    uiState: InstallationCardState,
    onClickClear: () -> Unit,
    onClickInstall: () -> Unit,
    onClickCustomizePartitions: () -> Unit = {},
) {
    FileSelectionBox(
        textFieldInteraction = textFieldInteraction,
        isEnabled = uiState.isTextFieldEnabled,
        isError = uiState.isError,
        isReadOnly = true,
        textFieldValue = uiState.text,
        textFieldTitle = stringResource(id = R.string.select_gsi_info),
    )
    if (uiState.imageFormatInfo.isNotEmpty()) {
        Spacer(modifier = Modifier.padding(top = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = uiState.imageFormatInfo,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (uiState.isPayload && uiState.payloadPartitionsCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onClickCustomizePartitions() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "${stringResource(R.string.customize_partitions)} (${uiState.selectedPartitionsCount}/${uiState.payloadPartitionsCount})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.padding(top = 10.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AnimatedVisibility(visible = uiState.isError) {
            Text(
                text = stringResource(id = R.string.file_unsupported),
                modifier = Modifier.padding(start = 2.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.weight(1F))
        if (uiState.isInstallable) {
            SecondaryButton(
                text = stringResource(R.string.clear),
                onClick = onClickClear,
            )
            Spacer(modifier = Modifier.padding(end = 6.dp))
        }
        PrimaryButton(
            text = stringResource(R.string.install),
            onClick = onClickInstall,
            isEnabled = uiState.isInstallable,
        )
    }
}
