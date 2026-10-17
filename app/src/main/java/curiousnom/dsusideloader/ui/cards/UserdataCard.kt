package curiousnom.dsusideloader.ui.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import curiousnom.dsusideloader.R
import curiousnom.dsusideloader.ui.components.CardBox
import curiousnom.dsusideloader.ui.components.FileSelectionBox
import curiousnom.dsusideloader.ui.screen.home.UserDataCardState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserdataCard(
    isEnabled: Boolean,
    uiState: UserDataCardState,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    CardBox(
        modifier = modifier,
        cardTitle = stringResource(id = R.string.userdata_size),
        cardIcon = Icons.Outlined.Storage,
        addToggle = true,
        isToggleEnabled = !isEnabled,
        isToggleChecked = uiState.isSelected,
        onCheckedChange = onCheckedChange,
    ) {
        AnimatedVisibility(
            visible = uiState.isSelected,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                val presets = remember(uiState.maximumAllowed) {
                    listOf(8, 16, 32, 64).filter {
                        uiState.maximumAllowed == 0 || it <= uiState.maximumAllowed
                    }
                }
                if (presets.isNotEmpty() || uiState.maximumAllowed > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        presets.forEach { gb ->
                            FilterChip(
                                selected = uiState.text == gb.toString(),
                                onClick = { onValueChange(gb.toString()) },
                                label = { Text("$gb GB", fontSize = 12.sp) },
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isEnabled,
                            )
                        }
                        if (uiState.maximumAllowed > 0 && !presets.contains(uiState.maximumAllowed)) {
                            FilterChip(
                                selected = uiState.text == uiState.maximumAllowed.toString(),
                                onClick = { onValueChange(uiState.maximumAllowed.toString()) },
                                label = { Text("Max (${uiState.maximumAllowed} GB)", fontSize = 12.sp) },
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isEnabled,
                            )
                        }
                    }
                }
                FileSelectionBox(
                    modifier = Modifier.padding(bottom = 4.dp),
                    isEnabled = !isEnabled,
                    isError = uiState.isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textFieldValue = uiState.text,
                    textFieldTitle = stringResource(id = R.string.userdata_size_info),
                    onValueChange = onValueChange,
                )
                AnimatedVisibility(visible = uiState.isError) {
                    Text(
                        modifier = Modifier.padding(start = 1.dp),
                        text = stringResource(
                            id = R.string.allowed_userdata_allocation,
                            uiState.maximumAllowed,
                        ),
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 14.sp,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
