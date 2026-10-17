package vegabobo.dsusideloader.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import vegabobo.dsusideloader.ui.theme.surfaceContainer

@Composable
fun SimpleCard(
    modifier: Modifier = Modifier,
    cardTitle: String = "",
    text: String = "",
    addToggle: Boolean = false,
    isToggleEnabled: Boolean = false,
    cardColor: Color = Color.Unspecified,
    justifyText: Boolean = false,
    textScrollable: Boolean = false,
    addPadding: Boolean = true,
    content: @Composable () -> Unit = {},
) {
    val containerColor = if (cardColor != Color.Unspecified) cardColor else MaterialTheme.colorScheme.surfaceContainer
    CardBox(
        modifier = modifier,
        cardTitle = cardTitle,
        addToggle = addToggle,
        isToggleChecked = isToggleEnabled,
        addPadding = addPadding,
        cardColor = containerColor,
    ) {
        val scroll = rememberScrollState(0)
        if (text.isNotEmpty()) {
            Text(
                text = text,
                textAlign = if (justifyText) TextAlign.Justify else TextAlign.Start,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (textScrollable) Modifier.verticalScroll(scroll) else Modifier,
            )
        }
        content()
    }
}
