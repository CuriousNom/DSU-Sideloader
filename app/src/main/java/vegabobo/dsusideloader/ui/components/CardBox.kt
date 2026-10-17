package vegabobo.dsusideloader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import vegabobo.dsusideloader.ui.theme.surfaceContainer

@Composable
fun CardBox(
    modifier: Modifier = Modifier,
    cardTitle: String = "",
    addToggle: Boolean = false,
    isToggleChecked: Boolean = false,
    isToggleEnabled: Boolean = true,
    addPadding: Boolean = true,
    cardColor: Color = Color.Unspecified,
    onCheckedChange: ((Boolean) -> Unit) = {},
    roundedCornerShape: RoundedCornerShape = RoundedCornerShape(24.dp),
    content: @Composable (ColumnScope) -> Unit,
) {
    val resolvedColor = if (cardColor != Color.Unspecified) cardColor else MaterialTheme.colorScheme.surfaceContainer
    val strokeColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Box(
        modifier = if (addPadding) {
            Modifier
                .clip(roundedCornerShape)
                .background(resolvedColor)
                .border(1.dp, strokeColor, roundedCornerShape)
                .padding(all = 16.dp)
                .fillMaxWidth()
        } else {
            Modifier
                .clip(roundedCornerShape)
                .background(resolvedColor)
                .border(1.dp, strokeColor, roundedCornerShape)
                .fillMaxWidth()
        },
    ) {
        Column(modifier = modifier) {
            if (cardTitle.isNotEmpty()) {
                if (addToggle) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CardTitle(modifier.weight(1F), cardTitle = cardTitle)
                        Spacer(modifier = Modifier.padding(4.dp))
                        Switch(
                            checked = isToggleChecked,
                            onCheckedChange = onCheckedChange,
                            enabled = isToggleEnabled,
                            colors = SwitchDefaults.colors(),
                        )
                    }
                } else {
                    CardTitle(
                        cardTitle = cardTitle,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            content(this)
        }
    }
}
